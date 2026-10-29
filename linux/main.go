package main

import (
	"bufio"
	"context"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"strconv"
	"strings"
	"syscall"
	"time"

	"github.com/ashanews9776-eng/asha_scanner/linux/pkg/parser"
	"github.com/ashanews9776-eng/asha_scanner/linux/pkg/scanner"
	"github.com/ashanews9776-eng/asha_scanner/linux/pkg/tui"
	"github.com/ashanews9776-eng/asha_scanner/linux/pkg/vpn"
	"github.com/ashanews9776-eng/asha_scanner/linux/pkg/web"
)

func main() {
	if len(os.Args) > 1 {
		handleCLI()
		return
	}

	// Check if stdin is a terminal. If launched from desktop menu (e.g. .desktop launcher), launch GUI directly
	fileInfo, err := os.Stdin.Stat()
	if err == nil && (fileInfo.Mode()&os.ModeCharDevice) == 0 {
		port := web.FindFreePort()
		_ = web.LaunchDesktopWindow(port)
		return
	}

	// Default: Interactive Cyberpunk TUI
	runInteractiveTUI()
}

func handleCLI() {
	cmd := os.Args[1]

	switch cmd {
	case "gui":
		fs := flag.NewFlagSet("gui", flag.ExitOnError)
		port := fs.Int("port", 0, "Custom port for desktop GUI server")
		_ = fs.Parse(os.Args[2:])

		tui.PrintBanner()
		p := *port
		if p == 0 {
			p = web.FindFreePort()
		}
		fmt.Printf(" [GUI] Launching native Cyberpunk Desktop window on port %d...\n", p)
		if err := web.LaunchDesktopWindow(p); err != nil {
			fmt.Printf("[ERROR] %v\n", err)
		}

	case "scan":
		fs := flag.NewFlagSet("scan", flag.ExitOnError)
		count := fs.Int("count", 500, "Number of IPs to test")
		workers := fs.Int("workers", 50, "Concurrency worker threads")
		mode := fs.String("mode", "http", "Probe mode: http, tls, tcp")
		portsStr := fs.String("ports", "443", "Comma-separated ports (e.g. 443,8443)")
		speedTest := fs.Bool("speedtest", true, "Perform download speed test on top results")
		gentle := fs.Bool("gentle", false, "Gentle mode: max 25 workers, 40 pps rate limit")
		proxyLink := fs.String("proxy", "", "Optional VLESS / Trojan proxy link")
		format := fs.String("out", "table", "Output format: table, csv, json, ips")
		outFile := fs.String("file", "", "Save results to destination file")

		_ = fs.Parse(os.Args[2:])

		tui.PrintBanner()

		cfg := scanner.DefaultScanConfig()
		cfg.Count = *count
		cfg.Concurrency = *workers
		cfg.Mode = scanner.ProbeMode(*mode)
		cfg.SpeedTest = *speedTest
		cfg.Gentle = *gentle

		var ports []int
		for _, pStr := range strings.Split(*portsStr, ",") {
			if p, err := strconv.Atoi(strings.TrimSpace(pStr)); err == nil {
				ports = append(ports, p)
			}
		}
		if len(ports) > 0 {
			cfg.Ports = ports
		}

		if *proxyLink != "" {
			p, err := parser.Parse(*proxyLink)
			if err != nil {
				fmt.Printf("[ERROR] Failed to parse proxy link: %v\n", err)
				os.Exit(1)
			}
			cfg.Proxy = p
			fmt.Printf(" [INFO] Loaded proxy: %s (%s)\n", p.Remark, p.Protocol)
		}

		ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
		defer cancel()

		engine := scanner.NewEngine(cfg)
		results, err := engine.Scan(ctx, func(progress scanner.ScanProgress) {
			tui.PrintProgress(progress)
		})
		fmt.Println()

		if err != nil {
			fmt.Printf("\n[ERROR] Scan error: %v\n", err)
			os.Exit(1)
		}

		if len(results) == 0 {
			fmt.Println(" [INFO] No reachable Cloudflare edges found.")
			return
		}

		var outputText string
		switch *format {
		case "csv":
			outputText = scanner.ExportCSV(results)
		case "json":
			outputText, _ = scanner.ExportJSON(results)
		case "ips":
			outputText = scanner.ExportIPs(results)
		default:
			tui.PrintResultsTable(results)
			if cfg.Proxy != nil {
				fmt.Println(tui.CyanBright + " [VLESS/TROJAN READY CONFIGS]:" + tui.Reset)
				fmt.Print(scanner.ExportConfigs(results[:3], cfg.Proxy))
			}
		}

		if outputText != "" {
			if *outFile != "" {
				_ = os.WriteFile(*outFile, []byte(outputText), 0644)
				fmt.Printf(" [INFO] Saved results to: %s\n", *outFile)
			} else {
				fmt.Print(outputText)
			}
		}

	case "vpn":
		fs := flag.NewFlagSet("vpn", flag.ExitOnError)
		cleanIP := fs.String("clean-ip", "", "Clean Cloudflare IP to use")
		proxyLink := fs.String("proxy", "", "VLESS / Trojan proxy configuration link")
		bypassIran := fs.Bool("bypass-iran", true, "Route Iranian websites outside the VPN tunnel")
		killSwitch := fs.Bool("killswitch", false, "Enable firewall killswitch")

		_ = fs.Parse(os.Args[2:])

		tui.PrintBanner()

		opts := vpn.DefaultTunnelOptions()
		opts.CleanIP = *cleanIP
		opts.BypassIran = *bypassIran
		opts.KillSwitch = *killSwitch

		if *proxyLink != "" {
			p, err := parser.Parse(*proxyLink)
			if err != nil {
				fmt.Printf("[ERROR] Invalid proxy link: %v\n", err)
				os.Exit(1)
			}
			opts.Proxy = p
		}

		mgr := vpn.NewManager(opts)
		fmt.Println(" [VPN] Establishing system VPN tunnel...")
		if err := mgr.Connect(); err != nil {
			fmt.Printf(" [ERROR] Failed to connect: %v\n", err)
			os.Exit(1)
		}

		fmt.Println(tui.GreenBright + " [SUCCESS] VPN Connected! All system traffic routed through tunnel." + tui.Reset)
		fmt.Println(" Press Ctrl+C to disconnect...")

		sigChan := make(chan os.Signal, 1)
		signal.Notify(sigChan, os.Interrupt, syscall.SIGTERM)
		<-sigChan

		fmt.Println("\n [VPN] Tearing down routes and restoring network...")
		mgr.Disconnect()
		fmt.Println(" [VPN] Disconnected.")

	case "serve":
		fs := flag.NewFlagSet("serve", flag.ExitOnError)
		port := fs.Int("port", 8080, "Web dashboard port")
		_ = fs.Parse(os.Args[2:])

		tui.PrintBanner()
		srv := web.NewServer(*port)
		if err := srv.Start(); err != nil {
			fmt.Printf("[ERROR] Server error: %v\n", err)
		}

	default:
		printHelp()
	}
}

