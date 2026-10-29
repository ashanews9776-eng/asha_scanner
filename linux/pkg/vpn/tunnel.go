package vpn

import (
	"bufio"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"syscall"
	"time"

	"github.com/ashanews9776-eng/asha_scanner/linux/pkg/parser"
)

// TunnelStatus tracks active connection states.
type TunnelStatus string

const (
	StatusDisconnected TunnelStatus = "DISCONNECTED"
	StatusConnecting   TunnelStatus = "CONNECTING"
	StatusConnected    TunnelStatus = "CONNECTED"
	StatusError        TunnelStatus = "ERROR"
)

// TunnelOptions configures the VPN setup.
type TunnelOptions struct {
	Protocol      string // pow | aether24 | masque | wom | wireguard | gool | psiphon | tor | shard | custom
	CleanIP       string
	Proxy         *parser.ProxyConfig
	BypassIran    bool
	KillSwitch    bool
	TunDevice     string
	SocksPort     int
	HttpPort      int
	SystemVpnMode bool
}

// DefaultTunnelOptions creates default settings.
func DefaultTunnelOptions() TunnelOptions {
	return TunnelOptions{
		Protocol:      "pow",
		BypassIran:    true,
		KillSwitch:    false,
		TunDevice:     DefaultTunDevice,
		SocksPort:     10808,
		HttpPort:      10809,
		SystemVpnMode: true,
	}
}

// Manager orchestrates process spawning and Linux network routing.
type Manager struct {
	opts       TunnelOptions
	status     TunnelStatus
	activeCmd  *exec.Cmd
	tun2socks  *exec.Cmd
	ks         KillSwitch
	gwInfo     *DefaultGatewayInfo
	configFile string
	mu         sync.Mutex
	startTime  time.Time
	Message    string
	// last 20 lines of the aether core's output, for honest error reports.
	coreOutput []string
}

// NewManager creates a VPN session controller.
func NewManager(opts TunnelOptions) *Manager {
	if opts.Protocol == "" {
		opts.Protocol = "pow"
	}
	if opts.TunDevice == "" {
		opts.TunDevice = DefaultTunDevice
	}
	if opts.SocksPort == 0 {
		opts.SocksPort = 10808
	}
	if opts.HttpPort == 0 {
		opts.HttpPort = 10809
	}
	return &Manager{
		opts:   opts,
		status: StatusDisconnected,
	}
}

// Status returns current state and elapsed connection duration.
func (m *Manager) Status() (TunnelStatus, time.Duration) {
	m.mu.Lock()
	defer m.mu.Unlock()
	duration := time.Duration(0)
	if m.status == StatusConnected {
		duration = time.Since(m.startTime)
	}
	return m.status, duration
}

// FindBinary searches for an executable in standard local and system paths.
func FindBinary(name string) string {
	execPath, err := os.Executable()
	execDir := ""
	if err == nil {
		execDir = filepath.Dir(execPath)
	}

	candidates := []string{
		filepath.Join(execDir, "bin", name),
		filepath.Join(execDir, name),
		filepath.Join("linux/bin", name),
		filepath.Join("./bin", name),
		filepath.Join("./", name),
		filepath.Join("/usr/local/bin", name),
		filepath.Join("/usr/bin", name),
	}

	for _, c := range candidates {
		if fi, err := os.Stat(c); err == nil && !fi.IsDir() && (fi.Mode()&0111 != 0) {
			return c
		}
	}

	if p, err := exec.LookPath(name); err == nil {
		return p
	}

	return ""
}

