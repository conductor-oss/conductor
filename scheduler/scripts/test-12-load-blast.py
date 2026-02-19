#!/usr/bin/env python3
"""
Test #12: Multi-client simultaneous workflow submission (load test).

Fires N concurrent POST /api/workflow requests at a synchronized epoch second,
measuring response times and error rates. Run simultaneously on styx and spartacus
to simulate a real thundering-herd at the top of a minute.

Usage:
  python3 test-12-load-blast.py [--url URL] [--count N] [--target EPOCH_SEC]

  --url     Conductor URL (default: http://localhost:8080)
  --count   Concurrent requests per machine (default: 25)
  --target  Unix epoch second to fire at (default: now+10)

Example (styx):
  python3 test-12-load-blast.py --url http://localhost:8080 --count 25 --target 1771600000

Example (spartacus):
  python3 test-12-load-blast.py --url http://192.168.65.221:8080 --count 25 --target 1771600000
"""

import argparse
import threading
import time
import urllib.request
import urllib.error
import json
import statistics

def fire_workflow(url, idx, results):
    payload = json.dumps({
        "name": "concurrent_demo_workflow",
        "version": 1,
        "input": {"loadTestIndex": idx}
    }).encode()

    req = urllib.request.Request(
        f"{url}/api/workflow",
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST"
    )

    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            body = resp.read().decode()
            elapsed = int((time.time() - t0) * 1000)
            results.append({"status": resp.status, "latency_ms": elapsed, "wf_id": body.strip('"')})
    except urllib.error.HTTPError as e:
        elapsed = int((time.time() - t0) * 1000)
        results.append({"status": e.code, "latency_ms": elapsed, "error": str(e)})
    except Exception as e:
        elapsed = int((time.time() - t0) * 1000)
        results.append({"status": 0, "latency_ms": elapsed, "error": str(e)})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default="http://localhost:8080")
    parser.add_argument("--count", type=int, default=25)
    parser.add_argument("--target", type=int, default=0)
    args = parser.parse_args()

    target = args.target if args.target > 0 else int(time.time()) + 10
    print(f"[{args.url}] Blasting {args.count} concurrent requests at epoch {target}")

    # Wait until target second
    wait = target - time.time()
    if wait > 0:
        time.sleep(wait)

    results = []
    threads = []
    blast_start = time.time()

    for i in range(args.count):
        t = threading.Thread(target=fire_workflow, args=(args.url, i, results))
        threads.append(t)

    for t in threads:
        t.start()
    for t in threads:
        t.join()

    blast_elapsed = int((time.time() - blast_start) * 1000)

    # Summarize
    success = [r for r in results if 200 <= r["status"] < 300]
    errors = [r for r in results if r["status"] == 0 or r["status"] >= 400]
    latencies = [r["latency_ms"] for r in results]

    print(f"\n=== Results from {args.url} ({args.count} requests) ===")
    print(f"  Wall time for all threads: {blast_elapsed}ms")
    print(f"  Success (2xx):  {len(success)}")
    print(f"  Errors (4xx/5xx/timeout): {len(errors)}")
    if latencies:
        print(f"  Latency min/median/p95/max: "
              f"{min(latencies)} / {int(statistics.median(latencies))} / "
              f"{int(sorted(latencies)[int(len(latencies)*0.95)]) if len(latencies) > 1 else latencies[0]} / "
              f"{max(latencies)} ms")
    if errors:
        print(f"  Error details:")
        for r in errors[:5]:
            print(f"    HTTP {r['status']}: {r.get('error','')}")

    # Write raw results for post-analysis
    import socket
    hostname = socket.gethostname().split(".")[0]
    out_file = f"/tmp/test12-results-{hostname}.json"
    with open(out_file, "w") as f:
        json.dump(results, f, indent=2)
    print(f"\n  Raw results written to {out_file}")


if __name__ == "__main__":
    main()