func printHelp() {
	tui.PrintBanner()
	fmt.Println(` Usage:
   asha-linux [command] [flags]

 Commands:
   gui      Launch native Cyberpunk Desktop GUI window (matches Android look & feel)
   scan     Execute Cloudflare IP scan
            Flags: -count 500 -workers 50 -mode http -speedtest -proxy "vless://..." -out table|csv|json
   vpn      Connect system VPN using a clean IP and/or proxy config
            Flags: -clean-ip 104.16.x.x -proxy "vless://..." -bypass-iran -killswitch
   serve    Launch local Cyberpunk Web Dashboard
            Flags: -port 8080

 Run without arguments to launch the interactive terminal menu.`)
}

func runInteractiveTUI() {
	reader := bufio.NewReader(os.Stdin)

	for {
		tui.PrintBanner()
		fmt.Println(tui.CyanBright + " SELECT AN ACTION:" + tui.Reset)
		fmt.Println("  " + tui.GreenBright + "[1]" + tui.Reset + " 🖥️  Launch Cyberpunk Desktop GUI Window (پنجره گرافیکی مشابه اندروید)")
		fmt.Println("  " + tui.GreenBright + "[2]" + tui.Reset + " ⚡ Quick Scan (500 random IPs, Balanced, Speed Test)")
		fmt.Println("  " + tui.GreenBright + "[3]" + tui.Reset + " 🎛️  Custom Scan (Customize count, workers, ports, and mode)")
		fmt.Println("  " + tui.GreenBright + "[4]" + tui.Reset + " 📋 Test IP List (Import and validate specific IPs)")
		fmt.Println("  " + tui.GreenBright + "[5]" + tui.Reset + " 🌍 Discover Cloudflare Data Centers (Colo discovery)")
		fmt.Println("  " + tui.GreenBright + "[6]" + tui.Reset + " 🛡️  Connect System VPN (with Clean IP & Iran site bypass)")
		fmt.Println("  " + tui.GreenBright + "[7]" + tui.Reset + " 🌐 Launch Web Dashboard on http://127.0.0.1:8080")
		fmt.Println("  " + tui.Red + "[0]" + tui.Reset + " ❌ Exit")
		fmt.Println()
		fmt.Print(tui.Cyan + " Enter choice [0-7]: " + tui.Reset)

		choice, _ := reader.ReadString('\n')
		choice = strings.TrimSpace(choice)

		switch choice {
		case "1":
			port := web.FindFreePort()
			fmt.Printf("\n Launching Desktop GUI on port %d...\n", port)
			_ = web.LaunchDesktopWindow(port)
		case "2":
			runQuickScan()
		case "3":
			runCustomScan(reader)
		case "4":
			runTestListScan(reader)
		case "5":
			runColoDiscovery()
		case "6":
			runVpnConnect(reader)
		case "7":
			srv := web.NewServer(8080)
			fmt.Println(" Press Ctrl+C in terminal to stop web server.")
			_ = srv.Start()
		case "0", "q", "exit":
			fmt.Println("\n Exiting Asha Scanner. Stay secure!")
			return
		default:
			fmt.Println(" Invalid selection, please try again.")
			time.Sleep(1 * time.Second)
		}

		fmt.Println("\n Press Enter to return to main menu...")
		_, _ = reader.ReadString('\n')
	}
}

