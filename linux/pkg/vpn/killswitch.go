package vpn

// KillSwitch manages iptables firewall leak prevention.
type KillSwitch struct {
	enabled bool
}

// Enable activates strict drop rules on the physical interface.
func (ks *KillSwitch) Enable(proxyServerIP string, tunDev string) error {
	err := executeHelper("killswitch-on", proxyServerIP, tunDev)
	if err != nil {
		return err
	}
	ks.enabled = true
	return nil
}

// Disable restores standard iptables rules.
func (ks *KillSwitch) Disable() {
	if !ks.enabled {
		return
	}
	_ = executeHelper("killswitch-off")
	ks.enabled = false
}
