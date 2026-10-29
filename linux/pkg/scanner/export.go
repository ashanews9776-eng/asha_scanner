package scanner

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"strings"

	"github.com/ashanews9776-eng/asha_scanner/linux/pkg/parser"
)

// ExportCSV formats scan results as CSV.
func ExportCSV(results []ScanResult) string {
	var sb strings.Builder
	sb.WriteString("IP,Port,AvgLatency(ms),MinLatency(ms),Jitter(ms),Loss(%),Speed(Mbps),Colo,Healthy\n")
	for _, r := range results {
		sb.WriteString(fmt.Sprintf("%s,%d,%.1f,%d,%d,%.1f,%.2f,%s,%t\n",
			r.IP, r.Port, r.AvgLatencyMs(), r.MinLatencyMs(), r.JitterMs(),
			r.Loss()*100, r.ThroughputMbps(), r.Colo, r.Healthy))
	}
	return sb.String()
}

// ExportJSON serializes scan results to formatted JSON.
func ExportJSON(results []ScanResult) (string, error) {
	bytes, err := json.MarshalIndent(results, "", "  ")
	if err != nil {
		return "", err
	}
	return string(bytes), nil
}

// ExportIPs returns a clean newline-separated list of IP:Port endpoints.
func ExportIPs(results []ScanResult) string {
	var sb strings.Builder
	for _, r := range results {
		sb.WriteString(fmt.Sprintf("%s:%d\n", r.IP, r.Port))
	}
	return sb.String()
}

// ExportConfigs transforms a base proxy config with each clean IP.
func ExportConfigs(results []ScanResult, baseProxy *parser.ProxyConfig) string {
	if baseProxy == nil {
		return ""
	}
	var sb strings.Builder
	for _, r := range results {
		cfgLink := baseProxy.BuildWithCleanIP(r.IP)
		sb.WriteString(cfgLink + "\n")
	}
	return sb.String()
}

// ExportSubscription generates a base64 encoded subscription format.
func ExportSubscription(results []ScanResult, baseProxy *parser.ProxyConfig) string {
	if baseProxy == nil {
		return ""
	}
	plainConfigs := ExportConfigs(results, baseProxy)
	return base64.StdEncoding.EncodeToString([]byte(plainConfigs))
}
