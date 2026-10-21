# 1.6.1 / build 68

Date: 2026-10-20 (KST). Based on `refactor/complete-build67` / `7127316`.
The application-source changes are local and have not been committed or pushed.
This document records the completed local build and is published separately with
the UI/UX retrospective on 2026-10-21; publishing it does not publish build 68 source.

## Behavior

- Replace electronic beeps with six original, locally synthesized cues: utility
  tap, piece selection, move completion, capture, finish, and victory.
- Wooden modal impacts and a short pentatonic plucked-string victory phrase fit
  the physical-board theme. See [audio specification and provenance](sound-effects.md).
- Distinguish a normal finish from a match win. Winning moves play the victory
  cue instead of also playing the finish cue.
- Keep independent sound/vibration settings and media volume control. Muting
  stops ongoing audio. Leaving the Activity stops audio/haptics; resume does not
  replay old cues. Samples are loaded asynchronously; unready inputs are skipped.
- Restore missing capture feedback when reduced motion bypasses the animation.
- SoundPool permits at most two streams and protects the victory cue from rapid
  duplicate input. No network or new Android permission is required.

Game engine, movement coordinates, save format and match-statistics rules are
unchanged. New application version: `1.6.1`, versionCode `68`.

## Verification

- `testDebugUnitTest`: 164 tests, 0 failures, 0 errors. Includes seven new audio
  unit tests and one Activity integration test for reduced-motion capture and
  pause/resume feedback.
- `lintDebug`: 0 errors, 47 existing warnings. Known victory touch accessibility
  warnings remain; this release does not claim to resolve them.
- `compileDebugAndroidTestJavaWithJavac`: successful; instrumented tests were not
  run on a device in this task.
- `bundleRelease`: successful, with R8/resource shrinking and release signing.
- Generator `--check`: all six assets are valid mono 44.1 kHz / 16-bit PCM WAVs;
  nonzero RMS, peak headroom and zero-valued endpoints verified. Total 366,294 bytes.
- All six raw WAV entries are present in the signed AAB.
- Merged release manifest confirms package `com.das312.yutnori`, versionCode 68,
  versionName 1.6.1, minSdk 24 and targetSdk 36.
- `jarsigner -verify`: `jar verified`. Expected self-signed certificate,
  trust-chain and missing-timestamp warnings remain.
- Certificate SHA-256 matches build 67:
  `04E5FA9112BDC9F8320D4BE1B63099B80FF2DF6C62CFBE4D577CB53DC26F28DF`.
- Final AAB copy hash verified locally and in the Drive sync folder.

## Artifact

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| Yutnori-1.6.1-build68.aab | 5,422,122 | 285CBB115E43E2803731DBBF21B59630A87F7FDE4D7093157561B04FCF199F76 |

- Local: `C:\yutnori\builds\Yutnori-1.6.1-build68.aab`.
- Drive sync folder: `G:\내 드라이브\개발\2026\윷놀이\Yutnori-1.6.1-build68.aab`.
- AAB size increases by 299,904 bytes from build 67, including bundled sound assets.
- An APK was not requested or generated. Play Console upload was not performed.
- Drive cloud synchronization completion was not independently verified.
- Real-device listening, Bluetooth/headphone balance and subjective timbre remain
  manual checks. Automated playback tests do not prove physical speaker output.

## Release notes

```text
윷놀이 분위기에 맞는 효과음을 추가했습니다.
말 선택·이동·잡기·골인·우승을 소리로 구분할 수 있습니다.
앱을 나가거나 소리를 끌 때 재생이 멈추도록 개선했습니다.
애니메이션 줄이기 설정에서도 잡기 소리가 정상적으로 나도록 수정했습니다.
```
