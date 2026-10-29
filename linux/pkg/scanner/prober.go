package scanner

import (
	"bufio"
	"fmt"
	"math/rand"
	"net"
	"regexp"
	"strconv"
	"strings"
	"time"
)

var defaultSNIs = []string{
	"speed.cloudflare.com",
	"www.cloudflare.com",
	"cloudflare.com",
	"dash.cloudflare.com",
	"blog.cloudflare.com",
	"pages.dev",
	"workers.dev",
}

var coloRegex = regexp.MustCompile(`colo=([A-Za-z0-9]+)`)

// Prober executes handshake probes against a target endpoint.
type Prober struct {
	config ScanConfig
	rng    *rand.Rand
}

// NewProber creates a prober configured with the specified scan options.
func NewProber(cfg ScanConfig) *Prober {
	return &Prober{
		config: cfg,
		rng:    rand.New(rand.NewSource(time.Now().UnixNano())),
	}
}

// ChooseSNI selects the best SNI for an attempt.
func (p *Prober) ChooseSNI(attempt int, preferred string) string {
	if preferred != "" {
		return preferred
	}
	if p.config.SNIOverride != "" {
		return p.config.SNIOverride
	}
	if p.config.Proxy != nil && p.config.Proxy.EffectiveSNI() != "" {
		return p.config.Proxy.EffectiveSNI()
	}
	return defaultSNIs[attempt%len(defaultSNIs)]
}

// Probe executes cfg.Tries attempts against ip:port and returns the aggregated ScanResult.
func (p *Prober) Probe(ip string, port int, preferredSNI string) ScanResult {
	latencies := make([]int64, 0, p.config.Tries)
	tlsOk := false
	httpStatus := 0
	colo := ""
	attempts := 0

	totalTimeout := p.config.Timeout
	if totalTimeout < 800*time.Millisecond {
		totalTimeout = 800 * time.Millisecond
	}

	dialTimeout := totalTimeout / 4
	tlsTimeout := totalTimeout / 2
	httpTimeout := totalTimeout / 4

	addr := net.JoinHostPort(ip, strconv.Itoa(port))
	isTlsPort := port == 443 || port == 8443 || port == 2053 || port == 2083 || port == 2087 || port == 2096

	for attempt := 0; attempt < p.config.Tries; attempt++ {
		attempts++
		sni := p.ChooseSNI(attempt, preferredSNI)
		start := time.Now()

		dialer := net.Dialer{Timeout: dialTimeout}
		conn, err := dialer.Dial("tcp", addr)
		if err != nil {
			continue // Packet dropped or port unreachable
		}

		elapsed := time.Since(start).Milliseconds()

		switch p.config.Mode {
		case ProbeTCP:
			_ = conn.Close()
			latencies = append(latencies, elapsed)

		case ProbeTLS:
			if isTlsPort {
				tlsConn, err := DialTLSHandshake(conn, sni, tlsTimeout, p.config.AntiDpi)
				if err == nil {
					tlsOk = true
					latencies = append(latencies, time.Since(start).Milliseconds())
					_ = tlsConn.Close()
				}
			} else {
				// Non-TLS port fallback to TCP latency
				_ = conn.Close()
				latencies = append(latencies, elapsed)
			}

		case ProbeHTTP:
			if isTlsPort {
				tlsConn, err := DialTLSHandshake(conn, sni, tlsTimeout, p.config.AntiDpi)
				if err != nil {
					continue
				}
				tlsOk = true

				_ = tlsConn.SetDeadline(time.Now().Add(httpTimeout))
				req := fmt.Sprintf("GET /cdn-cgi/trace HTTP/1.1\r\nHost: %s\r\nUser-Agent: Mozilla/5.0 (AshaScanner Linux)\r\nConnection: close\r\n\r\n", sni)
				if _, err := tlsConn.Write([]byte(req)); err == nil {
					status, c := readTraceResponse(tlsConn)
					if status > 0 {
						httpStatus = status
					}
					if c != "" {
						colo = c
					}
					if status > 0 || c != "" {
						latencies = append(latencies, time.Since(start).Milliseconds())
					}
				}
				_ = tlsConn.Close()
			} else {
				// Plain HTTP probe
				_ = conn.SetDeadline(time.Now().Add(httpTimeout))
				req := fmt.Sprintf("GET /cdn-cgi/trace HTTP/1.1\r\nHost: %s\r\nUser-Agent: Mozilla/5.0 (AshaScanner Linux)\r\nConnection: close\r\n\r\n", sni)
				if _, err := conn.Write([]byte(req)); err == nil {
					status, c := readTraceResponse(conn)
					if status > 0 {
						httpStatus = status
					}
					if c != "" {
						colo = c
					}
					if status > 0 || c != "" {
						latencies = append(latencies, time.Since(start).Milliseconds())
					}
				}
				_ = conn.Close()
			}
		}

		// Micro jitter delay between attempts
		if attempt < p.config.Tries-1 {
			time.Sleep(time.Duration(10+rand.Intn(40)) * time.Millisecond)
		}
	}

	healthy := false
	if len(latencies) > 0 {
		switch p.config.Mode {
		case ProbeTCP:
			healthy = true
		case ProbeTLS:
			healthy = tlsOk
		case ProbeHTTP:
			healthy = httpStatus >= 200 && httpStatus <= 499 || colo != ""
		}
	}

	return ScanResult{
		IP:         ip,
		Port:       port,
		Mode:       p.config.Mode,
		Latencies:  latencies,
		Attempts:   attempts,
		TLSOk:      tlsOk,
		HTTPStatus: httpStatus,
		Colo:       colo,
		Healthy:    healthy,
		PassRate:   1.0,
		Timestamp:  time.Now().Unix(),
	}
}

// readTraceResponse extracts the HTTP response code and colo=XXX trace marker.
func readTraceResponse(conn net.Conn) (int, string) {
	scanner := bufio.NewScanner(conn)
	status := 0
	colo := ""

	// Parse Status Line: e.g. "HTTP/1.1 200 OK"
	if scanner.Scan() {
		line := scanner.Text()
		parts := strings.Split(line, " ")
		if len(parts) >= 2 {
			if code, err := strconv.Atoi(parts[1]); err == nil {
				status = code
			}
		}
	}

	// Read lines to find colo=
	linesRead := 0
	for scanner.Scan() && linesRead < 30 {
		linesRead++
		line := scanner.Text()
		if m := coloRegex.FindStringSubmatch(line); len(m) > 1 {
			colo = strings.ToUpper(m[1])
			break
		}
	}

	return status, colo
}
