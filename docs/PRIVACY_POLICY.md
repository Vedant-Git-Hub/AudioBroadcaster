# AudioBridge Privacy Policy

Last updated: 21 November 2026

AudioBridge is an Android receiver app developed by Vedant Bhale. It receives audio from a compatible AudioBridge ESP32 board on the same local Wi-Fi network.

## Information the app uses

The app checks whether Wi-Fi is available and reads the phone's local network address and subnet to discover the board. It sends short discovery and listener registration messages to the board and receives audio packets from it. The phone's local IP address is visible to the board as part of normal network communication.

The app plays received audio through Android's selected media output. It does not record the phone's microphone, read the phone's media library, save received audio, or send the audio to the developer or an internet server. It does not include advertising, analytics, or third-party tracking SDKs.

The app requests notification permission where Android requires it so it can show the ongoing playback notification and Stop action. It uses a foreground service and wake lock while listening. You can stop listening from the app or its notification.

## Board Wi-Fi setup

The app's setup button opens the board's local setup page in your browser. Wi-Fi details entered there go directly to the board, which stores them on the board; the Android app does not receive or store those details. The board's setup page and audio stream use the local network. Use a trusted Wi-Fi network because the local UDP audio stream is not encrypted by the app.

## Sharing, retention, and deletion

AudioBridge does not collect personal data on a developer server and does not sell or share personal data with advertisers. The app does not keep an audio history or an account. Local playback data is held only in memory while listening and is cleared when playback stops or the app process ends. You can remove the app's local data using Android's app settings or by uninstalling it. Board Wi-Fi credentials can be deleted through the board's setup page.

## Contact

For privacy questions, contact the developer through the [AudioBroadcaster GitHub issue form](https://github.com/Vedant-Git-Hub/AudioBroadcaster/issues/new). You may need a GitHub account to submit an issue.
