# J.A.R.V.I.S. 3.0 — engineering roadmap

This roadmap is an engineering target, not a claim that every Android capability is already implemented. Android's permission model, foreground-service rules, OEM restrictions, and other apps' security boundaries mean no third-party assistant can literally control everything.

## Delivered in this safety-pass update

- Tool registry rejects invalid names, duplicate registrations, and unusable descriptions instead of silently replacing an existing tool.
- Added a bounded 12-step agent loop (up from 8); repetition detection and the emergency stop remain active.
- Added `analyze_screen`, which captures the current screen and sends it to the configured image-capable provider only after the existing high-risk approval flow.
- Added image-input implementations for Gemini and OpenAI-compatible chat-completions providers. A selected endpoint/model must support vision.
- Screen analysis treats visible content as untrusted data, limits image size to 8 MB, and deletes the temporary screenshot after analysis.
- The agent prompt now directs JARVIS to verify task outcomes rather than treating a tap/type result as proof of success.

## Phase A — build and reliability gate

1. Run the GitHub Actions `Build debug APK` workflow.
2. Fix all compiler, lint, and manifest warnings that affect behavior.
3. Add unit tests for plan parsing, duplicate tool registration, risk classification, argument validation, and cancellation.
4. Add instrumentation tests for permission setup, STOP, screenshot denial, and app foreground changes.
5. Test on Android 13, 14, 15, and 16 where available, including the target OEM device.

Exit criteria: clean CI build; no crash in a 30-minute manual session; STOP cancels pending and subsequent actions; no tool can bypass the registry or permission gate.

## Phase B — grounded screen understanding

1. Combine screenshot analysis with the accessibility node tree and active package name.
2. Add freshness timestamps so the planner cannot act on stale screen observations.
3. Require a fresh observation before coordinate taps and after navigation.
4. Add confidence-aware behavior: ask the user when a target is ambiguous.
5. Add image redaction/exclusion controls and a clear indicator whenever screen content leaves the device.

## Phase C — durable agent runtime

1. Persist task plans, step results, and checkpoints without storing secrets or unnecessary screen contents.
2. Add explicit task states: queued, running, waiting-for-approval, paused, completed, failed, cancelled.
3. Add bounded retries, per-tool timeouts, idempotency keys, and recovery after process death.
4. Add per-task cancellation and a global emergency stop.
5. Keep autonomous execution limited to user-approved capabilities and risk policies.

## Phase D — voice and local intelligence

1. Add a foreground voice-session service with visible mic state and user-controlled stop.
2. Investigate wake-word operation within Android foreground-microphone restrictions and OEM battery behavior.
3. Keep basic intents local; use cloud models for complex planning and vision.
4. Evaluate an optional on-device model based on RAM, thermal load, storage, and latency.
5. Do not promise offline speech or reasoning unless the selected device/model actually supports it.

## Phase E — useful Android integrations

Prioritize supported public Android APIs and explicit user-granted permissions: alarms/reminders, calendar intents, contacts (optional), share sheet, media controls, notifications, document picker, and app launching. Avoid accessibility scraping where a public API or app integration exists.

## Risk policy

- Low: reversible, local, read-only actions.
- Medium: UI navigation, typing drafts, changing ordinary preferences; require approval according to the user's configured policy.
- High: sending, posting, deleting, sharing, purchasing, or transmitting private screen content; require a clear, action-specific approval immediately before execution.
- Prohibited: bypassing device security, stealing credentials, reading OTPs, or performing banking/payment actions.

Approval must identify the exact target and consequence. Silence, past approvals, or model-generated text never count as consent.

## Known platform limits

- Accessibility cannot reliably control every app or protected screen.
- Android restricts background microphone use; always-on wake words are not guaranteed.
- Some OEMs aggressively stop background work.
- Banking, DRM, password, and other secure surfaces may intentionally block screenshots/accessibility.
- A third-party app cannot silently grant itself system permissions or bypass another app's confirmation flows.
- Vision requests send screen content to the configured cloud provider; the user must understand and approve that transfer.
