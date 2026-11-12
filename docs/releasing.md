# Release checklist

1. Increase `versionCode` and set `versionName` in `app/build.gradle.kts`. Update [CHANGELOG.md](../CHANGELOG.md).
2. Use JDK 21 and build with `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:bundleRelease`.
3. Run `./gradlew :app:connectedDebugAndroidTest` with an Android emulator or device. Exercise camera permission, a physical barcode, manual entry, language switching, missing nutrition values and offline history. Check the release on a 16 KB device when one is available.
4. Verify both packages with `python3 scripts/check-native-alignment.py app/build/outputs/apk/release/app-release-unsigned.apk app/build/outputs/bundle/release/app-release.aab`. Check APK ZIP alignment with `zipalign -c -P 16 4 app-release-unsigned.apk`.
5. Sign the APK with the existing BarcodeScanner release key and verify it with `apksigner verify --verbose --print-certs BarcodeScanner-vX.Y.Z.apk`. Keep the keystore and password in secure backups outside the repository. Future updates for GitHub installs must use the same signing key.
6. After CI passes, tag the verified commit as `vX.Y.Z` and publish the GitHub Release with the signed APK, a SHA-256 checksum and release notes. The AAB is a separate build artifact for distribution through Play Console.

[Android signing guide](https://developer.android.com/studio/publish/app-signing) · [Android 16 KB verification](https://developer.android.com/guide/practices/page-sizes)
