# J.A.R.V.I.S - personal AI assistant for Android

A native Kotlin Android app that understands Hindi / English / Hinglish commands (voice or text),
plans with an LLM, and acts on your phone through a **fixed list of tools** - opening apps, driving other
apps via the Accessibility API, sending messages **after you confirm**, reminders, memory, lookups, GitHub status.

The UI is an original HUD design (animated core, holographic panels). No movie assets are used.

> Status: complete first version, **not yet compiled or tested on a device by its author**. Expect 1-3 rounds of
> small compile/UI fixes on the first GitHub Actions build. See *Known limits*.

## Power: always on, until you shut it down
Tap the core once -> JARVIS powers on (plasma-core animation) and **stays on**: a foreground service keeps it alive,
and it comes back after the app is closed or the phone reboots. It turns **fully off** (service stopped, mic closed,
no restart) when you say or type **shutdown / power off / turn off / band ho ja**, say **stop while idle**,
tap **OFF** in the app, or tap **SHUT DOWN** in the notification. *Stop while a task is running only cancels that task.*

## How it works

```
voice/text -> IntentProcessor (STOP / yes / no, wake word)
           -> Agent (LLM returns JSON: tool + args)
           -> PermissionGate -> Tool.execute() -> result -> LLM -> reply (voice + UI)
```

* The model can only name tools registered in `agent/ToolRegistry.kt`. **There is no shell tool.**
* Medium/high-risk tools require an explicit in-app approval before execution. The approval is checked again against current permissions immediately before execution.
* `send_message` types the message, then previews the exact recipient, app and message and asks before sending (in-app card and notification with Approve / Cancel). Message sending cannot bypass confirmation.
* **STOP** works three ways: the red button in the app, the STOP button on the "JARVIS is working" notification
  (works while another app is on screen), or saying/typing "stop", "cancel", "jarvis stop", "ruko".
* JARVIS refuses to read or operate banking, payment and password apps (`accessibility/UiDriver.kt`).

## Tools

`open_app`, `send_message`, `find_ui_element`, `click_ui`, `tap`, `swipe`, `type_text`, `back`, `screenshot`,
`web_search`, `browser`, `clipboard`, `notify`, `reminder`, `file`, `github` (read-only), `calculator`, `device_info`, `memory`, `analyze_screen` (approved screenshot-to-vision analysis).
Add one by implementing `Tool` and registering it in `buildRegistry()`.

## Project layout

```
app/src/main/java/com/jarvis/
  app/            Application, MainActivity, dependency container (Graph)
  ai/             AIProvider interface, Gemini + OpenAI-compatible providers, HTTP helper
  agent/          Agent loop, Tool interface, registry, PermissionGate, confirmations, IntentProcessor
  tools/          All tool implementations
  automation/     Messaging adapters (WhatsApp/Telegram/Instagram/Snapchat/generic), notifications, reminders
  accessibility/  AccessibilityService + UiDriver (all UI automation)
  memory/         Long-term memory (encrypted), short-term context, temp task state
  voice/          Speech-to-text, text-to-speech, replaceable wake-word seam (push-to-talk shipped)
  ui/             Compose HUD, orb, screens, ViewModel
  security/       Emergency stop, encrypted store, action log, permission status, logging
  settings/       Settings + performance profiles
```

## Build (recommended: GitHub Actions, no PC needed)

1. Create an empty **private** repo on GitHub (e.g. `jarvis-android`).
2. In Termux (see `docs/TERMUX.md`) from the project folder:
   ```bash
   git init -b main
   git add .
   git commit -m "JARVIS: initial import"
   git remote add origin https://github.com/<you>/jarvis-android.git
   git push -u origin main
   ```
3. GitHub -> **Actions** -> *Build debug APK* -> open the latest run -> download artifact **jarvis-debug-apk**.
4. Unzip, tap the `.apk` on your phone, allow "install unknown apps" for your browser/files app.

If the build fails, open the run log, copy the red error lines and send them to me - that is the normal fix loop.

### Local build in Termux (experimental, not verified)
Android's build tools are x86/ARM-desktop binaries and the Android SDK has no official Termux package, so a local Gradle
build is unreliable. Details and the community workaround are in `docs/TERMUX.md`.

