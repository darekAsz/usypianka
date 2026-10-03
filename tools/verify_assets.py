"""Acceptance gate for the bundled loop assets (levels, peaks, spectral slope, loop seam).

Usage:
    verify_assets.py [--expect main|debug] [--self-test] [dir]

Exit code 0 only when every expected file is present and passes; any missing
directory, missing file, unexpected file or failed check yields exit code 1.
Depends on numpy and soundfile only. Importable as a module (the generator
reuses a_weighting_db / a_weighted_rms_dbfs so one A-weighting implementation
is used for both scaling and checking).
"""
import argparse
import sys
from pathlib import Path

import numpy as np
import soundfile as sf

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_DIRS = {
    "main": REPO_ROOT / "app" / "src" / "main" / "assets" / "sounds",
    "debug": REPO_ROOT / "app" / "src" / "debug" / "assets" / "sounds",
}

SR = 44100
NOISE_FRAMES = 1_323_000          # 30 s
SINE_FRAMES = 220_500             # 5 s = 5000 cycles of 1 kHz
SINE_HZ = 1000.0
TARGET_DBFS_A = -26.0
LEVEL_TOLERANCE_DB = 0.3
PEAK_LIMIT_DBFS = -2.0
SLOPE_TOLERANCE = 0.5
SINE_RESIDUAL_LIMIT = 1e-3
NOISE_SLOPES = {"white": 0.0, "pink": -3.01, "brown": -6.02}
OCTAVE_EDGES_HZ = (100, 200, 400, 800, 1600, 3200, 6400)
MAIN_STEMS = ("white", "pink", "brown")
DEBUG_FILES = ("spike_sine.flac", "spike_sine.wav", "pink.wav")
AUDIO_SUFFIXES = (".flac", ".wav")


def a_weighting_db(f):
    """IEC 61672 analytic A-weighting in dB (vectorised, 0 Hz -> -inf)."""
    f = np.asarray(f, dtype=np.float64)
    f2 = f * f
    with np.errstate(divide="ignore"):
        ra = (12194.0 ** 2 * f2 * f2) / (
            (f2 + 20.6 ** 2)
            * np.sqrt((f2 + 107.7 ** 2) * (f2 + 737.9 ** 2))
            * (f2 + 12194.0 ** 2)
        )
        return 20.0 * np.log10(ra) + 2.00


def _power_spectrum(x):
    """One-sided power per rfft bin such that sum(power) == mean(x**2) (Parseval)."""
    n = len(x)
    spec = np.fft.rfft(x)
    power = (np.abs(spec) ** 2) / (n * n)
    power[1:] *= 2.0
    if n % 2 == 0:
        power[-1] /= 2.0  # Nyquist bin is not mirrored
    return power


def a_weighted_rms_dbfs(x, sr):
    """A-weighted RMS in dBFS (full scale = 1.0) via rfft and Parseval."""
    x = np.asarray(x, dtype=np.float64)
    freqs = np.fft.rfftfreq(len(x), 1.0 / sr)
    weight_power = 10.0 ** (a_weighting_db(freqs) / 10.0)  # 0 Hz -> 0
    rms = np.sqrt(np.sum(_power_spectrum(x) * weight_power))
    return float(20.0 * np.log10(rms))


def psd_slope_db_per_octave(x, sr):
    """Spectral slope in dB/octave of the power spectral density (100-6400 Hz)."""
    x = np.asarray(x, dtype=np.float64)
    freqs = np.fft.rfftfreq(len(x), 1.0 / sr)
    power = _power_spectrum(x)
    centres, energies = [], []
    for lo, hi in zip(OCTAVE_EDGES_HZ[:-1], OCTAVE_EDGES_HZ[1:]):
        band = (freqs >= lo) & (freqs < hi)
        energies.append(10.0 * np.log10(np.sum(power[band])))
        centres.append(np.log2(np.sqrt(lo * hi)))
    slope = np.polyfit(centres, energies, 1)[0]
    return float(slope - 3.01)  # remove the +3.01 dB/oct bandwidth term


def seam_ok(x):
    """True when the loop wrap step is no larger than the 99.9th percentile step."""
    x = np.asarray(x, dtype=np.float64)
    wrap = abs(x[0] - x[-1])
    return bool(wrap <= np.percentile(np.abs(np.diff(x)), 99.9))


def sine_residual_max(x, sr, hz):
    """Max |x[n] - 2cos(w) x[n-1] + x[n-2]| over the cyclic sequence (incl. wrap)."""
    x = np.asarray(x, dtype=np.float64)
    two_cos = 2.0 * np.cos(2.0 * np.pi * hz / sr)
    residual = x - two_cos * np.roll(x, 1) + np.roll(x, 2)
    return float(np.max(np.abs(residual)))


def _peak_dbfs(x):
    peak = float(np.max(np.abs(x)))
    return 20.0 * np.log10(peak) if peak > 0 else float("-inf")


