# ESP32-A1S firmware

This ESP-ADF application captures the Ai-Thinker Audio Kit V2.2 A149 **LINEIN** jack and sends 48 kHz, 16-bit stereo PCM over UDP to up to ten registered listeners. See the repository [setup guide](../docs/SETUP.md), [architecture](../docs/ARCHITECTURE.md), and [wire protocol](../docs/PROTOCOL.md).

## Build

The tested toolchain is ESP-IDF **5.4.1** with a compatible ESP-ADF checkout. Use the custom board defaults supplied in `sdkconfig.defaults`:

```sh
source /path/to/esp-idf/export.sh
export ADF_PATH=/path/to/esp-adf
cd firmware
idf.py set-target esp32
idf.py build
idf.py -p /dev/ttyUSB0 flash monitor
```

Use the serial port for your machine. `main/idf_component.yml` declares the Espressif mDNS component; the component manager downloads it during configuration. The vendored board component is based on [trombik/esp-adf-component-ai-thinker-esp32-a1s](https://github.com/trombik/esp-adf-component-ai-thinker-esp32-a1s) and retains its MIT license. Its ADC input is set to **LINE2** for the tested physical LINEIN jack.

## Configuration

`idf.py menuconfig` → **Audio broadcaster** provides the existing Wi-Fi SSID/password fallback, setup hotspot name/password, maximum listener count (1–10), UDP port (default 45678), and optional silence gate. The board creates a setup hotspot when no router credentials exist. The HTTP form at **http://audiobridge.local/** saves router credentials to NVS; the board attempts that router on every boot and returns to its hotspot after five failed retries. GPIO22 drives the green LED: steady on when connected to the router, blinking otherwise.

The silence gate is enabled by default with an average PCM threshold of 900 over a 100 ms window. It mutes measured low-level LINEIN noise when the source is quiet, with short fades between states. For best preservation of very soft source passages, lower its threshold or disable it. Input samples that clip at the ES8388 ADC cannot be recovered later in firmware or the Android app.

## Desktop capture

Connect a computer to the board's hotspot or the same router network and run:

```sh
python3 tools/record_udp.py BOARD_IP capture.wav --seconds 10
```

Use `192.168.4.1` for the default setup hotspot. The script saves a stereo WAV and reports missing UDP packets. The Android app discovers the board automatically, so its users do not need this IP address.