// Connect initiates the VPN tunnel.
func (m *Manager) Connect() error {
	// The lock only guards STATUS transitions. The long network operations
	// below (registration, tunnel-up polls, privileged helper calls) run
	// UNLOCKED — holding it here would freeze Status(), which the UI polls
	// every 1.5s, and make the whole app appear hung while connecting.
	m.mu.Lock()
	if m.status == StatusConnected || m.status == StatusConnecting {
		m.mu.Unlock()
		return fmt.Errorf("VPN tunnel already active")
	}

	m.status = StatusConnecting
	m.mu.Unlock()

	// 1. Detect default gateway for routing
	gw, _ := DetectDefaultGateway()
	m.mu.Lock()
	m.gwInfo = gw
	m.mu.Unlock()

	// A disconnect racing this connect is honoured at every checkpoint below.
	checkCancelled := func() bool {
		m.mu.Lock()
		defer m.mu.Unlock()
		return m.status == StatusDisconnected
	}

	socksTargetPort := m.opts.SocksPort
	isAetherFamily := m.isAetherProtocol(m.opts.Protocol)

	if isAetherFamily {
		// Launch Aether CLI Core
		aetherBin := FindBinary("aether")
		if aetherBin == "" {
			m.status = StatusError
			return fmt.Errorf("aether binary not found. Please run: cd linux && make build")
		}

		socksTargetPort = 10822
		proto := strings.ToLower(m.opts.Protocol)
		if proto == "pow" || proto == "aether24" {
			proto = "wireguard"
		}

		tempDir := os.TempDir()
		idFile := filepath.Join(tempDir, "aether-identity.toml")

		args := []string{
			"--bind", fmt.Sprintf("127.0.0.1:%d", socksTargetPort),
			"--protocol", proto,
			"--turbo",
			"-4",
			"--config", idFile,
			"--log-level", "warning",
		}

		peer := m.opts.CleanIP
		if peer != "" {
			if !strings.Contains(peer, ":") {
				peer += ":2408"
			}
			if err := validatePeer(peer); err != nil {
				m.status = StatusError
				return fmt.Errorf("invalid clean IP: %w", err)
			}
			args = append(args, "--peer", peer)
		} else {
			args = append(args, "--peer", "162.159.192.1:2408")
		}

		cmd := exec.Command(aetherBin, args...)
		// Capture the core's output: when the tunnel never comes up (a failed
		// registration, a refused peer), the reason is in these lines and
		// nowhere else — without them the error report is empty.
		pipe, perr := cmd.StdoutPipe()
		if perr == nil {
			cmd.Stderr = cmd.Stdout
			go func() {
				sc := bufio.NewScanner(pipe)
				sc.Buffer(make([]byte, 16*1024), 64*1024)
				for sc.Scan() {
					line := strings.TrimSpace(sc.Text())
					if line == "" {
						continue
					}
					m.mu.Lock()
					m.coreOutput = append(m.coreOutput, line)
					if len(m.coreOutput) > 20 {
						m.coreOutput = m.coreOutput[len(m.coreOutput)-20:]
					}
					m.mu.Unlock()
				}
			}()
		}

		if err := cmd.Start(); err != nil {
			m.status = StatusError
			return fmt.Errorf("failed to start aether core: %w", err)
		}
		m.mu.Lock()
		m.activeCmd = cmd
		m.mu.Unlock()

	} else {
		// Launch Xray Core for VLESS / Trojan / Custom Proxy
		xrayBin := FindBinary("xray")
		if xrayBin == "" {
			m.status = StatusError
			return fmt.Errorf("xray binary not found. Please run: cd linux && make build")
		}

		cfgPath, err := m.writeXrayConfig()
		if err != nil {
			m.status = StatusError
			return fmt.Errorf("xray config generation failed: %w", err)
		}
		m.mu.Lock()
		m.configFile = cfgPath
		m.mu.Unlock()

		cmd := exec.Command(xrayBin, "run", "-c", cfgPath)
		if err := cmd.Start(); err != nil {
			m.status = StatusError
			return fmt.Errorf("failed to start xray process: %w", err)
		}
		m.mu.Lock()
		m.activeCmd = cmd
		m.mu.Unlock()
	}

	// Verify that the local proxy backend is actively responding. This result
	// is load-bearing: a dead SOCKS backend plus TUN routes equals a total
	// system blackout with a green CONNECTED UI — the worst failure shape.
	// The core's own last output lines are reported so the user sees WHY.
	// A fresh identity file means the core must REGISTER before the SOCKS
	// listener comes up — that takes tens of seconds on a filtered carrier.
	// Wait up to 45s while the process is alive; fail immediately if it dies.
	socksReady := false
	probeDeadline := time.Now().Add(45 * time.Second)
	for time.Now().Before(probeDeadline) {
		if probeSocks5Once(socksTargetPort) {
			socksReady = true
			break
		}
		m.mu.Lock()
		cmd := m.activeCmd
		m.mu.Unlock()
		if cmd == nil || cmd.Process == nil || cmd.Process.Signal(syscall.Signal(0)) != nil {
			break // the core itself exited — waiting is pointless
		}
		time.Sleep(500 * time.Millisecond)
	}
	if !socksReady {
		m.mu.Lock()
		tail := strings.Join(m.coreOutput, " | ")
		m.mu.Unlock()
		m.status = StatusError
		m.cleanupProcesses()
		if tail != "" {
			return fmt.Errorf("VPN core did not come up: %s", tail)
		}
		return fmt.Errorf("VPN core did not come up — registration may be blocked; try Psiphon/SHARD first")
	}

	// ── Wait until the tunnel PROVABLY carries traffic ──────────────────
	// A fresh identity file means the core must REGISTER first, which on a
	// filtered carrier can take tens of seconds. Routing system traffic into
	// the TUN before that moment is exactly the "CONNECTED but nothing works"
	// defect: every packet blackholes inside a half-born tunnel.
	m.mu.Lock()
	m.Message = "Establishing tunnel…"
	m.mu.Unlock()
	if !tunnelUpThroughSocks(socksTargetPort, 75*time.Second) {
		m.mu.Lock()
		cancelled := m.status == StatusDisconnected
		tail := strings.Join(m.coreOutput, " | ")
		m.mu.Unlock()
		if cancelled {
			m.cleanupProcesses()
			return nil
		}
		m.status = StatusError
		m.cleanupProcesses()
		if tail != "" {
			return fmt.Errorf("tunnel did not carry traffic: %s", tail)
		}
		return fmt.Errorf("tunnel did not carry traffic — registration may be blocked; try another protocol")
	}
	m.mu.Lock()
	m.Message = "Tunnel established — routing…"
	m.mu.Unlock()

	if checkCancelled() {
		m.cleanupProcesses()
		return nil
	}

	// Honour a disconnect that raced this connect.
	if checkCancelled() {
		m.cleanupProcesses()
		return nil
	}

	// 3. Configure Network Routing (TUN vs Desktop System Proxy)
	tunConfigured := false
	if m.opts.SystemVpnMode {
		if err := runHelperBounded(90*time.Second, "tun-up", m.opts.TunDevice, "10.0.0.2/24", userOwner()); err == nil {
			tun2socksBin := FindBinary("tun2socks")
			if tun2socksBin != "" {
				tunArgs := []string{
					"-device", m.opts.TunDevice,
					"-proxy", fmt.Sprintf("socks5://127.0.0.1:%d", socksTargetPort),
					"-loglevel", "warn",
				}
				tCmd := exec.Command(tun2socksBin, tunArgs...)
				if err := tCmd.Start(); err == nil {
					m.tun2socks = tCmd
					// Start() only proves the process spawned. Give it a
					// moment, then confirm it is STILL alive — a tun2socks
					// that died at TUN-attach turns the /1 routes into a
					// total system blackout while the UI shows CONNECTED.
					time.Sleep(900 * time.Millisecond)
					alive := tCmd.Process != nil &&
						tCmd.Process.Signal(syscall.Signal(0)) == nil
					tunConfigured = alive
				}
			}

			proxyIP := m.opts.CleanIP
			if proxyIP == "" && m.opts.Proxy != nil {
				proxyIP = m.opts.Proxy.Address
			}

			if tunConfigured {
				if err := runHelperBounded(120*time.Second, "route-up", m.opts.TunDevice, proxyIP, gwIP(m.gwInfo), gwDev(m.gwInfo)); err != nil {
					// Without the /1 routes there is no VPN at all — saying
					// TUN Mode here would be a lie. Tear the half-setup down
					// and fall through to the GNOME proxy fallback.
					tunConfigured = false
					TeardownTunInterface(m.opts.TunDevice)
					if m.tun2socks != nil && m.tun2socks.Process != nil {
						_ = m.tun2socks.Process.Kill()
						m.tun2socks = nil
					}
				}
			}

			if tunConfigured {
				if m.opts.BypassIran && m.gwInfo != nil {
					_, _ = ApplyIranBypassRoutes(m.gwInfo)
				}

				if m.opts.KillSwitch {
					_ = m.ks.Enable(proxyIP, m.opts.TunDevice)
				}

				// ── End-to-end proof ─────────────────────────────────────
				// The routes claim every packet enters asha0, but only a real
				// dial from THIS process (whose traffic follows the system
				// table) proves the whole chain works: TUN → tun2socks →
				// core → exit. If the proof fails, the honest outcome is to
				// tear the TUN down and fall back to the desktop proxy —
				// never a green CONNECTED over a blackholed system.
				verified := false
				for attempt := 0; attempt < 2 && !verified; attempt++ {
					conn, derr := net.DialTimeout("tcp", "1.1.1.1:443", 5*time.Second)
					if derr != nil {
						time.Sleep(1200 * time.Millisecond)
						continue
					}
					_ = conn.SetDeadline(time.Now().Add(5 * time.Second))
					tlsConn := tls.Client(conn, &tls.Config{InsecureSkipVerify: false, ServerName: "1.1.1.1"})
					herr := tlsConn.Handshake()
					_ = tlsConn.Close()
					if herr == nil {
						verified = true
					} else {
						time.Sleep(800 * time.Millisecond)
					}
				}

				if !verified {
					_ = runHelperBounded(30*time.Second, "route-down", m.opts.TunDevice, proxyIP)
					_ = runHelperBounded(30*time.Second, "tun-down", m.opts.TunDevice)
					if m.tun2socks != nil && m.tun2socks.Process != nil {
						_ = m.tun2socks.Process.Kill()
						m.tun2socks = nil
					}
					tunConfigured = false
					m.mu.Lock()
				m.Message = "TUN routing failed verification — fell back to Desktop System Proxy"
				m.mu.Unlock()
				}
			}
		}
	}

	// Graceful fallback: If TUN interface creation was not authorized, enable GNOME Desktop System Proxy!
	if !tunConfigured {
		_ = SetGnomeProxy(socksTargetPort, m.opts.HttpPort)
		m.opts.SystemVpnMode = false
		m.mu.Lock()
		m.Message = fmt.Sprintf("Connected (GNOME Desktop System Proxy on 127.0.0.1:%d)", socksTargetPort)
		m.mu.Unlock()
	} else {
		m.mu.Lock()
		m.Message = "Connected (Whole-Device TUN Mode)"
		m.mu.Unlock()
	}

	m.mu.Lock()
	m.status = StatusConnected
	m.startTime = time.Now()
	m.mu.Unlock()
	return nil
}

