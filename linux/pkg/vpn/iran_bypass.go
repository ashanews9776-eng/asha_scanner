package vpn

import (
	_ "embed"
	"fmt"
	"os"
	"os/exec"
	"strings"
)

//go:embed data/geoip-iran.cidr
var embeddedIranCIDRs string

// DefaultGatewayInfo holds the physical network interface and gateway.
type DefaultGatewayInfo struct {
	Gateway string
	Device  string
}

// DetectDefaultGateway discovers the main physical network route.
func DetectDefaultGateway() (*DefaultGatewayInfo, error) {
	out, err := exec.Command("ip", "route", "show", "default").Output()
	if err != nil {
		return nil, fmt.Errorf("failed to query default route: %w", err)
	}

	// Example output: "default via 192.168.1.1 dev wlan0 proto dhcp metric 600"
	fields := strings.Fields(string(out))
	var gw, dev string
	for i := 0; i < len(fields); i++ {
		if fields[i] == "via" && i+1 < len(fields) {
			gw = fields[i+1]
		}
		if fields[i] == "dev" && i+1 < len(fields) {
			dev = fields[i+1]
		}
	}

	if gw == "" || dev == "" {
		return nil, fmt.Errorf("could not parse default gateway from: %s", string(out))
	}

	return &DefaultGatewayInfo{
		Gateway: gw,
		Device:  dev,
	}, nil
}

// ApplyIranBypassRoutes adds explicit routes for Iranian CIDRs via the physical gateway.
func ApplyIranBypassRoutes(gw *DefaultGatewayInfo) (int, error) {
	if gw == nil {
		return 0, fmt.Errorf("gateway info is nil")
	}

	tmpFile := "/tmp/asha-iran-cidr.txt"
	_ = os.WriteFile(tmpFile, []byte(embeddedIranCIDRs), 0644)

	err := executeHelper("iran-bypass-up", gw.Gateway, gw.Device, tmpFile)
	if err != nil {
		return 0, err
	}

	return len(strings.Split(embeddedIranCIDRs, "\n")), nil
}

// RemoveIranBypassRoutes removes all injected Iranian CIDR bypass routes.
func RemoveIranBypassRoutes() int {
	tmpFile := "/tmp/asha-iran-cidr.txt"
	_ = executeHelper("iran-bypass-down", tmpFile)
	_ = os.Remove(tmpFile)
	return 0
}
