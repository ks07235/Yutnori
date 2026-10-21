# Yutnori sound effects

Created for this project on 2026-10-20 by Codex at the owner's request.
All six WAVs are synthesized by `tools/generate_sound_effects.py`; no external
recordings, samples, or existing melodies are used. No third-party audio license
or attribution dependency is introduced. The generator is deterministic.

| Asset | Trigger | Character | Duration |
| --- | --- | --- | ---: |
| sfx_tap | Yut input and utility actions that previously beeped | Quiet wooden tap | 85 ms |
| sfx_select | Selecting a movable piece | Higher wooden tick | 105 ms |
| sfx_move | Completing a move without capture or finish | Two soft wooden contacts | 190 ms |
| sfx_capture | Capturing opposing pieces | Short rounded descending spring and knock | 390 ms |
| sfx_finish | Finishing pieces without winning the match | Ascending plucked chime | 900 ms |
| sfx_win | Completing the winning move | Original pentatonic plucked phrase | 2,480 ms |

The plucked tones suggest traditional Korean strings; they are synthesized rather
than recordings of a specific instrument. Files are mono 44.1 kHz 16-bit PCM,
with short fades, DC removal, and peak headroom. Total WAV bytes: 366,294.
Individual files decode well below SoundPool's per-sample memory limit.

## Playback

- SoundPool loads samples asynchronously at startup, using USAGE_GAME and media
  volume. No network or extra Android permission is needed.
- An input received before its sample is ready is skipped, never replayed later.
- At most two streams play simultaneously; a repeated cue replaces its previous
  stream. A winning cue clears other streams and is protected from rapid inputs.
- The existing sound checkbox and vibration checkbox remain independent. Turning
  sound off stops current cues immediately. Media volume zero silences playback.
- Pausing/leaving the Activity stops sounds and vibration. Returning does not
  replay sounds. Destroying the Activity releases native audio resources.
- Reduced motion still plays capture feedback. A finish has a distinct cue, and
  the winning move plays the victory cue instead of an additional finish cue.
- The sound setting does not override the phone's media volume or claim to follow
  ringer-mode settings. Users can mute media or turn off sound in game settings.

## Reproduction and verification

```text
python tools/generate_sound_effects.py
python tools/generate_sound_effects.py --check
```

`--check` validates the existing WAV headers, duration, nonzero RMS, peak headroom,
and zero-valued endpoints without rewriting files. Runtime regression tests cover
resource-to-event mapping, load readiness/failure, mute, pause/resume, repeated
victory inputs, invalid events, release, and reduced-motion capture integration.

Reference: [Android SoundPool API](https://developer.android.com/reference/android/media/SoundPool).
Phone-speaker loudness and subjective timbre should be reviewed on a real device.
