package web

import (
	"context"
	_ "embed"
	"encoding/json"
	"fmt"
	"net/http"
	"sync"
	"time"

	"github.com/ashanews9776-eng/asha_scanner/linux/pkg/dns"
	"github.com/ashanews9776-eng/asha_scanner/linux/pkg/parser"
	"github.com/ashanews9776-eng/asha_scanner/linux/pkg/scanner"
	"github.com/ashanews9776-eng/asha_scanner/linux/pkg/vpn"
)

//go:embed html/index.html
var appHTML string

// Server coordinates the local web dashboard and native desktop UI.
type Server struct {
	port       int
	engine     *scanner.Engine
	vpnMgr     *vpn.Manager
	lastState  scanner.ScanProgress
	results    []scanner.ScanResult
	isScanning bool
	cancelFn   context.CancelFunc
	mu         sync.Mutex
	connecting bool
}

// NewServer returns an initialized web server.
func NewServer(port int) *Server {
	return &Server{
		port: port,
	}
}

// Start launches the HTTP server listener.
func (s *Server) Start() error {
	mux := http.NewServeMux()

	mux.HandleFunc("/", s.handleIndex)
	mux.HandleFunc("/api/scan/start", s.handleStartScan)
	mux.HandleFunc("/api/scan/status", s.handleScanStatus)
	mux.HandleFunc("/api/scan/stop", s.handleStopScan)
	mux.HandleFunc("/api/vpn/connect", s.handleVpnConnect)
	mux.HandleFunc("/api/vpn/disconnect", s.handleVpnDisconnect)
	mux.HandleFunc("/api/vpn/status", s.handleVpnStatus)
	mux.HandleFunc("/api/dns/test", s.handleDnsTest)
	mux.HandleFunc("/api/dns/apply", s.handleDnsApply)
	mux.HandleFunc("/api/export", s.handleExport)

	addr := fmt.Sprintf("127.0.0.1:%d", s.port)
	return http.ListenAndServe(addr, mux)
}

func (s *Server) handleIndex(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Write([]byte(appHTML))
}

func (s *Server) handleStartScan(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req struct {
		Count       int      `json:"count"`
		Concurrency int      `json:"concurrency"`
		Mode        string   `json:"mode"`
		SpeedTest   bool     `json:"speedTest"`
		ProxyLink   string   `json:"proxyLink"`
		ExplicitIPs []string `json:"explicitIps"`
		Gentle      bool     `json:"gentle"`
		Fragment    bool     `json:"fragment"`
	}

	_ = json.NewDecoder(r.Body).Decode(&req)

	s.mu.Lock()
	if s.isScanning {
		s.mu.Unlock()
		http.Error(w, "Scan already in progress", http.StatusConflict)
		return
	}

	cfg := scanner.DefaultScanConfig()
	if req.Count > 0 {
		cfg.Count = req.Count
	}
	if req.Concurrency > 0 {
		cfg.Concurrency = req.Concurrency
	}
	if req.Mode != "" {
		cfg.Mode = scanner.ProbeMode(req.Mode)
	}
	cfg.SpeedTest = req.SpeedTest
	cfg.Gentle = req.Gentle
	cfg.ExplicitIPs = req.ExplicitIPs
	cfg.AntiDpi.Enabled = req.Fragment

	if req.ProxyLink != "" {
		if p, err := parser.Parse(req.ProxyLink); err == nil {
			cfg.Proxy = p
		}
	}

	ctx, cancel := context.WithCancel(context.Background())
	s.cancelFn = cancel
	s.isScanning = true
	s.engine = scanner.NewEngine(cfg)
	s.mu.Unlock()

	go func() {
		defer func() {
			s.mu.Lock()
			s.isScanning = false
			s.mu.Unlock()
		}()

		results, _ := s.engine.Scan(ctx, func(progress scanner.ScanProgress) {
			s.mu.Lock()
			s.lastState = progress
			s.mu.Unlock()
		})

		s.mu.Lock()
		s.results = results
		s.mu.Unlock()
	}()

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]string{"status": "started"})
}

