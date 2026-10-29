package parser

import (
	"fmt"
	"net/url"
	"strconv"
	"strings"
)

// Protocol enum for proxy configurations
type Protocol string

const (
	ProtocolVless  Protocol = "vless"
	ProtocolTrojan Protocol = "trojan"
)

// ProxyConfig holds parsed VLESS or Trojan configuration parameters.
type ProxyConfig struct {
	Protocol      Protocol `json:"protocol"`
	Address       string   `json:"address"`
	Port          int      `json:"port"`
	UUID          string   `json:"uuid,omitempty"`
	Password      string   `json:"password,omitempty"`
	Encryption    string   `json:"encryption,omitempty"`
	Flow          string   `json:"flow,omitempty"`
	Security      string   `json:"security,omitempty"` // tls | reality | none
	SNI           string   `json:"sni,omitempty"`
	Fingerprint   string   `json:"fingerprint,omitempty"`
	ALPN          []string `json:"alpn,omitempty"`
	AllowInsecure bool     `json:"allowInsecure,omitempty"`
	Network       string   `json:"network,omitempty"` // tcp | ws | grpc | xhttp
	Path          string   `json:"path,omitempty"`
	HostHeader    string   `json:"hostHeader,omitempty"`
	ServiceName   string   `json:"serviceName,omitempty"`
	Mode          string   `json:"mode,omitempty"`
	PublicKey     string   `json:"publicKey,omitempty"`
	ShortID       string   `json:"shortId,omitempty"`
	Remark        string   `json:"remark,omitempty"`
	Raw           string   `json:"raw,omitempty"`
}

// EffectiveSNI returns the SNI or Host header or original address.
func (p *ProxyConfig) EffectiveSNI() string {
	if p.SNI != "" {
		return p.SNI
	}
	if p.HostHeader != "" {
		return p.HostHeader
	}
	return p.Address
}

// Parse extracts configuration details from a VLESS or Trojan link.
func Parse(raw string) (*ProxyConfig, error) {
	trimmed := strings.TrimSpace(raw)
	u, err := url.Parse(trimmed)
	if err != nil {
		return nil, fmt.Errorf("invalid URL: %w", err)
	}

	scheme := strings.ToLower(u.Scheme)
	if scheme != string(ProtocolVless) && scheme != string(ProtocolTrojan) {
		return nil, fmt.Errorf("unsupported protocol scheme: %s (only vless and trojan supported)", scheme)
	}

	port := 443
	host := u.Hostname()
	if pStr := u.Port(); pStr != "" {
		if pVal, err := strconv.Atoi(pStr); err == nil && pVal > 0 {
			port = pVal
		}
	}

	q := u.Query()
	cfg := &ProxyConfig{
		Protocol:   Protocol(scheme),
		Address:    host,
		Port:       port,
		Remark:     u.Fragment,
		Raw:        trimmed,
		Security:   q.Get("security"),
		SNI:        q.Get("sni"),
		Flow:       q.Get("flow"),
		Network:    q.Get("type"),
		Encryption: q.Get("encryption"),
	}

	if cfg.Network == "" {
		cfg.Network = "tcp"
	}

	if scheme == string(ProtocolVless) {
		cfg.UUID = u.User.Username()
		if cfg.Encryption == "" {
			cfg.Encryption = "none"
		}
	} else if scheme == string(ProtocolTrojan) {
		if u.User != nil {
			cfg.Password = u.User.Username()
		}
	}

	cfg.Fingerprint = q.Get("fp")
	if alpn := q.Get("alpn"); alpn != "" {
		cfg.ALPN = strings.Split(alpn, ",")
	}
	if q.Get("allowInsecure") == "1" || strings.ToLower(q.Get("allowInsecure")) == "true" {
		cfg.AllowInsecure = true
	}

	cfg.Path = q.Get("path")
	if cfg.Path == "" {
		cfg.Path = "/"
	}
	cfg.HostHeader = q.Get("host")
	cfg.ServiceName = q.Get("serviceName")
	cfg.Mode = q.Get("mode")
	cfg.PublicKey = q.Get("pbk")
	cfg.ShortID = q.Get("sid")

	return cfg, nil
}

// BuildWithCleanIP produces a new link replacing the host address with cleanIP,
// ensuring the original server name is preserved in the SNI / host header.
func (p *ProxyConfig) BuildWithCleanIP(cleanIP string) string {
	origHost := p.Address
	effectiveSNI := p.EffectiveSNI()

	userPart := p.UUID
	if p.Protocol == ProtocolTrojan {
		userPart = p.Password
	}

	q := make(url.Values)
	if p.Security != "" {
		q.Set("security", p.Security)
	}
	if effectiveSNI != "" {
		q.Set("sni", effectiveSNI)
	}
	if p.Network != "" && p.Network != "tcp" {
		q.Set("type", p.Network)
	}
	if p.Flow != "" {
		q.Set("flow", p.Flow)
	}
	if p.Encryption != "" && p.Encryption != "none" {
		q.Set("encryption", p.Encryption)
	}
	if p.Fingerprint != "" {
		q.Set("fp", p.Fingerprint)
	}
	if len(p.ALPN) > 0 {
		q.Set("alpn", strings.Join(p.ALPN, ","))
	}
	if p.Path != "" && p.Path != "/" {
		q.Set("path", p.Path)
	}
	if p.HostHeader != "" {
		q.Set("host", p.HostHeader)
	} else if origHost != "" && origHost != cleanIP {
		q.Set("host", origHost)
	}
	if p.PublicKey != "" {
		q.Set("pbk", p.PublicKey)
	}
	if p.ShortID != "" {
		q.Set("sid", p.ShortID)
	}
	if p.ServiceName != "" {
		q.Set("serviceName", p.ServiceName)
	}

	targetHost := cleanIP
	if strings.Contains(cleanIP, ":") && !strings.HasPrefix(cleanIP, "[") {
		targetHost = "[" + cleanIP + "]"
	}

	u := url.URL{
		Scheme:   string(p.Protocol),
		User:     url.User(userPart),
		Host:     fmt.Sprintf("%s:%d", targetHost, p.Port),
		RawQuery: q.Encode(),
		Fragment: fmt.Sprintf("%s [Asha Clean IP]", p.Remark),
	}

	return u.String()
}
