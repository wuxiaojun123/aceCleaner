# Floating ball library

Android source module (minSdk 24) adapted from CleanBoxPro's `ifloat` integration.
The `com.gg:float-sdk:1.0.1` AAR was inspected with `javap -p -c`; the WindowManager,
drag, edge-snap and linked-tip implementation here is rewritten Kotlin source, not
a bundled/decompiled binary dependency. Layouts, images and Lottie assets retain
the reference project's speed-test UI. The ball and bottom panel now use the host
app's orange / warm-white palette and a native, lifecycle-aware orbital animation.
Ensure you have redistribution rights to the retained reference assets.

## Integration

Include `:library_float` in settings and add `implementation(project(":library_float"))`.
Use the Android library and Kotlin plugins and repositories from the host project.
Initialize once from Application.onCreate on the main thread:

```kotlin
FloatingBall.initialize(application, host, debugTiming = BuildConfig.DEBUG)
```

Implement `FloatingBallHost.openRoute` to map the four `FloatRoute` destinations
to the host application's screens. Supply `interceptedToday` for notification
counts, `reportEvent` for analytics and `log` through your application's LogUtils.
`onBeforeExternalEntry` suppresses duplicate hot-start navigation. Optional
`preloadNetworkResult` and `bindNetworkResultAd` hooks keep ad SDK dependencies
outside this module; the default host integration leaves the result ad slot empty.

The merged manifest supplies overlay and network permissions. The host must
request overlay access; the library never launches a permission dialog on its own.
Image/download monitoring uses existing grants only. It does not request storage
or notification-listener access. Permission reminders route to host feature pages.

`FloatingBall.setEnabled(false)` persists opt-out and releases all windows and
background work; `setEnabled(true)` applies at the next background transition.
All public UI APIs must be called on the main thread. ProcessLifecycleOwner hides
the ball while the app is visible and starts a background session when it stops.
There is deliberately no foreground service: Android may kill the background
process and remove its windows. No battery-restriction bypass is implemented.

## Reference behavior

- Compact 40dp memory-ball window with explicit WindowManager dimensions; drag,
  click and animated edge snapping.
- Full-screen overlay backgrounds retain screen coverage while foreground
  content uses stable system-bar and display-cutout insets, including three-button
  and side-mounted navigation bars. Do not add a second navigation-bar spacer.
- Full-screen bottom menu, live traffic rates, linked six-second reminder bubble.
- First permission check at 65 seconds (45 debug); reminders cool down for three
  minutes (one debug).
- Priority: missing access, new download, new image, more than three intercepted
  notifications, RAM at least 80%, then network-speed test.
- User-initiated real download/upload test and the reference result card.
  The test transfers network data; it does not run automatically from a reminder.
- Floating UI copy is English, as in the reference default resources. The reference
  locale files contain no floating UI translations; none are fabricated here.

Modify `src/main/res/layout/view_float_*.xml`, `src/main/res/mipmap-*`,
`src/main/res/drawable` and `src/main/assets/anim` to restyle the UI later.

## Validation

Run `python3 tools/check_string_resources.py`, `sh gradlew :app:checkLocalization`,
`:library_float:testDebugUnitTest`, `:app:compileDebugKotlin`, then
`:app:activityGuardReleaseClassTask` and
`python3 tools/check_obfuscated_class_access.py` from the host repository.
Device coverage must include drag versus click, both screen edges, foreground /
background, rejected/revoked overlay permission, opt-out, network cancellation,
rotation, system navigation insets and release feature routing. Compilation and
JVM visibility checks do not replace final DEX inspection or device regression.

On Windows with a `python` installation but no `python3` executable, pass
`-PpythonCommand=python` to Gradle; this still runs the same resource checker.
