package scanner

import (
	"crypto/tls"
	"net"
	"time"
)

// FragmentingConn wraps a net.Conn to slice the initial TLS ClientHello flight.
type FragmentingConn struct {
	net.Conn
	config        AntiDpiConfig
	firstFlight   bool
	bytesWritten  int
}

// NewFragmentingConn returns a net.Conn with Anti-DPI write fragmentation enabled.
func NewFragmentingConn(c net.Conn, cfg AntiDpiConfig) net.Conn {
	if !cfg.Enabled {
		return c
	}
	return &FragmentingConn{
		Conn:        c,
		config:      cfg,
		firstFlight: true,
	}
}

// Write slices the initial handshake packets according to the configured chunk sizes.
func (f *FragmentingConn) Write(b []byte) (int, error) {
	if !f.firstFlight || !f.config.Enabled || len(b) == 0 {
		return f.Conn.Write(b)
	}

	// We only fragment the very first packet flight (the TLS ClientHello)
	f.firstFlight = false

	totalWritten := 0
	firstChunkSize := f.config.FirstChunk
	if firstChunkSize <= 0 || firstChunkSize > len(b) {
		firstChunkSize = len(b)
	}

	// Send chunk 1
	n, err := f.Conn.Write(b[:firstChunkSize])
	totalWritten += n
	if err != nil {
		return totalWritten, err
	}

	// Micro-delay so the NIC / kernel flushes the first TCP segment
	if f.config.Delay > 0 && totalWritten < len(b) {
		time.Sleep(f.config.Delay)
	}

	// Send remaining chunks
	remaining := b[firstChunkSize:]
	nextChunkSize := f.config.NextChunk
	if nextChunkSize <= 0 {
		nextChunkSize = len(remaining)
	}

	for len(remaining) > 0 {
		sendLen := nextChunkSize
		if sendLen > len(remaining) {
			sendLen = len(remaining)
		}

		n, err := f.Conn.Write(remaining[:sendLen])
		totalWritten += n
		if err != nil {
			return totalWritten, err
		}

		remaining = remaining[sendLen:]
		if len(remaining) > 0 && f.config.Delay > 0 {
			time.Sleep(f.config.Delay)
		}
	}

	return totalWritten, nil
}

// DialTLSHandshake performs a TLS handshake on a raw connection with optional fragmentation.
func DialTLSHandshake(rawConn net.Conn, sni string, timeout time.Duration, antiDpi AntiDpiConfig) (*tls.Conn, error) {
	fragConn := NewFragmentingConn(rawConn, antiDpi)

	tlsConfig := &tls.Config{
		ServerName:         sni,
		InsecureSkipVerify: true, // Verification is not required for edge latency / colo discovery
		NextProtos:         []string{"http/1.1"},
	}

	tlsConn := tls.Client(fragConn, tlsConfig)
	_ = tlsConn.SetDeadline(time.Now().Add(timeout))

	if err := tlsConn.Handshake(); err != nil {
		_ = tlsConn.Close()
		return nil, err
	}

	// Reset deadline for subsequent reads/writes
	_ = tlsConn.SetDeadline(time.Time{})
	return tlsConn, nil
}
