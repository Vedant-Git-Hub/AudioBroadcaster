# UDP protocol (AUB1)

The firmware listens on UDP port **45678** by default for both ASCII control messages and audio packets. A receiver uses **one UDP socket** for discovery, registration, keep-alives, and audio. The port can be changed in firmware menuconfig; the current Android app assumes 45678.

## Control messages

All control messages are ASCII and end in a newline. The receiver first sends `DISCOVER AUB1\n` to a local-network broadcast address on port 45678. The board replies to that socket with:

```text
HERE AUB1 48000 2 16 240 45678 STA
```

The final field is `STA` when joined to a router or `AP` in setup-hotspot mode. The sender's address in the UDP reply is the board's current IP. The numeric fields are sample rate, channel count, sample bits, sample frames per datagram, and UDP port. This reply reserves space for future protocol extensions; receivers should validate the known fields.

| Receiver command | Board reply | Meaning |
| --- | --- | --- |
| `JOIN\n` | `OK AUB1 48000 2 16 240\n` | Register this socket as a listener; audio begins. |
| `JOIN\n` | `ERR full\n` | All configured slots are occupied. |
| `KEEP\n` | `OK AUB1 48000 2 16 240\n` | Refresh a registered slot every three seconds. |
| `LEAVE\n` | `OK leave\n` | Release the slot. |
| Unknown command | `ERR command\n` | Command not recognized. |

Slots expire after **10 seconds** without a valid keep-alive. The maximum slot count is configurable from **1 to 10**. A registration is identified by the client's IPv4 address **and UDP source port**.

The board also accepts `GET STATE\n`, returning `STATE BOARD 1 VOL <0..100> MUTE <0|1> PLAYING <0|1> NEXT 0 PREV 0\n`. `SRC VOL <0..100>\n`, `SRC MUTE <0|1>\n`, and `SRC PLAYPAUSE\n` change the board's outgoing stream. Pausing sends silence while keeping listeners connected. Skip is unavailable because LINEIN carries audio only.

## Audio datagram

Each audio datagram contains a **16-byte header** and **960-byte payload**. Multi-byte header fields use network byte order; PCM samples use little-endian signed 16-bit values and alternate left/right.

| Offset | Bytes | Field |
| ---: | ---: | --- |
| 0 | 4 | ASCII magic `AUB1` |
| 4 | 4 | Unsigned packet sequence number, wraps at 2³² |
| 8 | 4 | Unsigned first sample-frame index, wraps at 2³² |
| 12 | 2 | Number of sample frames: 240 |
| 14 | 1 | Number of channels: 2 |
| 15 | 1 | Format code: 1 = signed 16-bit little-endian PCM |
| 16 | 960 | Interleaved stereo PCM (`240 × 2 × 2` bytes) |

At 48,000 sample frames per second, a packet covers **5 ms**. The PCM payload rate is **1.536 Mbit/s per listener**, excluding UDP/IP/Wi-Fi overhead. Sequence gaps indicate missing packets; a receiver should discard stale packets and bound its playback queue so latency does not grow indefinitely. The current Android app inserts short silence gaps and logs packet loss, queue drops, and audio underruns for diagnosis.
