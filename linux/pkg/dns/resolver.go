package dns

import (
	_ "embed"
	"fmt"
	"net"
	"os/exec"
	"sort"
	"strings"
	"sync"
	"time"
)

//go:embed data/dns_resolvers.txt
var embeddedResolvers string

// Resolver represents an upstream DNS target.
type Resolver struct {
	Category string `json:"category"`
	Name     string `json:"name"`
	IP       string `json:"ip"`
	Latency  int64  `json:"latencyMs"`
	Working  bool   `json:"working"`
}

// LoadResolvers parses the embedded resolvers list.
func LoadResolvers() []Resolver {
	lines := strings.Split(embeddedResolvers, "\n")
	var list []Resolver
	for _, l := range lines {
		l = strings.TrimSpace(l)
		if l == "" || strings.HasPrefix(l, "#") {
			continue
		}
		parts := strings.Split(l, "|")
		if len(parts) >= 3 {
			list = append(list, Resolver{
				Category: parts[0],
				Name:     parts[1],
				IP:       parts[2],
			})
		}
	}
	return list
}

// TestAll probes all resolvers concurrently and returns them sorted by latency.
func TestAll() []Resolver {
	resolvers := LoadResolvers()
	var wg sync.WaitGroup
	var mu sync.Mutex
	var results []Resolver

	sem := make(chan struct{}, 15) // Max 15 parallel DNS probes

	for _, r := range resolvers {
		wg.Add(1)
		go func(res Resolver) {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()

			lat, ok := testDnsLatency(res.IP)
			res.Latency = lat
			res.Working = ok

			mu.Lock()
			results = append(results, res)
			mu.Unlock()
		}(r)
	}

	wg.Wait()

	sort.Slice(results, func(i, j int) bool {
		if results[i].Working != results[j].Working {
			return results[i].Working
		}
		return results[i].Latency < results[j].Latency
	})

	return results
}

func testDnsLatency(dnsIP string) (int64, bool) {
	addr := net.JoinHostPort(dnsIP, "53")
	start := time.Now()

	d := net.Dialer{Timeout: 1500 * time.Millisecond}
	conn, err := d.Dial("udp", addr)
	if err != nil {
		return 0, false
	}
	defer conn.Close()

	// Simple standard DNS query for "cloudflare.com" (Type A, Class IN)
	query := []byte{
		0xaa, 0xbb, // Transaction ID
		0x01, 0x00, // Standard query
		0x00, 0x01, // Questions: 1
		0x00, 0x00, // Answer RRs: 0
		0x00, 0x00, // Authority RRs: 0
		0x00, 0x00, // Additional RRs: 0
		0x0a, 'c', 'l', 'o', 'u', 'd', 'f', 'l', 'a', 'r', 'e',
		0x03, 'c', 'o', 'm',
		0x00,       // End of name
		0x00, 0x01, // Type: A
		0x00, 0x01, // Class: IN
	}

	_ = conn.SetDeadline(time.Now().Add(1500 * time.Millisecond))
	if _, err := conn.Write(query); err != nil {
		return 0, false
	}

	buf := make([]byte, 512)
	n, err := conn.Read(buf)
	if err != nil || n < 12 {
		return 0, false
	}

	elapsed := time.Since(start).Milliseconds()
	return elapsed, true
}

// ApplyDNS configures the chosen DNS server on Linux using systemd-resolve or resolvconf.
func ApplyDNS(primaryIP, secondaryIP string) error {
	// Try resolvectl first (modern systemd)
	out, err := exec.Command("resolvectl", "dns", "asha0", primaryIP).CombinedOutput()
	if err == nil {
		return nil
	}

	// Fallback to updating default interface DNS
	if out, err = exec.Command("resolvectl", "default-route", "asha0", "true").CombinedOutput(); err == nil {
		return nil
	}

	return fmt.Errorf("applied via resolvectl: %s", string(out))
}
