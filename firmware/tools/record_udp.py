#!/usr/bin/env python3
"""Record an AUB1 UDP stream as a WAV file for board bring-up."""

import argparse
import socket
import struct
import time
import wave


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("board_ip", help="IP address printed by the ESP32 serial log")
    parser.add_argument("output", help="WAV file to write")
    parser.add_argument("--seconds", type=float, default=10)
    parser.add_argument("--port", type=int, default=45678)
    args = parser.parse_args()
    if args.seconds <= 0:
        parser.error("--seconds must be positive")

    board = (args.board_ip, args.port)
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.settimeout(2)
    sock.sendto(b"JOIN\n", board)
    try:
        response, sender = sock.recvfrom(128)
        if sender != board or response != b"OK AUB1 48000 2 16 240\n":
            raise RuntimeError(f"Unexpected JOIN reply: {response!r} from {sender}")

        packets = 0
        missing = 0
        expected_sequence = None
        last_keep = time.monotonic()
        end = last_keep + args.seconds
        sock.settimeout(0.5)
        with wave.open(args.output, "wb") as wav:
            wav.setnchannels(2)
            wav.setsampwidth(2)
            wav.setframerate(48000)
            while time.monotonic() < end:
                if time.monotonic() - last_keep >= 3:
                    sock.sendto(b"KEEP\n", board)
                    last_keep = time.monotonic()
                try:
                    datagram, sender = sock.recvfrom(1200)
                except socket.timeout:
                    continue
                if sender != board or len(datagram) != 976 or datagram[:4] != b"AUB1":
                    continue
                sequence, _first_frame, frames, channels, fmt = struct.unpack(
                    ">IIHBB", datagram[4:16]
                )
                if (frames, channels, fmt) != (240, 2, 1):
                    continue
                if expected_sequence is not None:
                    gap = (sequence - expected_sequence) & 0xFFFFFFFF
                    if gap >= 0x80000000:  # Older or reordered packet.
                        continue
                    if gap:
                        missing += gap
                        wav.writeframesraw(b"\0" * 960 * min(gap, 200))
                wav.writeframesraw(datagram[16:])
                expected_sequence = (sequence + 1) & 0xFFFFFFFF
                packets += 1
        print(f"Saved {args.output}: {packets} packets, {missing} missing packets")
    finally:
        sock.sendto(b"LEAVE\n", board)
        sock.close()


if __name__ == "__main__":
    main()
