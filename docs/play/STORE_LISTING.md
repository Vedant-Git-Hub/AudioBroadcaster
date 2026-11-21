# Draft Google Play listing

## App details

- App name: **AudioBridge**
- Category: **Music & Audio**
- Default language: English
- Price: Free
- Ads: None
- In-app purchases: None
- Package: `com.a1saudio.receiver`
- Minimum Android version: Android 10 (API 29)
- Requires companion hardware: Ai-Thinker ESP32-A1S Audio Kit V2.2 A149 with this project's firmware
- Privacy policy: `https://github.com/Vedant-Git-Hub/AudioBroadcaster/blob/main/docs/PRIVACY_POLICY.md`

## Short description

Listen to your AudioBridge board's stereo audio over local Wi-Fi.

## Full description

AudioBridge brings line-in audio from a compatible ESP32-A1S Audio Kit board to your Android phone over local Wi-Fi.

Connect the board and phone to the same network, then tap Start listening. The app finds the board automatically and plays its audio through your selected media output, including the phone speaker or USB-C headphones. Listening continues in the background; use Stop in the notification when you are done.

Features:
- Uncompressed 48 kHz stereo audio from the board's LINEIN jack.
- Automatic discovery, with no IP address to type.
- Background playback and a notification Stop action.
- Shortcut to the board's Wi-Fi setup page.

Requires the AudioBridge firmware on an Ai-Thinker ESP32-A1S Audio Kit V2.2 A149, a source connected to LINEIN, and a local Wi-Fi network. The board uses 2.4 GHz Wi-Fi. The app works on Android 10 and newer. Audio travels over the local network without app-level encryption; use a trusted Wi-Fi network. Audio quality and latency depend on signal strength and network conditions.

## Review access note

Audio playback requires the companion ESP32-A1S board and an active LINEIN source. The app opens without an account and its setup screen is available without hardware. A Play reviewer cannot hear the stream without a compatible board on the same local network. Provide a short demonstration video and hardware testing instructions in Play Console if requested.

## Data safety draft

The Android app has no ads, analytics, account, developer server, microphone access, or stored audio. It exchanges discovery and audio packets with a board on the user's local Wi-Fi. Confirm the final Data safety answers against the signed release and Play Console questionnaire before submission.
