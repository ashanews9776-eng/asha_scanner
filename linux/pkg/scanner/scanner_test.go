package scanner

import (
	"strings"
	"testing"
)

func TestRangeManagerSampling(t *testing.T) {
	rm, err := NewRangeManager(nil)
	if err != nil {
		t.Fatalf("failed to create RangeManager: %v", err)
	}

	samples := rm.Sample(50)
	if len(samples) != 50 {
		t.Errorf("expected 50 samples, got %d", len(samples))
	}

	for _, ip := range samples {
		if !strings.Contains(ip, ".") {
			t.Errorf("invalid IPv4 sampled: %s", ip)
		}
	}
}

func TestExportUtilities(t *testing.T) {
	results := []ScanResult{
		{
			IP:            "104.16.1.1",
			Port:          443,
			Latencies:     []int64{120, 130, 125},
			Attempts:      3,
			Colo:          "FRA",
			ThroughputBps: 2_500_000,
			Healthy:       true,
		},
	}

	csv := ExportCSV(results)
	if !strings.Contains(csv, "104.16.1.1") || !strings.Contains(csv, "FRA") {
		t.Errorf("CSV export missing expected values: %s", csv)
	}

	jsonOut, err := ExportJSON(results)
	if err != nil || !strings.Contains(jsonOut, "104.16.1.1") {
		t.Errorf("JSON export error: %v", err)
	}

	ips := ExportIPs(results)
	if strings.TrimSpace(ips) != "104.16.1.1:443" {
		t.Errorf("unexpected IP export: %s", ips)
	}
}
