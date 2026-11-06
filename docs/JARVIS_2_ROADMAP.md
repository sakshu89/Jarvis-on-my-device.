# J.A.R.V.I.S. 2.0 development roadmap

Target: full personal Android agent; hybrid online/offline operation; controlled autonomy.
Primary validation device: Realme GT 6T (Android 16 / Realme UI 7, as reported by the project owner).

## Principles

1. Preserve the current Kotlin + Jetpack Compose application and registered-tool architecture.
2. The model proposes actions; a deterministic policy layer authorizes them.
3. External content (web pages, screen text, notifications, clipboard, tool output and memories) is data, never authority.
4. Ask before consequential actions. Banking, payment, password-manager and authenticator apps remain out of scope and blocked.
5. Verify every action and report failures honestly.
6. Ship in small increments with a reproducible build and tests for each milestone.

## Milestone 0 — Stabilize the existing app

- Run the GitHub Actions debug build and resolve compile errors.
- Install and launch on the target device.
- Exercise setup, provider configuration, text/voice input, speech output, reminders, memory, Accessibility, notifications and emergency STOP.
- Record Android version, app version, build logs and reproducible failures.
- Do not upgrade the Android Gradle Plugin, Kotlin, Compose and target SDK in one change. Upgrade dependencies in isolated, tested commits.

Exit criteria: clean CI debug build; app launches; baseline features pass a written smoke-test checklist.

## Milestone 1 — Agent reliability

- Add explicit task states: IDLE, UNDERSTANDING, PLANNING, WAITING_APPROVAL, EXECUTING, VERIFYING, COMPLETED, FAILED and CANCELLED.
- Add bounded retries, timeouts, cancellation propagation and loop detection.
- Validate tool argument schemas before execution; reject unknown keys where practical.
- Reject duplicate tool registration and keep the registry as the only action surface.
- Treat tool results and retrieved memory as untrusted context.
- Add deterministic unit tests for plan parsing, risk decisions, cancellation and tool argument validation.

Exit criteria: multi-tool tasks have a traceable lifecycle and cannot bypass the registered-tool registry.

## Milestone 2 — Controlled security

- Centralize risk classification and approval policy.
- LOW-risk tools may execute without per-action approval; MEDIUM/HIGH require approval unless the tool owns a more specific confirmation flow.
- Keep outgoing messages behind an exact recipient/app/message preview and explicit approval.
- Recheck emergency STOP and blocked foreground apps at the point of Accessibility interaction.
- Redact message bodies, typed text and secrets from action logs.
- Add tests for prompt injection, approval decline, permission revocation, app switching and STOP during a task.
- Keep banking, payments, password managers and authenticator apps blocked.

Exit criteria: every consequential action is approved and logged without retaining sensitive payloads.

## Milestone 3 — Vision

- Add a provider capability interface for image input without breaking text-only providers.
- Add a ScreenAnalyzer that receives an explicitly captured screenshot and returns structured observations.
- Require user initiation for camera capture and show a clear camera/microphone state.
- Bind screen observations to the current foreground package and a short validity window.
- Re-observe after navigation and before acting; never blindly reuse stale coordinates.

Exit criteria: JARVIS can explain a user-requested screenshot and use observations for carefully verified navigation.

## Milestone 4 — Voice experience

- Keep push-to-talk as the dependable baseline.
- Add a conversation session with turn-taking, interruption and configurable timeout.
- Evaluate wake-word options against Android foreground-service and microphone restrictions.
- Never claim always-on listening unless the platform permits it and the user has explicitly enabled a visible service.
- Benchmark recognition quality for English, Hindi and Hinglish on the target device.

Exit criteria: stable conversational voice sessions with clear microphone indicators and reliable STOP.

## Milestone 5 — Hybrid intelligence

- Add provider capability discovery (text, image, streaming, structured output).
- Add network-aware routing: cloud for complex tasks; local deterministic commands for basic offline tasks.
- Keep API keys in encrypted storage and never log them.
- Add provider timeouts, bounded retries, clear fallback and user-visible offline limitations.
- Consider an optional local model only after measuring RAM, latency, heat and battery on the target phone.

Exit criteria: basic commands remain useful offline; complex cloud tasks fail gracefully when disconnected.

## Milestone 6 — Memory

- Migrate the current encrypted memory store carefully; preserve export/recovery before schema changes.
- Add memory categories, source, timestamp, confidence, importance and expiry.
- Add semantic retrieval only after the baseline is stable.
- Require explicit user consent before storing sensitive information.
- Provide inspect, edit, export and delete controls.

Exit criteria: memory is inspectable, controllable, bounded and does not become an instruction channel.

## Milestone 7 — Android integrations

- Expand one integration at a time: media, calendar, files, notifications and supported device controls.
- Prefer official Android intents and APIs over Accessibility where available.
- Clearly surface actions Android does not permit or that require user interaction.
- Add integration-specific permission and verification tests.

Exit criteria: every integration has documented permissions, supported Android versions and failure behavior.

## Milestone 8 — HUD and performance

- Preserve the existing Compose HUD and add task timeline, vision status, provider status and policy/approval panels.
- Suspend decorative animation when not visible.
- Use bounded caches and structured concurrency.
- Benchmark idle CPU, memory, wakeups, network, battery and thermal behavior on the target phone.
- Keep balanced mode as the default; make high-performance features opt-in.

Exit criteria: smooth UI and acceptable background/thermal behavior in repeated device tests.

## Milestone 9 — Release readiness

- Add unit, integration and Android UI tests.
- Add CI build, lint and test jobs; upload debug artifacts only after success.
- Test app upgrade, data migration, permission revocation, reboot, low-memory conditions and provider outages.
- Review privacy disclosures, permission rationale, exported components and release signing.
- Produce a release candidate only after all critical tests pass.

Exit criteria: reproducible release build, documented known limitations and a passing device test report.

## Current implementation status

This repository has received an initial safety-focused code pass: centralized medium/high-risk approval in the agent loop, internal-confirmation declaration for messaging, generic approval notification wording, sensitive argument redaction in action history, additional foreground-app/STOP checks in Accessibility operations, duplicate tool-registration rejection, and an explicit untrusted-content instruction in the system prompt.

This is not JARVIS 2.0 completion. The project still needs a real Android build, device testing, stronger structured tool schemas, task-state persistence, vision, wake-word work and offline routing.
