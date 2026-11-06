# Code changes in this handoff

This handoff preserves the original project and makes a first safety-focused improvement pass.

## Changed files

- `agent/Tool.kt`: tools can declare that they already implement a specific internal confirmation flow.
- `agent/Agent.kt`: MEDIUM/HIGH-risk tools now request approval before execution unless they own a more specific confirmation flow; permissions and STOP are rechecked after approval; action-log argument summaries redact message/text/secret payloads; system prompt marks external content and memories as untrusted data.
- `agent/PermissionGate.kt`: clarified as the non-interactive gate that runs immediately before tool execution.
- `agent/ToolRegistry.kt`: blank and duplicate tool names are rejected instead of silently replacing an existing tool.
- `automation/Messaging.kt`: message sending explicitly declares its existing recipient/app/message preview as its own confirmation flow; legacy trusted-action bypass is no longer used.
- `automation/Notifier.kt`: confirmation action label is now “Approve” rather than “Send”, so it works for non-message approvals too.
- `accessibility/UiDriver.kt`: click, text entry, gestures and global navigation recheck STOP and blocked foreground apps at the interaction boundary.
- `ui/JarvisViewModel.kt`: approval speech is generic rather than always saying “Message ready”.
- `ui/OtherScreens.kt` and `settings/AppSettings.kt`: removed the unsafe trusted-actions toggle from the settings UI and its setting accessor.
- `README.md`: updated security behavior and known limitations.

## Important limitations

- No Android SDK/Gradle executable was available in the editing environment, so this handoff has **not** been compiled or tested. Run the existing GitHub Actions “Build debug APK” workflow first.
- The approval flow is intentionally conservative: every MEDIUM/HIGH tool call may ask for approval. This can be refined later into task-level approvals with explicit scopes, but should not be weakened before tests exist.
- This does not yet implement screen vision, camera understanding, always-on wake word, semantic memory or a local LLM.
- The existing screenshot tool still saves a screenshot but does not send it to a vision model.
- Android may block or limit background microphone operation; wake-word support needs a platform-compliant design.
