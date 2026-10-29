package scanner

import (
	"fmt"
	"io"
	"net"
	"strconv"
	"time"
)

// MeasureThroughput tests the actual download speed against Cloudflare speed endpoints.
func MeasureThroughput(r ScanResult, cfg ScanConfig) ScanResult {
	addr := net.JoinHostPort(r.IP, strconv.Itoa(r.Port))
	sni := "speed.cloudflare.com"
	if cfg.SNIOverride != "" {
		sni = cfg.SNIOverride
	}

	dialer := net.Dialer{Timeout: 3 * time.Second}
	conn, err := dialer.Dial("tcp", addr)
	if err != nil {
		r.SpeedTested = true
		r.ThroughputBps = 0
		return r
	}
	defer conn.Close()

	tlsConn, err := DialTLSHandshake(conn, sni, 3*time.Second, cfg.AntiDpi)
	if err != nil {
		r.SpeedTested = true
		r.ThroughputBps = 0
		return r
	}
	defer tlsConn.Close()

	// Request 5MB download payload from Cloudflare speed test API
	req := fmt.Sprintf("GET /__down?bytes=5242880 HTTP/1.1\r\nHost: %s\r\nUser-Agent: AshaScanner-Linux\r\nAccept: */*\r\nConnection: close\r\n\r\n", sni)
	_ = tlsConn.SetDeadline(time.Now().Add(6 * time.Second))

	if _, err := tlsConn.Write([]byte(req)); err != nil {
		r.SpeedTested = true
		r.ThroughputBps = 0
		return r
	}

	buf := make([]byte, 32*1024)
	var totalBytes int64
	startTransfer := time.Time{}

	// Skip headers to measure pure payload throughput
	headerDone := false
	var headerBuf []byte

	for {
		n, err := tlsConn.Read(buf)
		if n > 0 {
			if !headerDone {
				headerBuf = append(headerBuf, buf[:n]...)
				// Find \r\n\r\n separator
				for i := 0; i < len(headerBuf)-3; i++ {
					if headerBuf[i] == '\r' && headerBuf[i+1] == '\n' && headerBuf[i+2] == '\r' && headerBuf[i+3] == '\n' {
						headerDone = true
						payloadFirstChunk := len(headerBuf) - (i + 4)
						totalBytes += int64(payloadFirstChunk)
						startTransfer = time.Now()
						break
					}
				}
			} else {
				totalBytes += int64(n)
			}
		}

		if err != nil {
			if err == io.EOF {
				break
			}
			break
		}

		// Cap measurement to 3 seconds for quick testing
		if headerDone && time.Since(startTransfer) >= 3*time.Second {
			break
		}
	}

	r.SpeedTested = true
	if headerDone && !startTransfer.IsZero() && totalBytes > 0 {
		durationSec := time.Since(startTransfer).Seconds()
		if durationSec > 0.1 {
			r.ThroughputBps = float64(totalBytes) / durationSec
		}
	}

	return r
}
