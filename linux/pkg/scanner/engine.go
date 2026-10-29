package scanner

import (
	"context"
	"fmt"
	"sort"
	"sync"
	"sync/atomic"
	"time"
)

// Engine orchestrates the multi-phase scanning pipeline.
type Engine struct {
	config ScanConfig
	prober *Prober
}

// NewEngine creates a new scan engine instance.
func NewEngine(cfg ScanConfig) *Engine {
	return &Engine{
		config: cfg,
		prober: NewProber(cfg),
	}
}

// Task represents an individual endpoint probe assignment.
type Task struct {
	IP   string
	Port int
	SNI  string
}

// Scan executes the full scanning lifecycle, streaming progress updates through onProgress.
func (e *Engine) Scan(ctx context.Context, onProgress func(ScanProgress)) ([]ScanResult, error) {
	start := time.Now()
	var logs []string
	var logMu sync.Mutex

	log := func(msg string) {
		logMu.Lock()
		defer logMu.Unlock()
		t := time.Now().Format("15:04:05")
		logs = append(logs, fmt.Sprintf("[%s] %s", t, msg))
		if len(logs) > 40 {
			logs = logs[1:]
		}
	}

	snapshotLogs := func() []string {
		logMu.Lock()
		defer logMu.Unlock()
		cp := make([]string, len(logs))
		copy(cp, logs)
		return cp
	}

	log(fmt.Sprintf("Scan initialized. Mode: %s | Workers: %d | Timeout: %v",
		e.config.Mode, e.config.Concurrency, e.config.Timeout))

	ports := e.config.Ports
	if len(ports) == 0 {
		ports = []int{443}
	}

	// 1. Prepare Primary Target Tasks
	var tasks []Task
	if len(e.config.ExplicitIPs) > 0 {
		log(fmt.Sprintf("Scanning explicit list of %d target IPs", len(e.config.ExplicitIPs)))
		for _, ip := range e.config.ExplicitIPs {
			for _, port := range ports {
				tasks = append(tasks, Task{IP: ip, Port: port})
			}
		}
	} else {
		rm, err := NewRangeManager(e.config.CustomV4Ranges)
		if err != nil {
			return nil, fmt.Errorf("range manager initialization failed: %w", err)
		}
		ips := rm.Sample(e.config.Count)
		log(fmt.Sprintf("Sampled %d Cloudflare IPv4 addresses across CIDRs", len(ips)))
		for _, ip := range ips {
			for _, port := range ports {
				tasks = append(tasks, Task{IP: ip, Port: port})
			}
		}
	}

	totalProbes := len(tasks)
	var healthyResults []ScanResult
	var healthyMu sync.Mutex

	var testedCount int64
	var foundCount int64
	var stopEarly int32

	earlyTarget := e.config.Top * 4
	if earlyTarget < 25 {
		earlyTarget = 25
	}
	canStopEarly := e.config.SmartStop && len(e.config.ExplicitIPs) == 0

	// Worker pool concurrency
	concurrency := e.config.Concurrency
	if e.config.Gentle && concurrency > 25 {
		concurrency = 25
	}
	if concurrency < 1 {
		concurrency = 1
	}

	runProbePool := func(taskList []Task, isFallback bool) {
		taskChan := make(chan Task, len(taskList))
		for _, t := range taskList {
			taskChan <- t
		}
		close(taskChan)

		var wg sync.WaitGroup
		rateTicker := time.NewTicker(25 * time.Millisecond) // 40 pps rate limit for gentle mode
		defer rateTicker.Stop()

		for w := 0; w < concurrency; w++ {
			wg.Add(1)
			go func() {
				defer wg.Done()
				for task := range taskChan {
					if ctx.Err() != nil || atomic.LoadInt32(&stopEarly) == 1 {
						return
					}

					if e.config.Gentle {
						<-rateTicker.C
					}

					r := e.prober.Probe(task.IP, task.Port, task.SNI)
					atomic.AddInt64(&testedCount, 1)

					if r.Healthy {
						healthyMu.Lock()
						healthyResults = append(healthyResults, r)
						healthyMu.Unlock()
						f := atomic.AddInt64(&foundCount, 1)

						if canStopEarly && f >= int64(earlyTarget) {
							if atomic.CompareAndSwapInt32(&stopEarly, 0, 1) {
								log(fmt.Sprintf("Smart-stop: %d healthy IPs found, concluding primary phase early", f))
							}
						}
					}

					// Emit progress update
					if onProgress != nil {
						healthyMu.Lock()
						topCurrent := TopByLatency(healthyResults, e.config.Top)
						healthyMu.Unlock()

						onProgress(ScanProgress{
							Phase:         PhaseProbing,
							Tested:        int(atomic.LoadInt64(&testedCount)),
							Total:         totalProbes,
							Found:         int(atomic.LoadInt64(&foundCount)),
							ElapsedMs:     time.Since(start).Milliseconds(),
							Best:          topCurrent,
							UsingFallback: isFallback,
							Logs:          snapshotLogs(),
						})
					}
				}
			}()
		}
		wg.Wait()
	}

	// ---- Phase 1: Range Scan ----
	runProbePool(tasks, false)

	// ---- Phase 1b: Open-site Fallback ----
	if len(healthyResults) == 0 && e.config.FallbackToDomains {
		log("No clean IPs found in range scan. Initiating open-site domain fallback...")
		fallbackDomains := e.config.FallbackDomains
		if len(fallbackDomains) == 0 {
			fallbackDomains = LoadFallbackDomains()
		}

		if len(fallbackDomains) > 0 {
			if onProgress != nil {
				onProgress(ScanProgress{
					Phase:         PhaseResolving,
					ElapsedMs:     time.Since(start).Milliseconds(),
					UsingFallback: true,
					Logs:          snapshotLogs(),
				})
			}

			resolvedIPs := ResolveDomains(fallbackDomains, 100)
			log(fmt.Sprintf("Resolved %d domain edge IPs for fallback", len(resolvedIPs)))

			var fbTasks []Task
			for _, ip := range resolvedIPs {
				for _, port := range ports {
					fbTasks = append(fbTasks, Task{IP: ip, Port: port})
				}
			}
			totalProbes += len(fbTasks)
			runProbePool(fbTasks, true)
		}
	}

	healthyMu.Lock()
	candidates := TopByLatency(healthyResults, e.config.Top)
	healthyMu.Unlock()

	if len(candidates) == 0 {
		log("Scan completed: 0 healthy Cloudflare edges discovered.")
		if onProgress != nil {
			onProgress(ScanProgress{
				Phase:     PhaseDone,
				Tested:    int(testedCount),
				Total:     totalProbes,
				ElapsedMs: time.Since(start).Milliseconds(),
				Logs:      snapshotLogs(),
			})
		}
		return nil, nil
	}

	// ---- Phase 1.5: Stability Re-check ----
	log(fmt.Sprintf("Phase 1.5: Measuring stability & packet loss on top %d candidates...", len(candidates)))
	stabilityCount := e.config.StabilityCount
	if stabilityCount < 2 {
		stabilityCount = 2
	}

	var stableResults []ScanResult
	var stableMu sync.Mutex
	var sWg sync.WaitGroup
	var sDone int64

	for _, cand := range candidates {
		sWg.Add(1)
		go func(c ScanResult) {
			defer sWg.Done()
			success := 0
			var allLatencies []int64

			for i := 0; i < stabilityCount; i++ {
				if ctx.Err() != nil {
					return
				}
				check := e.prober.Probe(c.IP, c.Port, "")
				if check.Healthy {
					success++
					allLatencies = append(allLatencies, check.Latencies...)
				}
				time.Sleep(50 * time.Millisecond)
			}

			passRate := float64(success) / float64(stabilityCount)
			c.PassRate = passRate
			c.Latencies = allLatencies

			stableMu.Lock()
			stableResults = append(stableResults, c)
			stableMu.Unlock()

			atomic.AddInt64(&sDone, 1)

			if onProgress != nil {
				stableMu.Lock()
				currentBest := TopByStability(stableResults, e.config.Top)
				stableMu.Unlock()

				onProgress(ScanProgress{
					Phase:         PhaseStability,
					Tested:        totalProbes,
					Total:         totalProbes,
					Found:         int(foundCount),
					Validated:     int(atomic.LoadInt64(&sDone)),
					ValidateTotal: len(candidates),
					ElapsedMs:     time.Since(start).Milliseconds(),
					Best:          currentBest,
					Logs:          snapshotLogs(),
				})
			}
		}(cand)
	}
	sWg.Wait()

	stableMu.Lock()
	candidates = TopByStability(stableResults, e.config.Top)
	stableMu.Unlock()

	// ---- Phase 2: Speed / Throughput Test ----
	if e.config.SpeedTest && len(candidates) > 0 {
		log(fmt.Sprintf("Phase 2: Measuring real download speed on top %d edges...", len(candidates)))
		var speedResults []ScanResult
		var speedMu sync.Mutex
		var vDone int64

		var speedWg sync.WaitGroup
		sem := make(chan struct{}, 4) // max 4 parallel speed tests

		for _, cand := range candidates {
			speedWg.Add(1)
			go func(c ScanResult) {
				defer speedWg.Done()
				sem <- struct{}{}
				defer func() { <-sem }()

				if ctx.Err() != nil {
					return
				}

				tested := MeasureThroughput(c, e.config)

				speedMu.Lock()
				speedResults = append(speedResults, tested)
				currentBest := TopBySpeed(speedResults, e.config.Top)
				speedMu.Unlock()

				atomic.AddInt64(&vDone, 1)

				if onProgress != nil {
					onProgress(ScanProgress{
						Phase:         PhaseValidating,
						Tested:        totalProbes,
						Total:         totalProbes,
						Found:         int(foundCount),
						Validated:     int(atomic.LoadInt64(&vDone)),
						ValidateTotal: len(candidates),
						ElapsedMs:     time.Since(start).Milliseconds(),
						Best:          currentBest,
						Logs:          snapshotLogs(),
					})
				}
			}(cand)
		}
		speedWg.Wait()

		speedMu.Lock()
		candidates = TopBySpeed(speedResults, e.config.Top)
		speedMu.Unlock()
	}

	log(fmt.Sprintf("Scan completed in %.1fs. Found %d clean Cloudflare IPs.",
		time.Since(start).Seconds(), len(candidates)))

	if onProgress != nil {
		onProgress(ScanProgress{
			Phase:         PhaseDone,
			Tested:        totalProbes,
			Total:         totalProbes,
			Found:         int(foundCount),
			Validated:     len(candidates),
			ValidateTotal: len(candidates),
			ElapsedMs:     time.Since(start).Milliseconds(),
			Best:          candidates,
			Logs:          snapshotLogs(),
		})
	}

	return candidates, nil
}

