# Contributing

* Keep dependencies minimal and Termux/GitHub-Actions friendly.
* New capability = implement `Tool` (`agent/Tool.kt`), register it in `agent/ToolRegistry.kt`, set `risk` and `requiresAccessibility` honestly.
* Anything that sends data out, spends money or changes another app's state must ask for confirmation via `ConfirmationManager`.
* Never add: shell execution tools, bypasses of Android security/permissions, banking/payment automation, or logging of keys/message text.
* Never commit secrets (`*.jks`, `.env`, tokens). Keys are entered in the app only.
* Open an issue first for large changes. Include Android version, phone model and the app being automated for bugs.
