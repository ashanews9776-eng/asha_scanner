package vpn

import (
	"fmt"
	"os/exec"
)

// SetGnomeProxy configures GNOME desktop system-wide proxy settings.
func SetGnomeProxy(socksPort int, httpPort int) error {
	if _, err := exec.LookPath("gsettings"); err != nil {
		return err // Not a GNOME / gsettings environment
	}

	_ = exec.Command("gsettings", "set", "org.gnome.system.proxy.socks", "host", "127.0.0.1").Run()
	_ = exec.Command("gsettings", "set", "org.gnome.system.proxy.socks", "port", fmt.Sprintf("%d", socksPort)).Run()

	if httpPort > 0 {
		_ = exec.Command("gsettings", "set", "org.gnome.system.proxy.http", "host", "127.0.0.1").Run()
		_ = exec.Command("gsettings", "set", "org.gnome.system.proxy.http", "port", fmt.Sprintf("%d", httpPort)).Run()
		_ = exec.Command("gsettings", "set", "org.gnome.system.proxy.https", "host", "127.0.0.1").Run()
		_ = exec.Command("gsettings", "set", "org.gnome.system.proxy.https", "port", fmt.Sprintf("%d", httpPort)).Run()
	}

	return exec.Command("gsettings", "set", "org.gnome.system.proxy", "mode", "manual").Run()
}

// ResetGnomeProxy disables the GNOME desktop proxy.
func ResetGnomeProxy() {
	if _, err := exec.LookPath("gsettings"); err != nil {
		return
	}
	_ = exec.Command("gsettings", "set", "org.gnome.system.proxy", "mode", "none").Run()
}
