package scanner

import (
	_ "embed"
	"encoding/binary"
	"fmt"
	"math/rand"
	"net"
	"strings"
	"sync"
	"time"
)

//go:embed data/cf_ipv4.txt
var embeddedIPv4Ranges string

//go:embed data/cf_domains.txt
var embeddedFallbackDomains string

// Official Cloudflare IPv4 CIDR blocks (fallback if asset is empty)
var officialCloudflareV4 = []string{
	"173.245.48.0/20",
	"103.21.244.0/22",
	"103.22.200.0/22",
	"103.31.4.0/22",
	"141.101.64.0/18",
	"108.162.192.0/18",
	"190.93.240.0/20",
	"188.114.96.0/20",
	"197.234.240.0/22",
	"198.41.128.0/17",
	"162.158.0.0/15",
	"104.16.0.0/13",
	"104.24.0.0/14",
	"172.64.0.0/13",
	"131.0.72.0/22",
}

// Subnet represents a parsed CIDR block with precalculated address bounds.
type Subnet struct {
	BaseIP uint32
	Count  uint32
}

// RangeManager generates random and target IPs from Cloudflare CIDR blocks.
type RangeManager struct {
	subnets []Subnet
	rng     *rand.Rand
	mu      sync.Mutex
}

// NewRangeManager creates a manager from the provided or default CIDR list.
func NewRangeManager(customRanges []string) (*RangeManager, error) {
	rawList := customRanges
	if len(rawList) == 0 {
		rawList = loadEmbeddedRanges()
	}
	if len(rawList) == 0 {
		rawList = officialCloudflareV4
	}

	var subnets []Subnet
	for _, line := range rawList {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		_, ipNet, err := net.ParseCIDR(line)
		if err != nil {
			continue
		}
		ip4 := ipNet.IP.To4()
		if ip4 == nil {
			continue // IPv4 only for now
		}
		ones, bits := ipNet.Mask.Size()
		if bits != 32 {
			continue
		}
		count := uint32(1) << uint32(32-ones)
		base := binary.BigEndian.Uint32(ip4)
		subnets = append(subnets, Subnet{
			BaseIP: base,
			Count:  count,
		})
	}

	if len(subnets) == 0 {
		return nil, fmt.Errorf("no valid CIDR blocks found")
	}

	return &RangeManager{
		subnets: subnets,
		rng:     rand.New(rand.NewSource(time.Now().UnixNano())),
	}, nil
}

// Sample generates count unique or near-unique random IPs across subnets.
func (rm *RangeManager) Sample(count int) []string {
	rm.mu.Lock()
	defer rm.mu.Unlock()

	seen := make(map[string]struct{}, count)
	results := make([]string, 0, count)

	maxAttempts := count * 4
	attempts := 0

	for len(results) < count && attempts < maxAttempts {
		attempts++
		subnet := rm.subnets[rm.rng.Intn(len(rm.subnets))]
		offset := rm.rng.Uint32() % subnet.Count
		// Avoid network and broadcast address for subnets > /31
		if subnet.Count > 2 {
			if offset == 0 {
				offset = 1
			} else if offset == subnet.Count-1 {
				offset = subnet.Count - 2
			}
		}

		ipNum := subnet.BaseIP + offset
		ipBytes := make([]byte, 4)
		binary.BigEndian.PutUint32(ipBytes, ipNum)
		ipStr := net.IP(ipBytes).String()

		if _, exists := seen[ipStr]; !exists {
			seen[ipStr] = struct{}{}
			results = append(results, ipStr)
		}
	}

	return results
}

func loadEmbeddedRanges() []string {
	lines := strings.Split(embeddedIPv4Ranges, "\n")
	res := make([]string, 0, len(lines))
	for _, l := range lines {
		l = strings.TrimSpace(l)
		if l != "" && !strings.HasPrefix(l, "#") {
			res = append(res, l)
		}
	}
	return res
}

// LoadFallbackDomains retrieves the list of open-site domains.
func LoadFallbackDomains() []string {
	lines := strings.Split(embeddedFallbackDomains, "\n")
	res := make([]string, 0, len(lines))
	for _, l := range lines {
		l = strings.TrimSpace(l)
		if l != "" && !strings.HasPrefix(l, "#") {
			res = append(res, l)
		}
	}
	return res
}

// ResolveDomains performs DNS lookups on domains to gather candidate Cloudflare IPs.
func ResolveDomains(domains []string, limit int) []string {
	seen := make(map[string]struct{})
	var ips []string

	for _, domain := range domains {
		domain = strings.TrimSpace(domain)
		if domain == "" {
			continue
		}
		addrs, err := net.LookupHost(domain)
		if err != nil {
			continue
		}
		for _, addr := range addrs {
			if strings.Contains(addr, ":") {
				continue // Skip IPv6 for IPv4 scan
			}
			if _, ok := seen[addr]; !ok {
				seen[addr] = struct{}{}
				ips = append(ips, addr)
				if len(ips) >= limit {
					return ips
				}
			}
		}
	}
	return ips
}
