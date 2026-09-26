# Yutnori

## Recovery status

This branch contains a **buildable reconstruction of version 1.0.0 (build 38)** from the owner's backed-up debug APK. It is not the original source tree, and it is not yet a reconstruction of the later 1.3.2 (build 46) release. The 1.3.2 AAB and its bundled R8/ProGuard map are being used as references for the remaining work.

The original application ID is `com.das312.yutnori`; Java classes remain in `com.example.yutnoriapp`. Reconstructed code was decompiled with JADX and repaired where the decompiler produced invalid Java. UI resources were extracted from build 38. Decompiled identifiers, comments and formatting may differ from the original source.

Build with an installed Android SDK (`ANDROID_HOME` or `local.properties` must point to it):

```powershell
.\gradlew.bat :app:assembleDebug
```

The `work/` directory is intentionally ignored; it contains local recovery tools, extracted binaries and test screenshots, not project source. Do not publish a release or upload to Play Console from this reconstruction without functional regression tests, signing checks and a version-code decision.
