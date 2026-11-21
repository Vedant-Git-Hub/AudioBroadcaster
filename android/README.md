# AudioBridge Android app

This Android 10+ app discovers an AudioBroadcaster board on the current Wi-Fi network and receives its uncompressed 48 kHz stereo UDP stream. A foreground media playback service feeds `AudioTrack`; the notification's **Stop** action closes the receiver. Android selects the active speaker, USB-C headphones, or other media output. See the repository [setup guide](../docs/SETUP.md), [architecture](../docs/ARCHITECTURE.md), and [wire protocol](../docs/PROTOCOL.md).

## Build and install

With JDK 17 and Android SDK platform 36 installed:

```sh
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Android 13+ asks for notification permission. Connect the phone to the same Wi-Fi network as the board, open **AudioBridge**, and tap **Start listening**. The app sends `DISCOVER AUB1` on Wi-Fi and uses the reply to learn the board's IP; no manual address is needed. For first-time board setup, connect to `A1S-Audio` (password `audiobridge`) and tap **Set up board Wi-Fi** to open `http://audiobridge.local/`. After saving a 2.4 GHz router network, reconnect the phone to its normal Wi-Fi.

## Playback behavior

The service uses one UDP socket for discovery, registration, keep-alives, and audio. It binds that socket to Wi-Fi, so cellular data can remain available. A receive thread validates packets and places PCM into a bounded queue; a playback thread writes them to `AudioTrack`. The app retries discovery after a network disconnect. It logs received packets, missing packets, queue drops, and `AudioTrack` underruns every ten seconds under logcat tag **A1S receiver**.

The bounded queue smooths short Wi-Fi timing variations, but UDP delivery is not guaranteed. Received PCM is played without compression; missing packets are filled briefly with silence. The board's analog input must not clip if faithful source reproduction is required. The current build has a red, green, and magenta interface, launcher and notification icons, and a footer credit.

The Play release configuration targets API 36 and signs release bundles using a local upload keystore outside this repository. See the [release guide](../docs/play/RELEASE_GUIDE.md). Debug builds use their own key and can still be installed for local testing.