def check_noise(path, stem):
    """Return (ok, message) for a noise loop file."""
    info = sf.info(str(path))
    x, _ = sf.read(str(path), dtype="float64", always_2d=True)
    problems = []
    if info.channels != 1:
        problems.append(f"channels={info.channels} (need 1)")
    if info.samplerate != SR:
        problems.append(f"samplerate={info.samplerate} (need {SR})")
    if x.shape[0] != NOISE_FRAMES:
        problems.append(f"frames={x.shape[0]} (need {NOISE_FRAMES})")
    x = x[:, 0]
    level = a_weighted_rms_dbfs(x, info.samplerate)
    peak = _peak_dbfs(x)
    slope = psd_slope_db_per_octave(x, info.samplerate)
    seam = seam_ok(x)
    if abs(level - TARGET_DBFS_A) > LEVEL_TOLERANCE_DB:
        problems.append(f"A-RMS {level:.2f} not within {TARGET_DBFS_A} +/- {LEVEL_TOLERANCE_DB}")
    if peak > PEAK_LIMIT_DBFS:
        problems.append(f"peak {peak:.2f} above {PEAK_LIMIT_DBFS} dBFS")
    if abs(slope - NOISE_SLOPES[stem]) > SLOPE_TOLERANCE:
        problems.append(f"slope {slope:.2f} not within {NOISE_SLOPES[stem]} +/- {SLOPE_TOLERANCE}")
    if not seam:
        problems.append("seam is an outlier")
    detail = f"A-RMS={level:.2f} peak={peak:.2f} slope={slope:.2f} seam={'ok' if seam else 'BAD'}"
    if problems:
        return False, f"{detail} :: " + "; ".join(problems)
    return True, detail


def check_sine(path):
    """Return (ok, message) for the spike sine file."""
    info = sf.info(str(path))
    x, _ = sf.read(str(path), dtype="float64", always_2d=True)
    problems = []
    if info.channels != 1:
        problems.append(f"channels={info.channels} (need 1)")
    if info.samplerate != SR:
        problems.append(f"samplerate={info.samplerate} (need {SR})")
    if x.shape[0] != SINE_FRAMES:
        problems.append(f"frames={x.shape[0]} (need {SINE_FRAMES})")
    x = x[:, 0]
    freqs = np.fft.rfftfreq(len(x), 1.0 / info.samplerate)
    peak_hz = float(freqs[int(np.argmax(np.abs(np.fft.rfft(x))))])
    residual = sine_residual_max(x, info.samplerate, SINE_HZ)
    if abs(peak_hz - SINE_HZ) > 1.0:
        problems.append(f"FFT peak {peak_hz:.1f} Hz (need {SINE_HZ:.0f} +/- 1)")
    if residual > SINE_RESIDUAL_LIMIT:
        problems.append(f"residual {residual:.2e} above {SINE_RESIDUAL_LIMIT:.0e}")
    detail = f"peak={_peak_dbfs(x):.2f} sine={peak_hz:.1f}Hz residual={residual:.2e}"
    if problems:
        return False, f"{detail} :: " + "; ".join(problems)
    return True, detail


def check_file(path):
    stem = path.stem
    if stem in NOISE_SLOPES:
        return check_noise(path, stem)
    if stem == "spike_sine":
        return check_sine(path)
    return False, f"unexpected stem '{stem}'"


def verify_dir(directory, expect):
    """Verify a directory; return True only if everything is present and passes."""
    directory = Path(directory)
    if not directory.is_dir():
        print(f"{directory} FAIL directory does not exist")
        return False
    files = sorted(p for p in directory.iterdir() if p.suffix.lower() in AUDIO_SUFFIXES)
    ok = True
    if expect == "main":
        for stem in MAIN_STEMS:
            matches = [p for p in files if p.stem == stem]
            if len(matches) != 1:
                print(f"{stem} FAIL expected exactly one file, found {len(matches)}")
                ok = False
        expected = [p for p in files if p.stem in MAIN_STEMS]
        extras = [p for p in files if p.stem not in MAIN_STEMS]
    else:
        expected = [p for p in files if p.name in DEBUG_FILES]
        extras = [p for p in files if p.name not in DEBUG_FILES]
        for name in DEBUG_FILES:
            if not (directory / name).is_file():
                print(f"{name} FAIL missing")
                ok = False
    for p in extras:
        print(f"{p.name} FAIL unexpected file for --expect {expect}")
        ok = False
    for p in expected:
        passed, message = check_file(p)
        print(f"{p.name} {'PASS' if passed else 'FAIL'} {message}")
        ok = ok and passed
    return ok


def self_test():
    ok = True
    for hz, expected in ((63, -26.22), (100, -19.14), (1000, 0.00), (2500, 1.27), (10000, -2.49)):
        got = float(a_weighting_db(hz))
        if abs(got - expected) > 0.05:
            print(f"FAIL a_weighting_db({hz}) = {got:.2f}, expected {expected:.2f}")
            ok = False
    if a_weighting_db(0.0) != -np.inf:
        print("FAIL a_weighting_db(0) must be -inf")
        ok = False
    t = np.arange(SR) / SR
    sine = np.sqrt(2) * 10 ** (-20 / 20) * np.sin(2 * np.pi * 1000 * t)
    got = a_weighted_rms_dbfs(sine, SR)
    if abs(got - -20.0) > 0.05:
        print(f"FAIL 1 kHz sine RMS -20 dBFS measured {got:.2f}")
        ok = False
    print("self-test PASS" if ok else "self-test FAIL")
    return 0 if ok else 1


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--expect", choices=("main", "debug"), default="main")
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("dir", nargs="?", default=None)
    args = parser.parse_args(argv)
    if args.self_test:
        return self_test()
    directory = Path(args.dir) if args.dir else DEFAULT_DIRS[args.expect]
    return 0 if verify_dir(directory, args.expect) else 1


if __name__ == "__main__":
    sys.exit(main())
