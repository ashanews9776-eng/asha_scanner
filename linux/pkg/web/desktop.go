package web

import (
	"fmt"
	"net"
	"os"
	"os/exec"
	"time"
)

// FindFreePort returns an available local TCP port.
func FindFreePort() int {
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return 8080
	}
	defer l.Close()
	return l.Addr().(*net.TCPAddr).Port
}

// LaunchDesktopWindow starts the web server and opens a dedicated native window.
func LaunchDesktopWindow(port int) error {
	url := fmt.Sprintf("http://127.0.0.1:%d", port)
	srv := NewServer(port)

	// Start web server in background
	go func() {
		if err := srv.Start(); err != nil {
			fmt.Printf("[SERVER ERROR] %v\n", err)
		}
	}()

	// Wait 200ms for server socket to bind
	time.Sleep(200 * time.Millisecond)

	// Candidate window browsers in priority order
	browserBin := ""
	var args []string

	if path, err := exec.LookPath("chromium"); err == nil {
		browserBin = path
		args = []string{
			fmt.Sprintf("--app=%s", url),
			"--window-size=1080,780",
			"--class=asha-scanner",
			"--user-data-dir=" + os.TempDir() + "/asha-scanner-profile",
		}
	} else if path, err := exec.LookPath("brave"); err == nil {
		browserBin = path
		args = []string{
			fmt.Sprintf("--app=%s", url),
			"--window-size=1080,780",
			"--class=asha-scanner",
			"--user-data-dir=" + os.TempDir() + "/asha-scanner-profile",
		}
	} else if path, err := exec.LookPath("epiphany"); err == nil {
		browserBin = path
		args = []string{fmt.Sprintf("--application-mode=%s", url)}
	} else if path, err := exec.LookPath("firefox"); err == nil {
		browserBin = path
		args = []string{"--new-window", url}
	} else if path, err := exec.LookPath("xdg-open"); err == nil {
		browserBin = path
		args = []string{url}
	}

	if browserBin == "" {
		return fmt.Errorf("no suitable browser found. Please open %s manually", url)
	}

	// Chromium and Brave require --no-sandbox when running with root/sudo privileges
	if os.Geteuid() == 0 {
		args = append(args, "--no-sandbox")
	}

	fmt.Printf(" [DESKTOP] Launching native window via %s...\n", browserBin)
	cmd := exec.Command(browserBin, args...)
	return cmd.Run()
}
