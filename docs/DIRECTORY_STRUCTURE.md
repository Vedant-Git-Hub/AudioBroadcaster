# Project directory structure

```text
AudioBroadcaster/
├── README.md                    Project overview and quick start
├── LICENSE                      GPL-3.0 project license
├── .gitignore                   Excludes builds, credentials, and local captures
├── docs/
│   ├── SETUP.md                 Hardware, firmware, and Android setup
│   ├── ARCHITECTURE.md          Data flow, connection lifecycle, limitations
│   ├── PROTOCOL.md              UDP discovery, control, and PCM packet format
│   └── DIRECTORY_STRUCTURE.md   This file
├── firmware/
│   ├── CMakeLists.txt            ESP-IDF/ESP-ADF project and board link setup
│   ├── sdkconfig.defaults        Reproducible board and build defaults
│   ├── dependencies.lock         Pinned ESP component versions
│   ├── README.md                 Firmware configuration notes
│   ├── main/
│   │   ├── main.c                Audio capture, ten listener slots, UDP streaming
│   │   ├── network.c             Wi-Fi, NVS, setup page, mDNS, LED status
│   │   ├── network.h             Network interface for the audio task
│   │   ├── Kconfig.projbuild     Menuconfig options
│   │   ├── idf_component.yml     mDNS component dependency
│   │   └── CMakeLists.txt        Main component dependencies
│   ├── components/
│   │   └── ai-thinker-esp32-a1s/ Vendored board definitions and MIT license
│   └── tools/
│       └── record_udp.py         Desktop UDP-to-WAV capture utility
└── android/
    ├── settings.gradle           Gradle repositories and module list
    ├── build.gradle              Android Gradle plugin version
    ├── gradle.properties         Gradle settings
    ├── gradlew                   Portable Gradle launcher
    ├── gradle/wrapper/           Pinned Gradle distribution and launcher JAR
    ├── README.md                 Android behavior and build notes
    └── app/
        ├── build.gradle          SDK levels, app ID, and version
        └── src/main/
            ├── AndroidManifest.xml
            ├── java/com/a1saudio/receiver/
            │   ├── MainActivity.java  Start/Stop/setup interface
            │   └── AudioService.java  Discovery, UDP receive, AudioTrack playback
            └── res/             Launcher/notification icons and colors
```

`firmware/build`, `firmware/managed_components`, `firmware/sdkconfig`, Android build directories, and `android/local.properties` are generated on each developer machine and are not committed. Wi-Fi credentials are stored in the ESP32's NVS after setup, not in repository source files. The local `artifacts/` folder holds test audio and is also excluded.