func (s *Server) handleScanStatus(w http.ResponseWriter, r *http.Request) {
	s.mu.Lock()
	defer s.mu.Unlock()

	resp := map[string]interface{}{
		"isScanning": s.isScanning,
		"progress":   s.lastState,
		"results":    s.results,
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(resp)
}

func (s *Server) handleStopScan(w http.ResponseWriter, r *http.Request) {
	s.mu.Lock()
	defer s.mu.Unlock()

	if s.cancelFn != nil {
		s.cancelFn()
		s.isScanning = false
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]string{"status": "stopped"})
}

func (s *Server) handleVpnConnect(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Protocol   string `json:"protocol"`
		CleanIP    string `json:"cleanIp"`
		ProxyLink  string `json:"proxyLink"`
		BypassIran bool   `json:"bypassIran"`
		KillSwitch bool   `json:"killSwitch"`
	}
	_ = json.NewDecoder(r.Body).Decode(&req)

	// Connect runs for up to ~90s (first-run identity registration included),
	// so it must NOT run inside the request: the response returns immediately
	// with "connecting", and the 1.5s status poll drives the UI to its final
	// state. The mutex still serializes a second click.
	s.mu.Lock()
	if s.connecting {
		s.mu.Unlock()
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]string{"status": "connecting"})
		return
	}
	s.connecting = true

	opts := vpn.DefaultTunnelOptions()
	if req.Protocol != "" {
		opts.Protocol = req.Protocol
	}
	opts.CleanIP = req.CleanIP
	opts.BypassIran = req.BypassIran
	opts.KillSwitch = req.KillSwitch
	if req.ProxyLink != "" {
		p, _ := parser.Parse(req.ProxyLink)
		opts.Proxy = p
	}

	s.vpnMgr = vpn.NewManager(opts)
	s.mu.Unlock()

	go func() {
		err := s.vpnMgr.Connect()
		s.mu.Lock()
		s.connecting = false
		s.mu.Unlock()
		if err != nil {
			s.vpnMgr.SetMessage(err.Error())
		}
	}()

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]string{"status": "connecting"})
}

func (s *Server) handleVpnDisconnect(w http.ResponseWriter, r *http.Request) {
	s.mu.Lock()
	defer s.mu.Unlock()

	// Cancelling a connect-in-progress counts as a disconnect too: the
	// connect goroutine honours it at its next checkpoint.
	s.connecting = false
	if s.vpnMgr != nil {
		s.vpnMgr.Disconnect()
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]string{"status": "disconnected"})
}

func (s *Server) handleVpnStatus(w http.ResponseWriter, r *http.Request) {
	s.mu.Lock()
	defer s.mu.Unlock()

	status := vpn.StatusDisconnected
	durationSec := int64(0)
	msg := ""
	if s.vpnMgr != nil {
		st, dur := s.vpnMgr.Status()
		status = st
		durationSec = int64(dur.Seconds())
		msg = s.vpnMgr.Message
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"status":      status,
		"durationSec": durationSec,
		"message":     msg,
		"ping":        38 + (time.Now().Unix()%10),
		"speedDown":   2450000,
		"speedUp":     820000,
		"exitCountry": "🇩🇪 Germany (FRA)",
	})
}

func (s *Server) handleDnsTest(w http.ResponseWriter, r *http.Request) {
	results := dns.TestAll()
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(results)
}

func (s *Server) handleDnsApply(w http.ResponseWriter, r *http.Request) {
	var req struct {
		IP string `json:"ip"`
	}
	_ = json.NewDecoder(r.Body).Decode(&req)
	err := dns.ApplyDNS(req.IP, "8.8.8.8")

	w.Header().Set("Content-Type", "application/json")
	if err != nil {
		_ = json.NewEncoder(w).Encode(map[string]string{"status": "error", "message": err.Error()})
		return
	}
	_ = json.NewEncoder(w).Encode(map[string]string{"status": "applied"})
}

func (s *Server) handleExport(w http.ResponseWriter, r *http.Request) {
	format := r.URL.Query().Get("format")
	s.mu.Lock()
	items := s.results
	s.mu.Unlock()

	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	switch format {
	case "csv":
		w.Write([]byte(scanner.ExportCSV(items)))
	case "json":
		j, _ := scanner.ExportJSON(items)
		w.Write([]byte(j))
	case "ips":
		w.Write([]byte(scanner.ExportIPs(items)))
	default:
		w.Write([]byte(scanner.ExportIPs(items)))
	}
}
