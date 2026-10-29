package tui

import (
	"fmt"
	"strings"
	"time"

	"github.com/ashanews9776-eng/asha_scanner/linux/pkg/scanner"
)

// ANSI Color constants
const (
	Reset       = "\033[0m"
	Bold        = "\033[1m"
	Dim         = "\033[2m"
	Cyan        = "\033[36m"
	CyanBright  = "\033[96;1m"
	Green       = "\033[32m"
	GreenBright = "\033[92;1m"
	Yellow      = "\033[33m"
	Red         = "\033[31m"
	Magenta     = "\033[35m"
	Gray        = "\033[90m"
	White       = "\033[97m"
)

// PrintBanner outputs the Cyberpunk Asha Scanner ASCII header.
func PrintBanner() {
	banner := `
` + CyanBright + `   ___  ______ _____  _    _ _____ _____  ___   _   _ _   _ ___________ 
  / _ \ | ___ /  ___|| |  | /  ___/  __ \/ _ \ | \ | | \ | |  ___| ___ \
 / /_\ \| |_/ \ '--. | |  | \ '--.| /  \/ /_\ \|  \| |  \| | |__ | |_/ /
 |  _  || ___ \'--. \| |/\| |'--. \ |   |  _  || . ' | . ' |  __||    / 
 | | | || |_/ /\__/ /\  /\  /\__/ / \__/\ | | || |\  | |\  | |___| |\ \ 
 \_| |_/\____/\____/  \/  \/ \____/ \____/\_| |_/\_| \_/\_| \_/\____/\_| \_|` + Reset + `
` + Gray + ` ─────────────────────────────────────────────────────────────────────────────` + Reset + `
` + GreenBright + ` 🛡️  ASHA SCANNER — HIGH PERFORMANCE CLOUDFLARE IP SCANNER & VPN (LINUX)` + Reset + `
` + Gray + ` ─────────────────────────────────────────────────────────────────────────────` + Reset + `
`
	fmt.Print(banner)
}

// PrintProgress renders an inline real-time progress update.
func PrintProgress(p scanner.ScanProgress) {
	pct := 0
	if p.Total > 0 {
		pct = (p.Tested * 100) / p.Total
	}

	barWidth := 24
	filled := (pct * barWidth) / 100
	if filled > barWidth {
		filled = barWidth
	}
	bar := strings.Repeat("█", filled) + strings.Repeat("░", barWidth-filled)

	phaseColor := CyanBright
	switch p.Phase {
	case scanner.PhaseProbing:
		phaseColor = CyanBright
	case scanner.PhaseResolving:
		phaseColor = Yellow
	case scanner.PhaseStability:
		phaseColor = Magenta
	case scanner.PhaseValidating:
		phaseColor = GreenBright
	case scanner.PhaseDone:
		phaseColor = Green
	}

	elapsedSec := float64(p.ElapsedMs) / 1000.0

	fmt.Printf("\r %s%-10s%s [%s%s%s] %3d%% | Probed: %s%d/%d%s | Found: %s%d%s | Time: %s%.1fs%s  ",
		phaseColor, p.Phase, Reset,
		Cyan, bar, Reset,
		pct,
		White, p.Tested, p.Total, Reset,
		GreenBright, p.Found, Reset,
		Gray, elapsedSec, Reset,
	)
}

// PrintResultsTable prints a formatted cyberpunk table of scan results.
func PrintResultsTable(results []scanner.ScanResult) {
	fmt.Println()
	fmt.Println(Gray + " ┌────┬─────────────────┬──────┬──────────┬──────────┬────────┬────────┬──────────┬──────┬────────┐" + Reset)
	fmt.Println(Gray + " │ " + CyanBright + "#" + Gray + "  │ " + CyanBright + "IP ADDRESS" + Gray + "      │ " + CyanBright + "PORT" + Gray + " │ " + CyanBright + "AVG (MS)" + Gray + " │ " + CyanBright + "MIN (MS)" + Gray + " │ " + CyanBright + "JITTER" + Gray + " │ " + CyanBright + "LOSS %" + Gray + " │ " + CyanBright + "SPEED" + Gray + "    │ " + CyanBright + "COLO" + Gray + " │ " + CyanBright + "STATUS" + Gray + " │" + Reset)
	fmt.Println(Gray + " ├────┼─────────────────┼──────┼──────────┼──────────┼────────┼────────┼──────────┼──────┼────────┤" + Reset)

	for i, r := range results {
		avg := r.AvgLatencyMs()
		avgColor := GreenBright
		if avg > 150 && avg <= 300 {
			avgColor = Yellow
		} else if avg > 300 {
			avgColor = Red
		}

		speedStr := "—"
		if r.SpeedTested {
			speedStr = fmt.Sprintf("%.2f Mbps", r.ThroughputMbps())
		}

		coloStr := r.Colo
		if coloStr == "" {
			coloStr = "—"
		}

		lossPct := r.Loss() * 100
		lossColor := Green
		if lossPct > 25 {
			lossColor = Red
		} else if lossPct > 0 {
			lossColor = Yellow
		}

		fmt.Printf(Gray+" │ "+White+"%-2d"+Gray+" │ "+Cyan+"%-15s"+Gray+" │ "+White+"%-4d"+Gray+" │ "+avgColor+"%8.1f"+Gray+" │ "+White+"%8d"+Gray+" │ "+White+"%6d"+Gray+" │ "+lossColor+"%5.1f%%"+Gray+" │ "+GreenBright+"%-8s"+Gray+" │ "+Yellow+"%-4s"+Gray+" │ "+GreenBright+"HEALTHY"+Gray+"│\n"+Reset,
			i+1, r.IP, r.Port, avg, r.MinLatencyMs(), r.JitterMs(), lossPct, speedStr, coloStr)
	}

	fmt.Println(Gray + " └────┴─────────────────┴──────┴──────────┴──────────┴────────┴────────┴──────────┴──────┴────────┘" + Reset)
	fmt.Println()
}

// PrintLogLine prints an engine diagnostic log.
func PrintLogLine(msg string) {
	t := time.Now().Format("15:04:05")
	fmt.Printf(" %s[%s]%s %s\n", Gray, t, Reset, msg)
}
