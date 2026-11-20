# AudioBroadcaster

AudioBroadcaster sends a stereo line-level source from an **Ai-Thinker ESP32-A1S Audio Kit V2.2 (A149)** to up to three Android listeners over Wi-Fi. The firmware uses ESP-ADF to capture the board's **LINEIN** jack. The Android 10+ app discovers the board automatically and plays audio through the phone's selected media output while a foreground notification provides a **Stop** action.

The transport is **UDP**, carrying uncompressed 48 kHz, 16-bit stereo PCM. The original ESP32 on this board does not support LE Audio broadcast/Auracast; the app uses Wi-Fi so ordinary Android phones can receive the stream.

## Quick start

1. Connect a stereo line-level source to **LINEIN**. Set the board's five DIP switches **OFF** and connect USB to **UART / POWER**.
2. [Build and flash the firmware](docs/SETUP.md#firmware). On first boot, join its `A1S-Audio` hotspot using password `audiobridge`.
3. Open **http://audiobridge.local/** (or **http://192.168.4.1/**) while connected to the hotspot. Save the credentials for your **2.4 GHz** router network. The board restarts and saves them in NVS. Its green LED stays lit when connected and blinks when disconnected or in setup mode.
4. [Build and install the Android app](docs/SETUP.md#android-app), connect the phone to the same router network, and tap **Start listening**. The app discovers the board; you do not enter an IP address.

The board retries a saved Wi-Fi network five times and returns to its setup hotspot if it cannot connect. The app can also receive audio while using that hotspot, though the phone may have no internet until the board joins a router.

## Documentation

- [Setup, build, and troubleshooting](docs/SETUP.md)
- [Architecture and limitations](docs/ARCHITECTURE.md)
- [UDP control and audio protocol](docs/PROTOCOL.md)
- [Project directory structure](docs/DIRECTORY_STRUCTURE.md)
- [Firmware details](firmware/README.md) and [Android app details](android/README.md)

## Current status

Firmware has been flashed on an A149 board. Audio transmission, the setup webpage, mDNS hostname, and UDP discovery have been exercised. The Android app has been installed on a Samsung phone and produced audible playback. Three listener sockets were tested from one computer; three separate phones and end-to-end latency have **not** been measured. Occasional stuttering and source input clipping remain areas for further testing. The firmware's optional silence gate can suppress low-level noise but may mute very quiet passages.

## License

Project code is released under [GPL-3.0](LICENSE). The vendored Ai-Thinker board component retains its own [MIT license](firmware/components/ai-thinker-esp32-a1s/LICENSE). ESP-IDF, ESP-ADF, Android SDK, and Gradle are external dependencies and are not included here.