// TopByLatency sorts results by average latency ascending.
func TopByLatency(items []ScanResult, top int) []ScanResult {
	cp := make([]ScanResult, len(items))
	copy(cp, items)
	sort.Slice(cp, func(i, j int) bool {
		return cp[i].AvgLatencyMs() < cp[j].AvgLatencyMs()
	})
	if len(cp) > top {
		return cp[:top]
	}
	return cp
}

// TopByStability sorts results by pass rate descending, then jitter, then latency.
func TopByStability(items []ScanResult, top int) []ScanResult {
	cp := make([]ScanResult, len(items))
	copy(cp, items)
	sort.Slice(cp, func(i, j int) bool {
		if cp[i].PassRate != cp[j].PassRate {
			return cp[i].PassRate > cp[j].PassRate
		}
		if cp[i].JitterMs() != cp[j].JitterMs() {
			return cp[i].JitterMs() < cp[j].JitterMs()
		}
		return cp[i].AvgLatencyMs() < cp[j].AvgLatencyMs()
	})
	if len(cp) > top {
		return cp[:top]
	}
	return cp
}

// TopBySpeed sorts results by download throughput descending.
func TopBySpeed(items []ScanResult, top int) []ScanResult {
	cp := make([]ScanResult, len(items))
	copy(cp, items)
	sort.Slice(cp, func(i, j int) bool {
		if cp[i].ThroughputBps != cp[j].ThroughputBps {
			return cp[i].ThroughputBps > cp[j].ThroughputBps
		}
		return cp[i].AvgLatencyMs() < cp[j].AvgLatencyMs()
	})
	if len(cp) > top {
		return cp[:top]
	}
	return cp
}