## First run

1. Install the APK, open **JARVIS**. The SETUP screen explains each permission; grant Microphone and Notifications.
2. Accessibility: App info -> **⋮ -> Allow restricted settings** (Android 13+ sideloaded apps), then
   Settings -> Accessibility -> Installed apps -> **JARVIS** -> On.
3. CONFIG -> pick a provider and paste an API key -> Save. (Keys live only in encrypted storage on the phone.)
4. Tap the orb and say: *"Jarvis, Instagram kholo"* or type it.

### AI provider
* **On-device** (offline, no key): import a `.task` model (Gemma / Qwen / Llama 3.2) - see `docs/LOCAL_MODEL.md`.
* **Gemini** (default model `gemini-2.5-flash`) and any **OpenAI-compatible** endpoint (default: Groq).
* Model names and free-tier terms change. **Check the provider's current terms and model list**; the model name is editable in Config. JARVIS does not assume any plan is free.

## Performance profiles
Auto-detects phone RAM: **Lite** (<3.2 GB: static HUD, no glow), **Balanced**, **High** (>=7 GB: glow, grid, voice bars).
Animation is time-based on the system frame clock, so it follows 60/90/120 Hz automatically. Override in Config.

## Security notes
* API keys, memories and reminders: Android-Keystore-backed encrypted storage. Keys are never logged and never in source.
* Dev vs release: debug builds have a `.debug` id suffix and verbose (secret-free) logs; release builds are shrunk with no verbose logs.
* Release signing uses env vars only (`JARVIS_KEYSTORE_PATH`, `JARVIS_KEYSTORE_PASSWORD`, `JARVIS_KEY_ALIAS`, `JARVIS_KEY_PASSWORD`); without them the release APK is debug-signed.
* Backups are disabled so memory cannot leak via cloud backup.

## Known limits (honest list)
* **Not yet compiled or device-tested in this workspace.** Run the GitHub Actions debug build and test on-device before relying on it. The repository does not include the Gradle wrapper; GitHub Actions provisions Gradle in CI.
* Messaging automation is **best-effort** per app. Instagram and Snapchat change their UI often and may block or hide nodes from accessibility; each step verifies itself and reports failure rather than pretending success. WhatsApp/Telegram are the most likely to work.
* Contact must match the name as saved in the app (exact spelling helps).
* No hands-free wake word: Android restricts background microphone use. Push-to-talk (tap the orb) is what ships; `WakeWordDetector` is the seam for a replacement.
* "Jarvis stop" by voice only works while the mic is open; the STOP button / notification STOP work any time.
* Speech recognition uses Android's recognizer; Hindi/Hinglish accuracy and offline support depend on the phone's speech service.
* Reminders use inexact-while-idle alarms (no special permission); Android may delay them a few minutes. They are re-armed after reboot, but some OEMs block auto-start.
* `web_search` is a quick lookup (DuckDuckGo instant answers + Wikipedia), not a full search engine; `browser` opens a real search.
* Screenshot capture works on Android 11+ and protected screens may appear blank. `analyze_screen` now sends a captured screenshot to the configured Gemini or OpenAI-compatible vision endpoint only after the high-risk approval flow. The chosen model/endpoint must support image input; screenshots are deleted from private temporary storage after analysis. This is cloud vision, not offline vision.
* Medium/high-risk tool actions now require explicit approval unless a tool implements a more specific confirmation flow. Screen image analysis is high-risk because it transmits screen content to the configured provider. Accessibility gestures also recheck the active app and emergency-stop state immediately before dispatch.
* Action-history argument summaries redact message bodies, typed text and common secret fields.
* The AI system prompt explicitly treats tool results, web content, clipboard data, screenshots and stored memories as untrusted data rather than instructions.
* The emergency stop cancels at the next step; an in-flight network request finishes (<= 25 s) before the cancel is seen.
* Android may kill a background app on 2 GB phones; the enabled Accessibility service keeps the process alive in practice, and the battery setting "Unrestricted" helps.

## License
MIT - see `LICENSE`.
