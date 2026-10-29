BINARY=asha-linux
VERSION=0.8.7
DESKTOP_FILE=asha-scanner.desktop
ICON_FILE=assets/icon.png

.PHONY: all build test clean install run gui web

all: build

build:
	@echo "Building $(BINARY) v$(VERSION)..."
	go build -ldflags="-s -w" -o $(BINARY) .
	@echo "Build successful: ./$(BINARY)"

test:
	@echo "Running tests..."
	go test -v ./...

gui: build
	./$(BINARY) gui

run: build
	./$(BINARY)

web: build
	./$(BINARY) serve -port 8080

install: build
	@echo "Installing $(BINARY) to /usr/local/bin/..."
	sudo cp $(BINARY) /usr/local/bin/
	sudo chmod +x /usr/local/bin/$(BINARY)
	@echo "Installing helper binaries to /usr/local/bin/..."
	sudo cp bin/xray /usr/local/bin/ 2>/dev/null || true
	sudo cp bin/aether /usr/local/bin/ 2>/dev/null || true
	sudo cp bin/tun2socks /usr/local/bin/ 2>/dev/null || true
	sudo cp bin/asha-net-helper /usr/local/bin/ 2>/dev/null || true
	sudo chmod +x /usr/local/bin/xray /usr/local/bin/aether /usr/local/bin/tun2socks /usr/local/bin/asha-net-helper 2>/dev/null || true
	sudo setcap cap_net_admin,cap_net_bind_service=+ep /usr/local/bin/tun2socks 2>/dev/null || true
	@echo "Configuring passwordless TUN routing for whole-system VPN..."
	echo "ALL ALL=(ALL) NOPASSWD: /usr/local/bin/asha-net-helper" | sudo tee /etc/sudoers.d/asha-scanner >/dev/null
	sudo chmod 440 /etc/sudoers.d/asha-scanner
	@echo "Installing icon and desktop entry..."
	sudo mkdir -p /usr/share/icons/hicolor/256x256/apps/
	sudo cp $(ICON_FILE) /usr/share/icons/hicolor/256x256/apps/asha-scanner.png
	sudo cp $(DESKTOP_FILE) /usr/share/applications/
	sudo update-desktop-database 2>/dev/null || true
	@echo "Installed successfully! Asha Scanner is now in your Linux Application Menu."

clean:
	rm -f $(BINARY)
