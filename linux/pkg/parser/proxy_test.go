package parser

import (
	"testing"
)

func TestParseVless(t *testing.T) {
	link := "vless://11111111-2222-3333-4444-555555555555@example.com:443?encryption=none&security=tls&sni=test.com&type=ws&path=%2Fws&host=h.com&fp=chrome#My%20Node"
	cfg, err := Parse(link)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if cfg.Protocol != ProtocolVless {
		t.Errorf("expected vless, got %s", cfg.Protocol)
	}
	if cfg.UUID != "11111111-2222-3333-4444-555555555555" {
		t.Errorf("uuid mismatch: %s", cfg.UUID)
	}
	if cfg.Address != "example.com" {
		t.Errorf("address mismatch: %s", cfg.Address)
	}
	if cfg.Port != 443 {
		t.Errorf("port mismatch: %d", cfg.Port)
	}
	if cfg.Security != "tls" {
		t.Errorf("security mismatch: %s", cfg.Security)
	}
	if cfg.SNI != "test.com" {
		t.Errorf("sni mismatch: %s", cfg.SNI)
	}
	if cfg.Network != "ws" {
		t.Errorf("network mismatch: %s", cfg.Network)
	}

	cleanLink := cfg.BuildWithCleanIP("104.16.1.1")
	if !contains(cleanLink, "@104.16.1.1:443") {
		t.Errorf("expected clean IP in link, got: %s", cleanLink)
	}
	if !contains(cleanLink, "sni=test.com") {
		t.Errorf("expected preserved SNI in link, got: %s", cleanLink)
	}
}

func TestParseTrojan(t *testing.T) {
	link := "trojan://secret-pass@1.2.3.4:8443?security=tls&sni=s.com#TrojanNode"
	cfg, err := Parse(link)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if cfg.Protocol != ProtocolTrojan {
		t.Errorf("expected trojan, got %s", cfg.Protocol)
	}
	if cfg.Password != "secret-pass" {
		t.Errorf("password mismatch: %s", cfg.Password)
	}
	if cfg.Port != 8443 {
		t.Errorf("port mismatch: %d", cfg.Port)
	}

	cleanLink := cfg.BuildWithCleanIP("104.16.2.2")
	if !contains(cleanLink, "@104.16.2.2:8443") {
		t.Errorf("expected clean IP in trojan link, got: %s", cleanLink)
	}
}

func contains(s, substr string) bool {
	return len(s) >= len(substr) && (s == substr || len(s) > 0 && len(substr) > 0 && stringContains(s, substr))
}

func stringContains(s, substr string) bool {
	for i := 0; i+len(substr) <= len(s); i++ {
		if s[i:i+len(substr)] == substr {
			return true
		}
	}
	return false
}
