package vpn

import (
	"fmt"
	"os"
	"os/exec"
)

const DefaultTunDevice = "asha0"

// executeHelper executes asha-net-helper with privilege elevation if needed.
func executeHelper(args ...string) error {
	helperBin := FindBinary("asha-net-helper")
	if helperBin == "" {
		helperBin = "/usr/local/bin/asha-net-helper"
	}

	// 1. Direct execution (if running as root or already privileged)
	if os.Geteuid() == 0 {
		cmd := exec.Command(helperBin, args...)
		if out, err := cmd.CombinedOutput(); err != nil {
			return fmt.Errorf("%s failed: %w (out: %s)", args[0], err, string(out))
		}
		return nil
	}

	// 2. Try passwordless sudo (succeeds instantly if sudoers rule is installed)
	if sudoPath, err := exec.LookPath("sudo"); err == nil {
		sudoArgs := append([]string{"-n", helperBin}, args...)
		cmd := exec.Command(sudoPath, sudoArgs...)
		if _, err := cmd.CombinedOutput(); err == nil {
			return nil
		}
	}

	// 3. Try pkexec with GUI polkit agent
	if pkexecPath, err := exec.LookPath("pkexec"); err == nil {
		pkArgs := append([]string{helperBin}, args...)
		cmd := exec.Command(pkexecPath, pkArgs...)
		cmd.Env = os.Environ()
		if _, err := cmd.CombinedOutput(); err == nil {
			return nil
		}
	}

	return fmt.Errorf("root privileges required for TUN routing")
}

// SetupTunInterface creates and brings up the TUN interface in Linux.
func SetupTunInterface(dev string, ipAddr string) error {
	user := os.Getenv("USER")
	if user == "" {
		user = os.Getenv("LOGNAME")
	}
	return executeHelper("tun-up", dev, ipAddr, user)
}

// TeardownTunInterface brings down and deletes the TUN interface.
func TeardownTunInterface(dev string) {
	_ = executeHelper("tun-down", dev)
}

// RedirectDefaultTraffic adds two /1 routes so all internet traffic routes through the TUN interface.
func RedirectDefaultTraffic(dev string, proxyServerIP string, gw *DefaultGatewayInfo) error {
	gwIp := ""
	gwDev := ""
	if gw != nil {
		gwIp = gw.Gateway
		gwDev = gw.Device
	}
	return executeHelper("route-up", dev, proxyServerIP, gwIp, gwDev)
}

// RestoreDefaultTraffic cleans up the /1 routes.
func RestoreDefaultTraffic(dev string, proxyServerIP string) {
	_ = executeHelper("route-down", dev, proxyServerIP)
}
