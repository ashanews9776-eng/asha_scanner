package scanner

import (
	"strings"
	"time"

	"github.com/ashanews9776-eng/asha_scanner/linux/pkg/parser"
)

// ProbeMode represents the handshake depth.
type ProbeMode string

const (
	ProbeTCP  ProbeMode = "tcp"
	ProbeTLS  ProbeMode = "tls"
	ProbeHTTP ProbeMode = "http"
)

// ScanTier represents predefined performance profiles.
type ScanTier string

const (
	TierTurbo     ScanTier = "turbo"
	TierBalanced  ScanTier = "balanced"
	TierThorough  ScanTier = "thorough"
	TierStealth   ScanTier = "stealth"
	TierIronclad  ScanTier = "ironclad"
	TierCustom    ScanTier = "custom"
)

// AntiDpiConfig configures TCP and TLS packet fragmentation.
type AntiDpiConfig struct {
	Enabled    bool          `json:"enabled"`
	FirstChunk int           `json:"firstChunk"`
	NextChunk  int           `json:"nextChunk"`
	Delay      time.Duration `json:"delay"`
}

// DefaultAntiDpiConfig returns standard anti-DPI ClientHello fragmentation settings.
func DefaultAntiDpiConfig() AntiDpiConfig {
	return AntiDpiConfig{
		Enabled:    true,
		FirstChunk: 104,
		NextChunk:  114,
		Delay:      1 * time.Millisecond,
	}
}

// ScanConfig holds all tunable parameters for an execution run.
type ScanConfig struct {
	Tier              ScanTier      `json:"tier"`
	Count             int           `json:"count"`
	Concurrency       int           `json:"concurrency"`
	Timeout           time.Duration `json:"timeout"`
	Tries             int           `json:"tries"`
	Ports             []int         `json:"ports"`
	Mode              ProbeMode     `json:"mode"`
	Top               int           `json:"top"`
	StabilityCount    int           `json:"stabilityCount"`
	SpeedTest         bool          `json:"speedTest"`
	SmartStop         bool          `json:"smartStop"`
	Gentle            bool          `json:"gentle"`
	FallbackToDomains bool          `json:"fallbackToDomains"`
	SNIOverride       string        `json:"sniOverride"`
	AntiDpi           AntiDpiConfig `json:"antiDpi"`
	ExplicitIPs       []string      `json:"explicitIps"`
	CustomV4Ranges    []string      `json:"customV4Ranges"`
	FallbackDomains   []string      `json:"fallbackDomains"`
	Proxy             *parser.ProxyConfig `json:"proxy,omitempty"`
}

// DefaultScanConfig creates balanced scanning defaults.
func DefaultScanConfig() ScanConfig {
	return ScanConfig{
		Tier:              TierBalanced,
		Count:             500,
		Concurrency:       50,
		Timeout:           3 * time.Second,
		Tries:             3,
		Ports:             []int{443},
		Mode:              ProbeHTTP,
		Top:               10,
		StabilityCount:    3,
		SpeedTest:         true,
		SmartStop:         true,
		Gentle:            false,
		FallbackToDomains: true,
		AntiDpi:           DefaultAntiDpiConfig(),
	}
}

// ScanResult holds the measurement outcome for a single IP.
type ScanResult struct {
	IP            string    `json:"ip"`
	Port          int       `json:"port"`
	Mode          ProbeMode `json:"mode"`
	Latencies     []int64   `json:"latenciesMs"`
	Attempts      int       `json:"attempts"`
	TLSOk         bool      `json:"tlsOk"`
	HTTPStatus    int       `json:"httpStatus"`
	Colo          string    `json:"colo"`
	ThroughputBps float64   `json:"throughputBytesPerSec"`
	SpeedTested   bool      `json:"speedTested"`
	Healthy       bool      `json:"healthy"`
	PassRate      float64   `json:"passRate"`
	Timestamp     int64     `json:"timestamp"`
}

// AvgLatencyMs returns the mean latency in milliseconds.
func (r *ScanResult) AvgLatencyMs() float64 {
	if len(r.Latencies) == 0 {
		return 0
	}
	var sum int64
	for _, l := range r.Latencies {
		sum += l
	}
	return float64(sum) / float64(len(r.Latencies))
}

// MinLatencyMs returns the lowest latency in milliseconds.
func (r *ScanResult) MinLatencyMs() int64 {
	if len(r.Latencies) == 0 {
		return 0
	}
	min := r.Latencies[0]
	for _, l := range r.Latencies[1:] {
		if l < min {
			min = l
		}
	}
	return min
}

// JitterMs returns the variation between max and min latencies.
func (r *ScanResult) JitterMs() int64 {
	if len(r.Latencies) < 2 {
		return 0
	}
	min := r.Latencies[0]
	max := r.Latencies[0]
	for _, l := range r.Latencies[1:] {
		if l < min {
			min = l
		}
		if l > max {
			max = l
		}
	}
	return max - min
}

// Loss returns the packet loss fraction between 0.0 and 1.0.
func (r *ScanResult) Loss() float64 {
	if r.Attempts <= 0 {
		return 0.0
	}
	loss := 1.0 - (float64(len(r.Latencies)) / float64(r.Attempts))
	if loss < 0 {
		return 0.0
	}
	if loss > 1.0 {
		return 1.0
	}
	return loss
}

// ThroughputMbps returns the measured download speed in Megabits per second.
func (r *ScanResult) ThroughputMbps() float64 {
	return (r.ThroughputBps * 8.0) / 1_000_000.0
}

// Endpoint returns the formatted IP:Port string.
func (r *ScanResult) Endpoint() string {
	if strings.Contains(r.IP, ":") && !strings.HasPrefix(r.IP, "[") {
		return "[" + r.IP + "]:" + string(rune(r.Port))
	}
	return r.IP + ":" + string(rune(r.Port))
}

// ScanPhase tracks the engine lifecycle.
type ScanPhase string

const (
	PhaseIdle       ScanPhase = "IDLE"
	PhaseProbing    ScanPhase = "PROBING"
	PhaseResolving  ScanPhase = "RESOLVING"
	PhaseStability  ScanPhase = "STABILITY"
	PhaseValidating ScanPhase = "VALIDATING"
	PhaseDone       ScanPhase = "DONE"
	PhaseCancelled  ScanPhase = "CANCELLED"
	PhaseError      ScanPhase = "ERROR"
)

// ScanProgress is the event emitted periodically during a scan.
type ScanProgress struct {
	Phase         ScanPhase    `json:"phase"`
	Tested        int          `json:"tested"`
	Total         int          `json:"total"`
	Found         int          `json:"found"`
	Validated     int          `json:"validated"`
	ValidateTotal int          `json:"validateTotal"`
	ElapsedMs     int64        `json:"elapsedMs"`
	Best          []ScanResult `json:"best"`
	Error         string       `json:"error,omitempty"`
	UsingFallback bool         `json:"usingFallback"`
	LatencyTrace  []int        `json:"latencyTrace"`
	Logs          []string     `json:"logs"`
}
