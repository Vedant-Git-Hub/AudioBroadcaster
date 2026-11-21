# Ubuntu host setup and one-command builds

Run these commands from the repository root on an x86_64 Ubuntu host. `setup_host` installs system packages, ESP-IDF v5.4.1, the tested ESP-ADF commit, Android command-line tools, Android platform 36, build-tools 36, and platform-tools. It also runs both build scripts so missing dependencies are found during setup. Package installation requires `sudo` and Android SDK licenses are accepted during setup.

```sh
./setup_host
./build_ef
./flash_ef
./build_aa
./flash_aa
```

`flash_ef` builds the latest firmware and auto-detects a single CP210x/CH340/FTDI-style USB serial adapter. Connect the Audio Kit to **UART / POWER**. If more than one serial adapter is connected, specify `./flash_ef --port /dev/ttyUSB0` or set `AUDIOBRIDGE_ESP_PORT`. The port must be writable; if setup added you to the `dialout` group, log out and back in before flashing.

`flash_aa` builds and installs the debug APK with ADB. Enable USB debugging, unlock the phone, and approve the debugging prompt. If several Android devices are connected, use `./flash_aa --serial SERIAL` or set `AUDIOBRIDGE_ANDROID_SERIAL`. It never uninstalls the app or clears its data. A debug APK cannot replace a Play-signed app with the same package name; Android requires matching signatures for updates.

The Android release bundle is built with `./build_aa --release`. Signing uses the local upload properties at `~/.config/audiobridge/upload.properties` if present; release signing keys are never committed. Back up the upload keystore and properties before using them for Play releases.

By default, setup places `esp-idf` and `esp-adf` beside this repository and the Android SDK in `$HOME/Android/Sdk`. Set `AUDIOBRIDGE_TOOLCHAIN_DIR`, `AUDIOBRIDGE_IDF_PATH`, `AUDIOBRIDGE_ADF_PATH`, or `ANDROID_HOME` to use another location. Existing toolchain checkouts must match the tested commits; setup will stop rather than silently replace them. For an already provisioned machine, `./setup_host --skip-system-packages` checks/downloads user-level tools and verifies both builds without invoking `sudo`.