func (m *Manager) isAetherProtocol(p string) bool {
	switch strings.ToLower(p) {
	case "pow", "aether24", "masque", "wom", "wireguard", "gool":
		return true
	default:
		return false
	}
}

// validatePeer rejects endpoint strings that carry anything beyond a
// host:port pair. exec.Command passes arguments without a shell, but the
// validation keeps hostile values out of the core's config surface anyway.
func validatePeer(peer string) error {
	if peer == "" {
		return nil
	}
	if len(peer) > 253 {
		return fmt.Errorf("peer endpoint too long")
	}
	for _, r := range peer {
		ok := r == '.' || r == ':' || (r >= '0' && r <= '9') ||
			(r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || r == '-'
		if !ok {
			return fmt.Errorf("peer endpoint contains an invalid character %q", r)
		}
	}
	return nil
}

// gwIP/gwDev safely unwrap the detected default-gateway info.
func gwIP(gw *DefaultGatewayInfo) string {
	if gw == nil {
		return ""
	}
	return gw.Gateway
}

func gwDev(gw *DefaultGatewayInfo) string {
	if gw == nil {
		return ""
	}
	return gw.Device
}

// userOwner resolves the desktop user so the unprivileged UI process may
// attach to the TUN device.
func userOwner() string {
	user := os.Getenv("USER")
	if user == "" {
		user = os.Getenv("LOGNAME")
	}
	return user
}

// SetMessage records a progress/failure line shown by the status endpoint.
func (m *Manager) SetMessage(text string) {
	m.mu.Lock()
	m.Message = text
	m.mu.Unlock()
}

// runHelperBounded runs a privileged helper action with a hard deadline.
// A pkexec dialog nobody answers must delay the connect attempt — never hang
// it forever (Connect holds the status mutex the UI polls).
func runHelperBounded(timeout time.Duration, args ...string) error {
	done := make(chan error, 1)
	go func() { done <- executeHelper(args...) }()
	select {
	case err := <-done:
		return err
	case <-time.After(timeout):
		return fmt.Errorf("network helper timed out after %s", timeout)
	}
}

// Disconnect gracefully tears down the VPN tunnel.
func (m *Manager) Disconnect() {
	m.mu.Lock()
	defer m.mu.Unlock()

	if m.status == StatusDisconnected {
		return
	}

	if m.opts.SystemVpnMode {
		m.ks.Disable()
		if m.opts.BypassIran {
			RemoveIranBypassRoutes()
		}
		proxyIP := m.opts.CleanIP
		if proxyIP == "" && m.opts.Proxy != nil {
			proxyIP = m.opts.Proxy.Address
		}
		_ = runHelperBounded(30*time.Second, "route-down", m.opts.TunDevice, proxyIP)
		_ = runHelperBounded(30*time.Second, "tun-down", m.opts.TunDevice)
	}
	ResetGnomeProxy()

	m.cleanupProcesses()
	if m.configFile != "" {
		_ = os.Remove(m.configFile)
		m.configFile = ""
	}

	m.status = StatusDisconnected
}

func (m *Manager) cleanupProcesses() {
	if m.tun2socks != nil && m.tun2socks.Process != nil {
		_ = m.tun2socks.Process.Kill()
		m.tun2socks = nil
	}
	if m.activeCmd != nil && m.activeCmd.Process != nil {
		_ = m.activeCmd.Process.Kill()
		m.activeCmd = nil
	}
}

// writeXrayConfig creates an Xray JSON configuration.
func (m *Manager) writeXrayConfig() (string, error) {
	tempDir := os.TempDir()
	cfgFile := filepath.Join(tempDir, fmt.Sprintf("asha-xray-%d.json", time.Now().UnixNano()))

	targetIP := m.opts.CleanIP
	targetPort := 443
	var proxyCfg *parser.ProxyConfig
	if m.opts.Proxy != nil {
		proxyCfg = m.opts.Proxy
		if targetIP == "" {
			targetIP = proxyCfg.Address
		}
		targetPort = proxyCfg.Port
	}

	if targetIP == "" {
		targetIP = "104.16.1.1"
	}

	configMap := map[string]interface{}{
		"log": map[string]interface{}{
			"loglevel": "warning",
		},
		"inbounds": []map[string]interface{}{
			{
				"tag":      "socks-in",
				"port":     m.opts.SocksPort,
				"listen":   "127.0.0.1",
				"protocol": "socks",
				"settings": map[string]interface{}{
					"udp": true,
				},
			},
			{
				"tag":      "http-in",
				"port":     m.opts.HttpPort,
				"listen":   "127.0.0.1",
				"protocol": "http",
			},
		},
		"outbounds": []map[string]interface{}{
			{
				"tag":      "proxy",
				"protocol": "vless",
				"settings": map[string]interface{}{
					"vnext": []map[string]interface{}{
						{
							"address": targetIP,
							"port":    targetPort,
							"users": []map[string]interface{}{
								{
									"id":         "00000000-0000-0000-0000-000000000000",
									"encryption": "none",
								},
							},
						},
					},
				},
				"streamSettings": map[string]interface{}{
					"network":  "ws",
					"security": "tls",
					"tlsSettings": map[string]interface{}{
						"serverName": "speed.cloudflare.com",
					},
				},
			},
			{
				"tag":      "direct",
				"protocol": "freedom",
			},
		},
	}

	if proxyCfg != nil {
		outbound := configMap["outbounds"].([]map[string]interface{})[0]
		outbound["protocol"] = string(proxyCfg.Protocol)
		if proxyCfg.Protocol == parser.ProtocolVless {
			outbound["settings"] = map[string]interface{}{
				"vnext": []map[string]interface{}{
					{
						"address": targetIP,
						"port":    proxyCfg.Port,
						"users": []map[string]interface{}{
							{
								"id":         proxyCfg.UUID,
								"encryption": proxyCfg.Encryption,
								"flow":       proxyCfg.Flow,
							},
						},
					},
				},
			}
		} else if proxyCfg.Protocol == parser.ProtocolTrojan {
			outbound["settings"] = map[string]interface{}{
				"servers": []map[string]interface{}{
					{
						"address":  targetIP,
						"port":     proxyCfg.Port,
						"password": proxyCfg.Password,
					},
				},
			}
		}

		stream := map[string]interface{}{
			"network":  proxyCfg.Network,
			"security": proxyCfg.Security,
		}
		if proxyCfg.Security == "tls" {
			stream["tlsSettings"] = map[string]interface{}{
				"serverName":    proxyCfg.EffectiveSNI(),
				"allowInsecure": proxyCfg.AllowInsecure,
			}
		}
		if proxyCfg.Network == "ws" {
			stream["wsSettings"] = map[string]interface{}{
				"path": proxyCfg.Path,
				"headers": map[string]interface{}{
					"Host": proxyCfg.HostHeader,
				},
			}
		}
		outbound["streamSettings"] = stream
	}

	data, err := json.MarshalIndent(configMap, "", "  ")
	if err != nil {
		return "", err
	}

	if err := os.WriteFile(cfgFile, data, 0600); err != nil {
		return "", err
	}

	return cfgFile, nil
}

// probeSocks5Once performs a single SOCKS5 greeting attempt; true when the
// listener answered with a valid greeting.
func probeSocks5Once(port int) bool {
	conn, err := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", port), 700*time.Millisecond)
	if err != nil {
		return false
	}
	_, _ = conn.Write([]byte{0x05, 0x01, 0x00})
	resp := make([]byte, 2)
	_ = conn.SetReadDeadline(time.Now().Add(700 * time.Millisecond))
	ok := false
	if n, err := conn.Read(resp); err == nil && n == 2 && resp[0] == 0x05 && resp[1] == 0x00 {
		ok = true
	}
	conn.Close()
	return ok
}

// probeSocks5Ready checks if the local SOCKS5 proxy server is accepting connections.
func probeSocks5Ready(port int, timeout time.Duration) bool {
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		conn, err := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", port), 500*time.Millisecond)
		if err == nil {
			// SOCKS5 Greeting: [0x05, 0x01, 0x00]
			_, _ = conn.Write([]byte{0x05, 0x01, 0x00})
			resp := make([]byte, 2)
			_ = conn.SetReadDeadline(time.Now().Add(500 * time.Millisecond))
			if n, err := conn.Read(resp); err == nil && n == 2 && resp[0] == 0x05 && resp[1] == 0x00 {
				conn.Close()
				return true
			}
			conn.Close()
		}
		time.Sleep(200 * time.Millisecond)
	}
	return false
}

