#!/usr/bin/env python3
import argparse
import time

from padrelay_core import PORT, Receiver, local_ips, make_backend


def main():
    ap = argparse.ArgumentParser(description="PadRelay PC receiver (headless)")
    ap.add_argument("--port", type=int, default=PORT)
    ap.add_argument("--allow", help="only accept packets from this phone IP")
    args = ap.parse_args()

    backend = make_backend()
    print(f"Backend: {backend.name}")
    if backend.note:
        print(f"  note: {backend.note}")
    rx = Receiver(backend, args.port, args.allow)
    rx.start()
    print(f"Listening on UDP {args.port}. Enter one of these IPs in the phone app:")
    for ip in local_ips():
        print(f"  {ip}")
    print("Ctrl+C to quit.\n")

    was = False
    try:
        while True:
            time.sleep(0.5)
            s = rx.snapshot()
            if s["connected"] != was:
                was = s["connected"]
                print(f"Phone {'connected from ' + str(s['phone_ip']) if was else 'disconnected'}")
            if was:
                rtt = f"{s['rtt_ms']:.1f} ms" if s["rtt_ms"] is not None else "-"
                print(f"\r  {s['pps']:>4} pkt/s  latency {rtt:<9}",
                      end="", flush=True)
    except KeyboardInterrupt:
        pass
    finally:
        rx.close()


if __name__ == "__main__":
    main()
