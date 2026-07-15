# Yutnori

현실에서 던진 윷 결과를 입력해 사용하는 Android 윷놀이 보드 앱입니다.

## 검증

```powershell
.\gradlew.bat testDebugUnitTest lintDebug
.\gradlew.bat assembleDebug
```

## 자산

- `store-assets/`: 플레이스토어 아이콘 원본과 제작 시안. 앱에는 포함되지 않습니다.
- `app/src/main/res/`: 실제 APK와 AAB에 포함되는 런처 아이콘과 화면 리소스입니다.

## Release 서명

1. `keystore.properties.example`을 `keystore.properties`로 복사합니다.
2. 로컬 업로드 키 경로, 별칭과 비밀번호를 입력합니다.
3. `.\gradlew.bat bundleRelease`로 서명된 AAB를 만듭니다.

`keystore.properties`와 키 파일은 Git에 포함하지 않습니다.
