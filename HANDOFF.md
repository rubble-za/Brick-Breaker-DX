# Brick Breaker (Android) - Handoff

Status as of 2026-10-28: version **0.3.0** (versionCode 3), playtested on one phone (Xiaomi, adb id `195d2d2`). 52 unit tests passing.

## What this is

A modern Android port of the old BlackBerry Brick Breaker. The original BB10 sample source is not public, so this is a **clean-room rebuild** in Kotlin + Jetpack Compose Canvas. Do not use original BlackBerry art, assets or branding.

Requirements so far:
- Scales automatically across phone resolution, aspect ratio and DPI.
- Portrait only.
- The play area is a fixed 360x640 logical field. Extra screen height goes to the top HUD panel (35%) and bottom control panel (65%).
- Bottom control is a fingerprint ring dragged left/right, tied 1:1 to the paddle, with chevrons either side.

## Stack

Kotlin 1.9.24, Compose BOM 2024.06.00 (compiler 1.5.14), AGP 8.5.2, Gradle 8.7, JDK 17, compileSdk/targetSdk 34, minSdk 26. Package/applicationId `com.brickbreaker`.

## Layout

- `app/src/main/java/com/brickbreaker/engine/GameEngine.kt` - pure-Kotlin game engine, no Android deps. 120 Hz fixed timestep, substepped ball movement (no tunnelling), circle-vs-AABB collision. Emits `GameEvent`s via `onEvent`.
- `engine/Levels.kt` - 8 text-grid levels (9 columns; `.` empty, `1`-`3` hit points, `#` unbreakable), looping after level 8.
- `FieldLayout.kt` - maps the 360x640 field to the screen and sizes the top/bottom panels.
- `GameScreen.kt` - Compose UI: HUD, canvas drawing, ring drag handling, pause overlay.
- `audio/ToneSynth.kt`, `audio/SoundBoard.kt` - synthesised PCM tones into static `AudioTrack`s, played on a single-thread executor.
- `Haptics.kt`, `GamePrefs.kt` - haptics (executor-based) and saved high score / sound / vibration toggles.
- Tests in `app/src/test/...`: `GameEngineTest` (42), `ToneSynthTest` (4), `FieldLayoutTest` (5).

## Power-ups

| Letter | Type | Effect | Drop weight |
|---|---|---|---|
| W | WIDE | Paddle 64 -> 100 for 12 s | 3 |
| M | MULTI | Extra balls (max 6) | 3 |
| S | SLOW | Slows the ball | 2 |
| G | GUNS | Single centre gun fires 1 shot/s for 10 s (10 shots); steel absorbs them | 1 |
| C | STICKY | Ball sticks to paddle for 5 catches; tap to release | 3 |
| Y | SPLIT | Next 5 breakable-brick hits split the ball | 3 |
| + | LIFE | +1 life (max 5) | 1 |

Drop chance is 15% per brick (`GameEngine(dropChance = 0.15f)`).

## Building on a new machine

There is no Gradle wrapper in the repo, so install Gradle 8.7 separately.

1. Install JDK 17, the Android SDK (platform 34, build-tools), and Gradle 8.7.
2. Create `local.properties` (gitignored) with `sdk.dir=<path to Android SDK>`.
3. Optionally set `BB_BUILD_ROOT` to a folder outside OneDrive so build output stays out of sync (`app/build.gradle` redirects the build dir to `$BB_BUILD_ROOT/app`).
4. Run `gradle testDebugUnitTest assembleRelease --no-daemon`.

On the desktop this was done with: `JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot`, `ANDROID_HOME=C:\android-sdk`, `BB_BUILD_ROOT=C:\brick-breaker-build`, Gradle at `C:\caligo-chaser-build\gradle-8.7\bin\gradle.bat`.

The release APK is **debug-signed** (sideload only, `minifyEnabled false`). Copy it to `dist/BrickBreaker-<version>.apk` and also to `C:\Users\Russel\OneDrive\AI Projects\QA Testing` (the user's shared test-build folder). Bump `versionName` in `app/build.gradle` for each build.

Install on a phone: `adb -s <id> install -r dist/BrickBreaker-<version>.apk`.

### Releases

Each test build is also published as a GitHub release with the APK attached (APKs are gitignored, so releases are the way to get a build onto another machine without rebuilding). Current: `v0.3.0` at https://github.com/rubble-za/Brick-Breaker-DX/releases/tag/v0.3.0. To publish a new one:

`gh release create v<version> dist/BrickBreaker-<version>.apk --repo rubble-za/Brick-Breaker-DX --title "Brick Breaker <version>" --notes "<notes>"`

To fetch it on the laptop: `gh release download v0.3.0 --repo rubble-za/Brick-Breaker-DX`.

## Gotchas

- **Never key `pointerInput` on objects rebuilt every frame** (e.g. `FieldLayout`). It restarts and cancels every gesture, and the touch area silently stops working. Use `pointerInput(Unit)` plus `rememberUpdatedState`.
- Sound and haptics must stay off the game thread (they caused visible bounce hitches before the executors were added).
- The Xiaomi test phone rejects `adb shell input` (INJECT_EVENTS), so touch cannot be scripted. Screenshots via `adb shell screencap` work. Ask the user to confirm gesture behaviour; don't change the phone's security settings.
- When writing engine tests, a level containing only `#` bricks clears instantly. Use something like `#.......1`.

## Not yet verified

- Layout on other phones (the main reason for the QA Testing builds).
- Feel and balance of GUNS, STICKY and SPLIT (charge counts, drop rates).
- Ring behaviour at the screen extremes.
- Sound/vibration feel of the newer events (SHOOT, STICK, SPLIT).

## Suggested next steps

1. Collect playtest feedback from other phones; fix any layout problems.
2. Tune power-up balance from that feedback.
3. Nothing else is queued. Do not start new features without the user asking.
