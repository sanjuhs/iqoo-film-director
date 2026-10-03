# Mini 4 Pro connection test — first milestone

Prepared 4 October 2026 (IST). **Not run.** SDK support is documentation evidence,
not proof of the user's aircraft, firmware or iQOO compatibility.

User-confirmed inventory: Nothing Phone (3a), RC-N3 and DJI Neo 2; Mini 4 Pro
ownership is previously reported. Firmware, cable and Mini 4 Pro pairing remain
untested. [DJI's compatibility table](https://repair.dji.com/help/content?customId=01700000763&documentType=&lang=en&paperDocType=ARTICLE&re=US&spaceId=17)
lists Neo/Neo 2 as SDK-unsupported and Mini 4 Pro as Mobile SDK supported. Use
Mini 4 Pro here; Neo 2 working in DJI Fly does not pass this probe.

## Verified official interface facts

[MSDK release notes](https://developer.dji.com/doc/mobile-sdk-tutorial/en/index.html)
publish Android **5.18.0**, released **22 May 2026**. Its supported row lists
Mini 4 Pro **01.00.1100**, RC-N2 **01.01.0300**, RC-N3 **01.01.0300**. Treat these
as the published reference firmware row, not proof that arbitrary other firmware
works. The row does not establish RC 2 compatibility.

[Empty-project guide](https://developer.dji.com/doc/mobile-sdk-tutorial/en/quick-start/user-project-caution.html)
recommends Kotlin 2.1.0, Gradle 8.12, Android Gradle Plugin 8.7.0, minSdk 24 and
targetSdk 35; it separately states SDK minimum 23 and ARM64-only support.
Prefer adapting existing tools over installing duplicate toolchains. Android
16/API36 and 16 KB native-page compatibility require their own device/build
verification; recommended target35 is not proof of either.

[Official dependency definitions](https://github.com/dji-sdk/Mobile-SDK-Android-V5#integration):

```text
implementation com.dji:dji-sdk-v5-aircraft:5.18.0
compileOnly   com.dji:dji-sdk-v5-aircraft-provided:5.18.0
runtimeOnly   com.dji:dji-sdk-v5-networkImp:5.18.0
```

[Run-sample guide](https://developer.dji.com/doc/mobile-sdk-tutorial/en/quick-start/run-sample.html)
requires a unique DJI app key and an Android package/applicationId matching its
developer registration. Account/app creation and activation are not completed.
Choose a new research applicationId, record it publicly, inject the associated
key only from ignored local settings into `com.dji.sdk.API_KEY` metadata.
Keep credential-bearing APKs private. Existing AI keys cannot register DJI SDK.

## Bench procedure

1. **Confirm setup:** read exact aircraft and holder-remote labels and firmware;
   record only non-sensitive model/version facts. Use the Mini 4 Pro with a
   supported RC-N2/N3, data-capable phone cable and an unlocked ARM64 Android
   phone. User physically removes propellers before power-on. Screen remote and
   Neo are outside this test. Do not update firmware automatically.
2. **Create the isolated probe:** pre-event source under
   `prototype/dji-connection/`. Build only registration, connection, read-only
   status and preview UI. No takeoff/land/Virtual Stick/waypoint/motor/recording
   or gimbal commands. Measure storage before obtaining packages; no AI model.
3. **Audit and register:** check merged manifest/native packaging and ask for
   normal Android permissions with explanations. Let the user accept USB accessory
   access. Call `SDKManager.init` → initialization-complete callback →
   `registerApp`, log sanitized success/error and actual SDK version. First
   registration normally needs internet; cached offline registration can be
   tested separately later. Use the official
   [SDK lifecycle](https://developer.dji.com/api-reference-v5/android-api/Components/SDKManager/DJISDKManager.html).
4. **Connect:** powered remote/aircraft remains on the bench. DJI Fly and the probe
   must not compete for USB transport; user exits the competing app. Observe
   product callback, product type/firmware and fresh connection/battery values.
   Show unsupported/missing data explicitly; never substitute fake telemetry.
5. **Preview:** discover the available camera index and bind a SurfaceView or
   TextureView through `ICameraStreamManager.putCameraStreamSurface`. A moving
   consented test card in view should visibly change; record first-frame time
   and a short observed stable interval. Remote preview alone needs no Android
   phone-camera capture; verify SDK-specific requirements rather than assuming.
6. **Recover:** unplug phone cable, verify disconnected/stale status and preview
   cleanup, then explicitly reconnect. Test leaving/re-entering the visible
   screen without retaining stale listeners or replaying actions. Finish with
   preview stopped and the user powering down aircraft/remote.

## Pass record

Record source/APK hash, actual SDK/package versions, phone model/OS/ABI/page size,
remote/aircraft versions, permission state, registration result, connection and
telemetry age, first-frame time, preview duration and detach/reconnect outcome.
Keep serials, GPS, app key, account details and private footage out of public logs.
Add result/failures to `docs/status.md`; retain a sanitized reproducible procedure.

Pass requires real Mini 4 Pro identity, fresh battery data, visibly changing live
video and honest disconnected/reconnected states, with zero flight/capture actions.
Bench success does not verify flight, AI review, NPU, Office Kit or a venue demo.
If blocked, preserve the exact failure and proceed with user-imported footage
planning/review; never label that fallback as a connected controller.
