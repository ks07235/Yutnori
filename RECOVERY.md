# 복원 기록

## 현재 상태

`recovery/apk-aab` 브랜치는 보관된 `1.0.0 / build 38` 디버그 APK에서 복원한 프로젝트입니다. 원본 소스와 바이트 단위로 같은 프로젝트가 아니며, `1.3.2 / build 46` 소스라고 표시하거나 배포해서는 안 됩니다.

| 자료 | SHA-256 | 용도 |
| --- | --- | --- |
| `Yutnori-1.0.0-build38-test.apk` | `1522323B05C8F1670E939976A609AF651CF717D0B15AEF14A70114F36596E052` | Java 코드와 앱 리소스의 복원 기준 |
| `Yutnori-1.3.2-build46.aab` | `8F73DB11E3A4F02E5B3960FDAC4447B0221531AC5E42A065C05EBEF03CD239F3` | 최신 동작·리소스·R8 매핑의 참고 자료 |

두 바이너리의 실제 앱 ID는 `com.das312.yutnori`이고 Java 패키지는 `com.example.yutnoriapp`입니다. 기존 GitHub `main`은 이보다 오래된 초기 프로젝트였습니다.

## 수행한 검증

- JADX 1.5.6으로 build 38 코드를 추출하고 잘못 풀린 리스너 참조, 측정 상수, XML 스타일 값 및 누락된 리소스를 수정했습니다.
- `:app:assembleDebug`와 `:app:testDebugUnitTest`가 통과했습니다. 후자는 기존 예제 테스트만 포함하며 게임 규칙 회귀 검증을 의미하지 않습니다.
- Android API 36.1 에뮬레이터에서 설치, 실행, 팀 선택 및 게임판 표시를 확인했습니다. 전체 게임 진행과 모든 화면 크기는 아직 검증하지 않았습니다.

## build 46이 아직 소스에 반영되지 않은 이유

build 46 AAB에는 `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`이 있어 이름 복원에 도움이 됩니다. 다만 R8이 앱 클래스와 AndroidX 클래스를 병합하고 메서드를 인라인했기 때문에 자동 역컴파일 결과를 그대로 Java 소스로 빌드할 수는 없습니다. 예를 들어 이전의 `BoardPath` 동작 일부가 난독화된 라이브러리 클래스 안에 병합되어 있습니다.

build 46의 `AppUpdateChecker`, `PieceStackLayout`, `PieceStackView`, 이동 취소 상태, 갱신된 게임 규칙 및 네 가지 화면 레이아웃은 build 38 프로젝트에 순서대로 다시 구현하고 원본 AAB 실행 결과와 비교해야 합니다. 진행 전 원본 APK/AAB 및 로드맵 문서는 별도로 백업해 두는 것이 좋습니다.
