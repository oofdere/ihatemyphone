# ihatemyphone

I hate my phone. Its volume buttons are dead. This is an Android "digital assistant" app that does
just two things instead of being an assistant:

- **Volume**: a slim pill slides in on the right edge, over a fully transparent window. **Swipe up or
  down anywhere on the screen** and the volume follows your finger, starting from where it already is.
  **Let go and it disappears**, and the phone is back to normal. Tapping anywhere also closes it, and so
  does leaving it alone for 4 seconds.
- **Screenshot**: the button under the pill. The panel hides first, so it isn't in the picture.

It also works from the lock screen, because Android draws assistant overlays above the lock screen.

## Lots of volume steps

Android gives media roughly 15–25 steps. With "Sub-step media volume" turned on (it's on by default)
the slider is basically continuous, with about a thousand usable positions. How it works: the system
volume is set to the step just above where your finger is, and the rest of the gap is made up by turning
down the phone's global audio output with Android's built-in `DynamicsProcessing` effect. The size of each
step in dB comes from your phone's real volume curve (`AudioManager.getStreamVolumeDb`), so the positions
between steps are smooth.

Caveats:
- While a trim is active, other sounds on the same output (notifications, for example) are also a bit
  quieter, by at most one step. Below the first step the trim goes deeper, so they get quieter there too.
- Some phones block global audio effects. In that case the app quietly falls back to normal steps, and the
  setup screen tells you so.
- Tap **MEDIA** at the top of the pill to switch to RING / ALARM / CALL. Those use normal steps. During a
  call the panel opens on call volume.

## Setup (on the phone)

1. Install the APK and open **I Hate My Phone**.
2. **Open assistant settings** and pick *I Hate My Phone* as the *Digital assistant app*.
3. To open the panel, do any of these:
   - swipe diagonally up from a bottom corner (gesture navigation)
   - long-press Home (3-button navigation)
   - set *Press and hold power button → Digital assistant* (usually under Settings → System → Gestures)
4. Screenshots, pick one:
   - **Best:** turn on the *I Hate My Phone screenshots* accessibility service. It subscribes to no events
     and has `canRetrieveWindowContent=false`. All it does is call the system's own "take screenshot" action
     (`ScreenshotService.java` is 40 lines, so you can check). On Android 13+ the switch may be greyed out
     for sideloaded apps. To fix that, go to App info → ⋮ → *Allow restricted settings*.
   - **Fallback:** in the digital assistant settings, turn on *Use text from screen* and *Use screenshot*.
     Android then gives the assistant a screenshot when you open it, and the app saves that screenshot to
     `Pictures/Screenshots`.
5. Hit **Show the panel** in the app to try it.

Side effect: Android also makes the assistant app the default *speech recognizer*, and it won't accept an
assistant that doesn't have one. This app's recognizer is a stub that does nothing. Gboard voice typing is
not affected, because it uses its own recognizer. A few apps that use the system default recognizer won't get
voice input while this is your assistant.

## Dependencies: none

No Gradle, no Maven, no AndroidX, no Kotlin, no libraries, no GitHub Actions from the marketplace. It's plain
Java against the Android framework. The app has one permission, `MODIFY_AUDIO_SETTINGS`, and **no `INTERNET`
permission**, so it can't send anything anywhere.

Building it requires only:
- a JDK (`javac`, `keytool`), `curl`, `unzip`, `zip`
- Google's Android build-tools 36.0.0 + platform 36. `tools/fetch-sdk.sh` downloads these from
  `dl.google.com` and refuses to use them if the SHA-256 doesn't match the value pinned in the script.

```sh
./tools/fetch-sdk.sh   # one time, into ./.sdk
./build.sh             # -> build/ihatemyphone.apk
```

`build.sh` creates a signing key in `keystore/` the first time it runs (that folder is gitignored).
**Keep that key.** Android only installs an update if it's signed with the same key as the installed app.

### Building on GitHub instead

`.github/workflows/build.yml` builds on every push to `main` and publishes the APK as the **latest**
release, so you can download it right on the phone. The workflow uses only shell commands: no `uses:` lines.

To keep the same key across builds (so updates install over each other), add two repository secrets:

```sh
keytool -genkeypair -keystore release.jks -alias app -keyalg RSA -keysize 3072 -validity 36500 -dname CN=ihatemyphone
base64 -w0 release.jks   # -> secret SIGNING_KEYSTORE_B64
                         #    secret SIGNING_KEYSTORE_PASS = the password you chose
```

Without those secrets, every build gets a throwaway key, and you have to uninstall before installing a newer one.

## Code map

| File | What it does |
| --- | --- |
| `AssistService.java` | The assistant service Android keeps running. It holds the fine-volume trim and handles the lock-screen shortcut. |
| `PanelSession.java` | The overlay: pill, stream switch, and screenshot button. Swiping anywhere on screen drives the pill. |
| `SliderView.java` | The pill. Relative drag (on the pill or anywhere on screen), light haptic ticks. |
| `Volume.java` | Stream volume plus the sub-step trim. |
| `ScreenshotService.java` | Optional accessibility service that takes the system screenshot. |
| `Screenshots.java` | Fallback: saves the assistant screenshot to MediaStore. |
| `StubRecognitionService.java` | The speech recognizer Android requires. It does nothing. |
| `SetupActivity.java` | Setup checklist and test button. |

Requires Android 10+.
