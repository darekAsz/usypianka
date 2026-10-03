# Offline asset tools

Python scripts that generate and verify the bundled noise loops (white, pink, brown)
and the debug-only spike assets. The assets are procedurally generated, so they carry
no third-party licence. Debug assets live in `app/src/debug/assets/sounds` and never
ship in the release APK.

## Setup

The virtual environment lives outside the repository so every git worktree can reuse it
(any venv outside the repo works):

```bash
python -m venv "$HOME/.venvs/usypianka-tools"
"$HOME/.venvs/usypianka-tools/Scripts/python.exe" -m pip install -r tools/requirements.txt   # Windows
# "$HOME/.venvs/usypianka-tools/bin/python" -m pip install -r tools/requirements.txt         # Linux / macOS
```

Pinned requirements: `numpy==2.5.2`, `soundfile==0.14.0` (soundfile bundles libsndfile,
so no `flac` or `ffmpeg` binary is needed).

## Regenerate

```bash
python tools/generate_noise.py                      # FLAC main assets (default)
python tools/generate_noise.py --main-format wav    # WAV fallback for the main assets
```

Writes `white/pink/brown.<fmt>` to `app/src/main/assets/sounds` and `spike_sine.flac`,
`spike_sine.wav`, `pink.wav` to `app/src/debug/assets/sounds`. Output is deterministic
(fixed seeds), so two runs produce byte-identical FLAC files. Every file is read back
and compared losslessly after writing. Switching `--main-format` removes the stale file
of the other format so the verifier still sees exactly one file per noise.

## Verify

```bash
python tools/verify_assets.py --self-test
python tools/verify_assets.py --expect main app/src/main/assets/sounds
python tools/verify_assets.py --expect debug app/src/debug/assets/sounds
```

The verifier exits 1 on a missing directory, missing or unexpected file, or any failed
check. Run it after every regeneration.

## Numbers used

| Item | Value |
|------|-------|
| Length / format | 30 s (1 323 000 frames), 44.1 kHz, mono, PCM16 (FLAC or WAV) |
| Loudness | -26 dBFS(A) A-weighted RMS for all three noises (+/- 0.3 dB gate) |
| Peak limit | <= -2 dBFS |
| High-pass (2nd order) | 20 Hz for white and pink, 40 Hz for brown (brown would clip at the common target otherwise) |
| Spectral slope | white 0, pink -3.01, brown -6.02 dB/octave (+/- 0.5, 100-6400 Hz) |
| Seeds | white 101, pink 202, brown 303 |
| Loop seam | exactly periodic (single inverse FFT of N points), wrap step not an outlier |
| Spike sine | 1 kHz, 5 s = 5000 whole cycles, amplitude 0.5, recurrence residual <= 1e-3 |
