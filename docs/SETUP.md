# Setup and build guide

## Hardware

- Tested board: Ai-Thinker ESP32-A1S Audio Kit V2.2 A149, ESP32-D0WD, 4 MB flash, ES8388 codec, board pin variant 5.
- Connect a **line-level stereo** source to the **LINEIN** 3.5 mm jack. The **EARPHONES** jack is an output.
- Set all five DIP switches **OFF** for the tested LINEIN/USB setup.
- Connect the data-capable USB cable to **UART / POWER**, not the power-only socket.
- The ESP32 uses **2.4 GHz Wi-Fi**. The phone may use either router band if the router allows devices on both bands to communicate.

## Firmware

Install ESP-IDF **5.4.1** and a compatible ESP-ADF checkout. The tested environment used ESP-ADF `v2.7-183-g01488579`. Set up the ESP-IDF toolchain as directed by Espressif, then build from this repository:

```sh
source /path/to/esp-idf/export.sh
export ADF_PATH=/path/to/esp-adf
cd firmware
idf.py set-target esp32
idf.py build
idf.py -p /dev/ttyUSB0 flash monitor
```

Replace the serial device with your actual port (`COMx` on Windows). The project includes the custom board defaults in `sdkconfig.defaults`; ESP-IDF downloads the mDNS component declared in `main/idf_component.yml`. `idf.py menuconfig` → **Audio broadcaster** controls the hotspot name/password, listener limit (1–3), UDP port, and optional silence gate. Use `idf.py reconfigure` after changing Kconfig options if a new option is not reflected in the generated `sdkconfig`.

The default hotspot is `A1S-Audio`, password `audiobridge`. On first boot, connect a phone to that hotspot and open **http://audiobridge.local/** or **http://192.168.4.1/**. Enter the router's 2.4 GHz SSID and password. The board stores both in ESP32 NVS and restarts. A normal reboot or firmware flash preserves them; **Forget saved Wi-Fi** on the setup page or a flash erase removes them. If the router connection fails after five retries, the board returns to the setup hotspot. The green LED is steady when the router is connected and blinks otherwise.

## Android app

Install **JDK 17** and the **Android SDK platform 34**. From the repository:

```sh
cd android
./gradlew assembleDebug
```

Install `android/app/build/outputs/apk/debug/app-debug.apk` on Android 10 or newer. For a USB-connected phone with debugging enabled:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Connect the phone to the router network and tap **Start listening**. The app sends a discovery request on Wi-Fi and needs no board IP. Playback uses the selected Android media output and continues through a foreground service; use the notification's **Stop** button to close it. Grant notification permission on Android 13+ when prompted.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Board not visible over USB | Use UART / POWER and a data cable. Check the serial port and USB driver. |
| LED keeps blinking after setup | Confirm the saved SSID is the 2.4 GHz network and the password is correct. Rejoin `A1S-Audio` to update them. |
| Setup page does not open | Stay on `A1S-Audio`; try `http://192.168.4.1/` if `.local` resolution fails. |
| App cannot find board | Put phone and board on the same LAN; disable router client isolation. Check that UDP port 45678 is allowed locally. |
| Fourth listener fails | The default limit is three. Existing listener slots expire after ten seconds without keep-alives. |
| Playback stutters | Check Wi-Fi signal and congestion. The app logs received packets, missing packets, queue drops, and AudioTrack underruns every ten seconds under tag `A1S receiver`. UDP cannot retransmit a late frame. |
| Loud source sounds distorted | Reduce the level feeding LINEIN, or use suitable analog attenuation. Once the input clips, the app cannot restore the peaks. |
| Very quiet audio disappears | Lower or disable the optional firmware silence gate in `menuconfig`. It trades quiet-source noise suppression for sensitivity to soft passages. |

For a computer-side capture, connect the computer to the same Wi-Fi and run `python3 firmware/tools/record_udp.py BOARD_IP capture.wav --seconds 10`. The script reports missing UDP packets and writes a stereo WAV file. See [PROTOCOL.md](PROTOCOL.md) for packet details.