func runQuickScan() {
	fmt.Println("\n" + tui.CyanBright + " [RADAR] Starting Quick Scan..." + tui.Reset)
	cfg := scanner.DefaultScanConfig()
	cfg.Count = 500
	cfg.Concurrency = 50

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	engine := scanner.NewEngine(cfg)
	results, _ := engine.Scan(ctx, func(progress scanner.ScanProgress) {
		tui.PrintProgress(progress)
	})
	fmt.Println()

	if len(results) > 0 {
		tui.PrintResultsTable(results)
	} else {
		fmt.Println(" No healthy IPs discovered.")
	}
}

func runCustomScan(reader *bufio.Reader) {
	cfg := scanner.DefaultScanConfig()

	fmt.Print("\n Enter number of IPs to test [default 1000]: ")
	countStr, _ := reader.ReadString('\n')
	if c, err := strconv.Atoi(strings.TrimSpace(countStr)); err == nil && c > 0 {
		cfg.Count = c
	} else {
		cfg.Count = 1000
	}

	fmt.Print(" Enter concurrency worker threads [default 60]: ")
	wStr, _ := reader.ReadString('\n')
	if w, err := strconv.Atoi(strings.TrimSpace(wStr)); err == nil && w > 0 {
		cfg.Concurrency = w
	} else {
		cfg.Concurrency = 60
	}

	fmt.Print(" Enter proxy link (VLESS / Trojan - optional, press Enter to skip): ")
	proxyStr, _ := reader.ReadString('\n')
	proxyStr = strings.TrimSpace(proxyStr)
	if proxyStr != "" {
		if p, err := parser.Parse(proxyStr); err == nil {
			cfg.Proxy = p
			fmt.Printf(" Loaded: %s\n", p.Remark)
		}
	}

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	engine := scanner.NewEngine(cfg)
	results, _ := engine.Scan(ctx, func(progress scanner.ScanProgress) {
		tui.PrintProgress(progress)
	})
	fmt.Println()

	if len(results) > 0 {
		tui.PrintResultsTable(results)
		if cfg.Proxy != nil {
			fmt.Println(tui.CyanBright + " Ready-to-use Proxy Links with Clean IP:" + tui.Reset)
			fmt.Print(scanner.ExportConfigs(results[:3], cfg.Proxy))
		}
	}
}

