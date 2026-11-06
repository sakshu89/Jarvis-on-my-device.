# Hands-free "Jarvis"

1. Power JARVIS on (tap the core). Grant the Microphone permission in SETUP.
2. Download `vosk-model-small-en-us-0.15.zip` (~40 MB) from https://alphacephei.com/vosk/models
3. CONFIG -> HANDS-FREE -> switch on -> IMPORT WAKE-WORD MODEL -> pick the zip -> SAVE.
4. Say **"Jarvis"**, wait for the beep, then say the command ("play Kesariya on youtube").

* The wake word runs 100 % on the phone (Vosk, tiny grammar). Nothing is recorded or sent.
* It listens only while JARVIS is ON and idle; it pauses while JARVIS listens/thinks/speaks. "Shutdown" closes the mic completely.
* After a reboot Android does not allow a background mic service to start by itself: open JARVIS once and it resumes listening.
* The command itself is still recognised by Android's speech service (can be online). Fully offline commands are a separate step.
* Battery: continuous listening costs a few % per day; turn the switch off if you do not want it.
