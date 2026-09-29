TRANSIVA CUSTOMER - MINIMAL PLAY STORE FIX
=========================================

Replace ONLY:
  app/src/play/AndroidManifest.xml

Do NOT replace src/main and do NOT change the direct APK flavor.

What this patch does for the Google Play flavor only:
- removes AD_ID / AdServices identifiers;
- removes REQUEST_INSTALL_PACKAGES;
- removes READ_MEDIA_IMAGES / READ_MEDIA_VIDEO;
- removes legacy READ/WRITE_EXTERNAL_STORAGE.

Why photo/file features remain functional:
The audited Customer source already uses ACTION_OPEN_DOCUMENT / ACTION_GET_CONTENT
for gallery/file selection and app-owned camera URIs. No Java runtime request for the
removed media/storage permissions was found.

Intentionally NOT changed:
- order lifecycle/API/backend;
- Split Pay/wallet;
- Google login;
- Maps/navigation;
- FCM notifications;
- WebRTC incoming calls and USE_FULL_SCREEN_INTENT;
- Trans Asisten overlay;
- TripGuardian/Redispatch/AudioProtect foreground services;
- direct APK self-update behavior.

Before Production:
1. Build the `playRelease` AAB, not `directRelease`.
2. Inspect the merged Play manifest and confirm the removed permissions are absent.
3. Run existing regression/unit tests.
4. Test photo picker, profile image, chat attachment, top-up proof, camera, incoming call,
   active trip/redispatch, and Audio Protect on the release build.
5. Complete Play Console declarations for foreground services/full-screen intent where requested.
6. Restrict the Google Maps key to package com.transiva.customer + the Play signing certificate.

NOTE:
The submitted project ZIP does not contain gradlew / gradle/wrapper, so a clean command-line
release build cannot be reproduced from that ZIP alone. Restore the project's normal Gradle
Wrapper from the canonical repository before CI/release validation; it is intentionally not
fabricated by this patch.