func runTestListScan(reader *bufio.Reader) {
	fmt.Print("\n Enter file path or comma-separated IPs to test: ")
	input, _ := reader.ReadString('\n')
	input = strings.TrimSpace(input)

	var ips []string
	if _, err := os.Stat(input); err == nil {
		content, _ := os.ReadFile(input)
		for _, line := range strings.Split(string(content), "\n") {
			l := strings.TrimSpace(line)
			if l != "" && !strings.HasPrefix(l, "#") {
				ips = append(ips, l)
			}
		}
	} else {
		for _, p := range strings.Split(input, ",") {
			p = strings.TrimSpace(p)
			if p != "" {
				ips = append(ips, p)
			}
		}
	}

	if len(ips) == 0 {
		fmt.Println(" No valid IPs provided.")
		return
	}

	cfg := scanner.DefaultScanConfig()
	cfg.ExplicitIPs = ips
	cfg.Concurrency = 20

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	engine := scanner.NewEngine(cfg)
	results, _ := engine.Scan(ctx, func(progress scanner.ScanProgress) {
		tui.PrintProgress(progress)
	})
	fmt.Println()

	if len(results) > 0 {
		tui.PrintResultsTable(results)
	}
}

func runColoDiscovery() {
	fmt.Println("\n" + tui.CyanBright + " [DISCOVERY] Probing Cloudflare Data Center Colos reachable from your ISP..." + tui.Reset)
	cfg := scanner.DefaultScanConfig()
	cfg.Count = 400
	cfg.Concurrency = 50
	cfg.Mode = scanner.ProbeHTTP
	cfg.SpeedTest = false

	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()

	engine := scanner.NewEngine(cfg)
	results, _ := engine.Scan(ctx, func(progress scanner.ScanProgress) {
		tui.PrintProgress(progress)
	})
	fmt.Println()

	coloMap := make(map[string][]scanner.ScanResult)
	for _, r := range results {
		if r.Colo != "" {
			coloMap[r.Colo] = append(coloMap[r.Colo], r)
		}
	}

	fmt.Println("\n" + tui.CyanBright + " REACHABLE CLOUDFLARE DATA CENTERS (COLOS):" + tui.Reset)
	for colo, list := range coloMap {
		avg := 0.0
		for _, item := range list {
			avg += item.AvgLatencyMs()
		}
		avg /= float64(len(list))
		fmt.Printf(" • %s%-5s%s : %s%2d IPs found%s | Avg Latency: %s%.1f ms%s\n",
			tui.Yellow, colo, tui.Reset,
			tui.White, len(list), tui.Reset,
			tui.GreenBright, avg, tui.Reset,
		)
	}
}

func runVpnConnect(reader *bufio.Reader) {
	fmt.Print("\n Enter Clean IP (or press Enter to auto-scan first): ")
	ipInput, _ := reader.ReadString('\n')
	cleanIP := strings.TrimSpace(ipInput)

	fmt.Print(" Enter VLESS or Trojan config link: ")
	linkInput, _ := reader.ReadString('\n')
	link := strings.TrimSpace(linkInput)

	opts := vpn.DefaultTunnelOptions()
	opts.CleanIP = cleanIP
	if link != "" {
		p, err := parser.Parse(link)
		if err != nil {
			fmt.Printf(" [ERROR] Invalid config link: %v\n", err)
			return
		}
		opts.Proxy = p
	}

	mgr := vpn.NewManager(opts)
	fmt.Println(" [VPN] Connecting... (requires sudo for TUN device creation)")
	if err := mgr.Connect(); err != nil {
		fmt.Printf(" [ERROR] %v\n", err)
		return
	}

	fmt.Println(tui.GreenBright + " [SUCCESS] Connected! All internet routed via tunnel. Iranian sites bypassed." + tui.Reset)
	fmt.Println(" Press Enter to disconnect...")
	_, _ = reader.ReadString('\n')
	mgr.Disconnect()
	fmt.Println(" [VPN] Disconnected.")
}
