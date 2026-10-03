"""Deterministic generator for the bundled noise loops and the debug spike assets.

Noise is synthesised in the frequency domain (random phases, magnitude ~ f^exponent,
2nd-order high-pass) and brought back with one inverse FFT of exactly N points, so
the signal is exactly periodic: the loop seam is continuous with no crossfade.
All three noises are scaled to the same A-weighted RMS so switching noise does not
change perceived loudness and one calibration fits all of them.

Usage:
    generate_noise.py [--main-format flac|wav] [--main-dir DIR] [--debug-dir DIR]
"""
import argparse
import sys
from pathlib import Path

import numpy as np
import soundfile as sf

TOOLS_DIR = Path(__file__).resolve().parent
REPO_ROOT = TOOLS_DIR.parent
sys.path.insert(0, str(TOOLS_DIR))

from verify_assets import a_weighted_rms_dbfs  # noqa: E402  (single A-weighting implementation)

SR = 44100
SECONDS = 30
N = 1_323_000
TARGET_DBFS_A = -26.0
PEAK_LIMIT_DBFS = -2.0
# name -> (spectral exponent, high-pass corner Hz, seed)
NOISES = {
    "white": (0.0, 20.0, 101),
    "pink": (-0.5, 20.0, 202),
    "brown": (-1.0, 40.0, 303),  # 40 Hz corner so brown does not clip at the common target
}
SPIKE_HZ = 1000
SPIKE_FRAMES = 220_500  # 5 s = exactly 5000 cycles
SPIKE_AMPLITUDE = 0.5

assert N == SR * SECONDS


def synthesize(exponent, high_pass_hz, seed):
    """Return one exactly periodic float64 noise loop of N samples, scaled to the target."""
    rng = np.random.default_rng(seed)
    f = np.fft.rfftfreq(N, 1.0 / SR)
    mag = np.zeros_like(f)
    pos = f > 0
    mag[pos] = f[pos] ** exponent / np.sqrt(1.0 + (high_pass_hz / f[pos]) ** 4)
    phase = rng.uniform(0.0, 2.0 * np.pi, size=f.shape)
    phase[0] = 0.0   # DC bin real
    phase[-1] = 0.0  # Nyquist bin real (N is even)
    x = np.fft.irfft(mag * np.exp(1j * phase), n=N)
    measured = a_weighted_rms_dbfs(x, SR)
    x *= 10.0 ** ((TARGET_DBFS_A - measured) / 20.0)
    peak_db = 20.0 * np.log10(np.max(np.abs(x)))
    if peak_db > PEAK_LIMIT_DBFS:
        raise AssertionError(
            f"peak {peak_db:.2f} dBFS exceeds limit {PEAK_LIMIT_DBFS} dBFS"
            f" (exponent={exponent}, high-pass={high_pass_hz} Hz)"
        )
    return x


def quantize(x):
    return np.round(x * 32767).astype(np.int16)


def spike_sine():
    n = np.arange(SPIKE_FRAMES)
    return quantize(SPIKE_AMPLITUDE * np.sin(2.0 * np.pi * SPIKE_HZ * n / SR))


def write_checked(path, data):
    """Write PCM_16, read it back losslessly, print one summary line."""
    path.parent.mkdir(parents=True, exist_ok=True)
    sf.write(str(path), data, SR, subtype="PCM_16")
    back, _ = sf.read(str(path), dtype="int16")
    if not np.array_equal(back, data):
        raise AssertionError(f"lossless read-back mismatch for {path}")
    as_float = data.astype(np.float64) / 32768.0
    peak_db = 20.0 * np.log10(np.max(np.abs(as_float)))
    a_rms = a_weighted_rms_dbfs(as_float, SR)
    print(f"{path.name}: peak={peak_db:.2f} dBFS A-RMS={a_rms:.2f} dBFS(A) bytes={path.stat().st_size}")


def resolve(path_arg, default_rel):
    p = Path(path_arg) if path_arg else REPO_ROOT / default_rel
    return p if p.is_absolute() else REPO_ROOT / p


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--main-format", choices=("flac", "wav"), default="flac")
    parser.add_argument("--main-dir", default=None)
    parser.add_argument("--debug-dir", default=None)
    args = parser.parse_args(argv)
    main_dir = resolve(args.main_dir, "app/src/main/assets/sounds")
    debug_dir = resolve(args.debug_dir, "app/src/debug/assets/sounds")

    other_format = "wav" if args.main_format == "flac" else "flac"
    pink_pcm = None
    for name, (exponent, high_pass_hz, seed) in NOISES.items():
        pcm = quantize(synthesize(exponent, high_pass_hz, seed))
        write_checked(main_dir / f"{name}.{args.main_format}", pcm)
        stale = main_dir / f"{name}.{other_format}"
        if stale.exists():
            stale.unlink()  # verifier requires exactly one file per noise
        if name == "pink":
            pink_pcm = pcm

    sine = spike_sine()
    write_checked(debug_dir / "spike_sine.flac", sine)
    write_checked(debug_dir / "spike_sine.wav", sine)
    write_checked(debug_dir / "pink.wav", pink_pcm)
    return 0


if __name__ == "__main__":
    sys.exit(main())