// tunnelUpThroughSocks proves the FULL chain works — SOCKS handshake, CONNECT
// to a public endpoint, and a TLS handshake through the tunnel — by dialing
// from THIS process. A fresh install must register its identity first, which
// on a filtered carrier takes tens of seconds; only after this proof is the
// system traffic routed into the TUN.
func tunnelUpThroughSocks(port int, deadline time.Duration) bool {
	hardDeadline := time.Now().Add(deadline)
	for time.Now().Before(hardDeadline) {
		if socksTunnelWorks(port) {
			return true
		}
		time.Sleep(1200 * time.Millisecond)
	}
	return false
}

// socksTunnelWorks performs a SOCKS5 CONNECT to 1.1.1.1:443 and completes a
// TLS handshake through it — one full round trip over the real exit.
func socksTunnelWorks(port int) bool {
	conn, err := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", port), 2*time.Second)
	if err != nil {
		return false
	}
	defer conn.Close()
	_ = conn.SetDeadline(time.Now().Add(6 * time.Second))

	// Greeting: VER=5, NMETHODS=1, METHOD=0 (no auth)
	if _, err := conn.Write([]byte{0x05, 0x01, 0x00}); err != nil {
		return false
	}
	greet := make([]byte, 2)
	if _, err := io.ReadFull(conn, greet); err != nil || greet[0] != 0x05 || greet[1] != 0x00 {
		return false
	}
	// CONNECT 1.1.1.1:443 (ATYP=IPv4)
	if _, err := conn.Write([]byte{0x05, 0x01, 0x00, 0x01, 1, 1, 1, 1, 0x01, 0xBB}); err != nil {
		return false
	}
	resp := make([]byte, 10)
	if _, err := io.ReadFull(conn, resp); err != nil || resp[1] != 0x00 {
		return false
	}
	// A TLS handshake through the tunnel proves the exit forwards traffic.
	tlsConn := tls.Client(conn, &tls.Config{ServerName: "cloudflare.com"})
	if err := tlsConn.Handshake(); err != nil {
		return false
	}
	_ = tlsConn.Close()
	return true
}
