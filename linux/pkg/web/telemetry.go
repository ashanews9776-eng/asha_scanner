package web

import (
	"encoding/json"
	"fmt"
	"io"
	"net"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Telemetry replaces the old hardcoded status numbers with REAL measurements:
//
//   - ping: a SOCKS5 CONNECT to 1.1.1.1:443 timed end-to-end (≈ one tunnel RTT
//     once the tunnel is established)
//   - download/upload: byte deltas of the TUN interface's /sys statistics —
//     every byte the system actually pushes through the tunnel, counted
//   - exit country: a geoip lookup performed THROUGH the tunnel (the exit's
//     country, not the user's), cached for a minute
//
// The sampler runs only while the tunnel reports CONNECTED and stops itself
// otherwise, so an idle app costs nothing.
type Telemetry struct {
	mu        sync.RWMutex
	pingMs    int64
	downBps   int64
	upBps     int64
	country   string
	tunDevice string
	socksPort int
	lastRx    int64
	lastTx    int64
	lastAt    time.Time
	lastPing  time.Time
	lastGeo   time.Time
}

func NewTelemetry(tunDevice string, socksPort int) *Telemetry {
	return &Telemetry{tunDevice: tunDevice, socksPort: socksPort}
}

// Snapshot returns the current real measurements.
func (t *Telemetry) Snapshot() (pingMs int64, downBps int64, upBps int64, country string) {
	t.mu.RLock()
	defer t.mu.RUnlock()
	return t.pingMs, t.downBps, t.upBps, t.country
}

// Restart re-targets the sampler at the new session's SOCKS port and TUN
// device (a fresh Manager is created per connect).
func (t *Telemetry) Restart(socksPort int, tunDevice string) {
	t.mu.Lock()
	t.socksPort = socksPort
	t.tunDevice = tunDevice
	t.downBps, t.upBps, t.pingMs = 0, 0, 0
	t.lastRx, t.lastTx, t.lastAt = 0, 0, time.Time{}
	t.mu.Unlock()
}

// Reset zeroes the counters after a disconnect.
func (t *Telemetry) Reset() {
	t.mu.Lock()
	t.downBps, t.upBps, t.pingMs = 0, 0, 0
	t.lastRx, t.lastTx, t.lastAt = 0, 0, time.Time{}
	t.mu.Unlock()
}

// DefaultSocksPort is the shared default for the plain-xray custom path.
const DefaultSocksPort = 10808

// Run samples until stop is closed. Cheap when the tunnel is not connected.
func (t *Telemetry) Run(status func() string, stop <-chan struct{}) {
	tick := time.NewTicker(1 * time.Second)
	defer tick.Stop()
	for {
		select {
		case <-stop:
			return
		case <-tick.C:
		}
		if status() != "CONNECTED" {
			t.mu.Lock()
			t.downBps, t.upBps, t.pingMs = 0, 0, 0
			t.lastRx, t.lastTx, t.lastAt = 0, 0, time.Time{}
			t.mu.Unlock()
			continue
		}
		t.sampleTraffic()
		if time.Since(t.lastPing) >= 5*time.Second {
			t.lastPing = time.Now()
			if ms, ok := socksConnectPing(t.socksPort); ok {
				t.mu.Lock()
				t.pingMs = ms
				t.mu.Unlock()
			}
		}
		if time.Since(t.lastGeo) >= 60*time.Second {
			t.lastGeo = time.Now()
			if c := lookupExitCountry(t.socksPort); c != "" {
				t.mu.Lock()
				t.country = c
				t.mu.Unlock()
			}
		}
	}
}

// sampleTraffic diffs the TUN interface counters once per second.
func (t *Telemetry) sampleTraffic() {
	rx, tx, ok := readDevStats(t.tunDevice)
	if !ok {
		return
	}
	now := time.Now()
	t.mu.Lock()
	defer t.mu.Unlock()
	if !t.lastAt.IsZero() {
		elapsed := now.Sub(t.lastAt).Seconds()
		if elapsed > 0.2 {
			t.downBps = int64(float64(rx-t.lastRx) / elapsed)
			t.upBps = int64(float64(tx-t.lastTx) / elapsed)
		}
	}
	t.lastRx, t.lastTx, t.lastAt = rx, tx, now
}

func readDevStats(dev string) (rx, tx int64, ok bool) {
	data, err := os.ReadFile("/sys/class/net/" + dev + "/statistics/rx_bytes")
	if err != nil {
		return 0, 0, false
	}
	rx, _ = strconv.ParseInt(strings.TrimSpace(string(data)), 10, 64)
	data, err = os.ReadFile("/sys/class/net/" + dev + "/statistics/tx_bytes")
	if err != nil {
		return 0, 0, false
	}
	tx, _ = strconv.ParseInt(strings.TrimSpace(string(data)), 10, 64)
	return rx, tx, true
}

// socksConnectPing dials 1.1.1.1:443 through the local SOCKS and returns the
// time until the CONNECT reply — approximately one tunnel round trip.
func socksConnectPing(port int) (int64, bool) {
	conn, err := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", port), 2*time.Second)
	if err != nil {
		return 0, false
	}
	defer conn.Close()
	start := time.Now()
	_ = conn.SetDeadline(start.Add(4 * time.Second))
	if _, err := conn.Write([]byte{0x05, 0x01, 0x00}); err != nil {
		return 0, false
	}
	greet := make([]byte, 2)
	if _, err := io.ReadFull(conn, greet); err != nil || greet[0] != 0x05 || greet[1] != 0x00 {
		return 0, false
	}
	if _, err := conn.Write([]byte{0x05, 0x01, 0x00, 0x01, 1, 1, 1, 1, 0x01, 0xBB}); err != nil {
		return 0, false
	}
	resp := make([]byte, 10)
	if _, err := io.ReadFull(conn, resp); err != nil || resp[1] != 0x00 {
		return 0, false
	}
	return time.Since(start).Milliseconds(), true
}

// geoHostGuard validates the lookup endpoint is the expected public API host —
// a poisoned DNS answer on a hostile carrier must not steer the lookup at
// local infrastructure.
func geoHostGuard(host string) error {
	if host != "api.ip.sb" {
		return fmt.Errorf("unexpected geo lookup host: %s", host)
	}
	ips, err := net.LookupIP(host)
	if err != nil {
		return err
	}
	for _, ip := range ips {
		if ip.IsLoopback() || ip.IsPrivate() || ip.IsLinkLocalUnicast() || ip.IsUnspecified() {
			return fmt.Errorf("geo lookup host resolved to a non-public address")
		}
	}
	return nil
}

// lookupExitCountry asks the geoip API THROUGH the tunnel (plain HTTP over the
// SOCKS CONNECT to port 80) so the answer describes the exit, not the user.
func lookupExitCountry(port int) string {
	host := "api.ip.sb"
	if err := geoHostGuard(host); err != nil {
		return ""
	}
	conn, err := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", port), 2*time.Second)
	if err != nil {
		return ""
	}
	defer conn.Close()
	_ = conn.SetDeadline(time.Now().Add(8 * time.Second))
	if _, err := conn.Write([]byte{0x05, 0x01, 0x00}); err != nil {
		return ""
	}
	greet := make([]byte, 2)
	if _, err := io.ReadFull(conn, greet); err != nil || greet[0] != 0x05 || greet[1] != 0x00 {
		return ""
	}
	// CONNECT api.ip.sb:80
	req := []byte{0x05, 0x01, 0x00, 0x03, byte(len(host))}
	req = append(req, host...)
	req = append(req, 0x00, 0x50)
	if _, err := conn.Write(req); err != nil {
		return ""
	}
	resp := make([]byte, 10)
	if _, err := io.ReadFull(conn, resp); err != nil || resp[1] != 0x00 {
		return ""
	}
	httpReq := "GET /geoip HTTP/1.1\r\nHost: " + host + "\r\nUser-Agent: AshaScanner\r\nAccept: application/json\r\nConnection: close\r\n\r\n"
	if _, err := conn.Write([]byte(httpReq)); err != nil {
		return ""
	}
	body, _ := io.ReadAll(conn)
	text := string(body)
	marker := strings.LastIndex(text, "{")
	end := strings.LastIndex(text, "}")
	if marker < 0 || end <= marker {
		return ""
	}
	var out struct {
		CountryCode string `json:"country_code"`
		Country     string `json:"country"`
	}
	if err := json.Unmarshal([]byte(text[marker:end+1]), &out); err != nil {
		return ""
	}
	cc := strings.ToUpper(strings.TrimSpace(out.CountryCode))
	if len(cc) != 2 {
		return ""
	}
	return cc
}
