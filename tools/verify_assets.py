"""Acceptance gate for the bundled loop assets (RED stage: behaviour stubs only)."""
import sys

import numpy as np


def a_weighting_db(f):
    return np.zeros_like(np.asarray(f, dtype=np.float64))


def a_weighted_rms_dbfs(x, sr):
    return 0.0


def self_test():
    ok = True
    for hz, expected in ((63, -26.22), (100, -19.14), (1000, 0.00), (2500, 1.27), (10000, -2.49)):
        got = float(a_weighting_db(hz))
        if abs(got - expected) > 0.05:
            print(f"FAIL a_weighting_db({hz}) = {got:.2f}, expected {expected:.2f}")
            ok = False
    sr = 44100
    t = np.arange(sr) / sr
    sine = np.sqrt(2) * 10 ** (-20 / 20) * np.sin(2 * np.pi * 1000 * t)
    got = a_weighted_rms_dbfs(sine, sr)
    if abs(got - -20.0) > 0.05:
        print(f"FAIL 1 kHz sine RMS -20 dBFS measured {got:.2f}")
        ok = False
    print("self-test PASS" if ok else "self-test FAIL")
    return 0 if ok else 1


if __name__ == "__main__":
    if "--self-test" in sys.argv:
        sys.exit(self_test())
    sys.exit(0)
