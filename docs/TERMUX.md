# Working on JARVIS from Termux

## 1. One-time setup (exact commands)

```bash
pkg update -y && pkg upgrade -y
pkg install -y git openssh unzip
termux-setup-storage            # allow storage so you can reach Downloads
```

Git identity and GitHub login (use a **fine-grained personal access token** with *Contents: read/write* on your repo only):

```bash
git config --global user.name  "Your Name"
git config --global user.email "you@example.com"
git config --global credential.helper store
# the first `git push` asks for username + token (paste the token as the password)
```

## 2. Get the project into Termux

Download `jarvis-android.zip` to your phone, then:

```bash
cd ~
unzip ~/storage/downloads/jarvis-android.zip
cd jarvis-android
```

## 3. Push and build in the cloud (recommended)

```bash
git init -b main
git add .
git commit -m "JARVIS: initial import"
git remote add origin https://github.com/<you>/jarvis-android.git
git push -u origin main
```
Then GitHub -> Actions -> *Build debug APK* -> download **jarvis-debug-apk**. Install the `.apk`.

After you change code: `git add . && git commit -m "fix" && git push` and download the new APK.

## 4. Fixing build errors
Open the failed run, expand *Build debug APK*, copy the lines starting with `e: file:///...` and send them back.
Typical first-build issues: a missing import, a changed Compose API name, a version number.

## 5. Local Gradle build in Termux (experimental, unverified)

Why it is hard: the official Android Gradle Plugin downloads `aapt2` for desktop CPUs, which does not run on Termux,
and there is no official Android SDK package for Termux. A community-style workaround that *may* work:

```bash
pkg install -y openjdk-17 gradle aapt2
# tell Gradle to use Termux's aapt2
mkdir -p ~/.gradle
echo "android.aapt2FromMavenOverride=$PREFIX/bin/aapt2" >> ~/.gradle/gradle.properties
echo "org.gradle.jvmargs=-Xmx1g" >> ~/.gradle/gradle.properties
export ANDROID_HOME=$HOME/android-sdk     # you must install platform android-34 + build-tools here yourself
echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle :app:assembleDebug --no-daemon
```
You must supply a working Android SDK (platform 34, build-tools) yourself; phones with < 6 GB RAM will often run out of memory.
If any step fails, use the GitHub Actions route above - it is the supported path.

## 6. Install the APK
Tap the downloaded `.apk` -> allow *Install unknown apps* for the app you opened it from -> Install.
If Play Protect warns about an unsigned/debug app, choose *Install anyway* (it is your own build).

## 7. API keys
Do **not** put keys in files or git. Paste them in the app: CONFIG -> provider -> API key -> SAVE.

## 8. Troubleshooting
| Problem | Fix |
|---|---|
| `Permission denied (publickey)` / push asks login | Use the https remote URL and a token as the password |
| Actions: `SDK location not found` | Not needed on GitHub runners; do not commit `local.properties` |
| Actions: `Unsupported class file major version` | Workflow already uses JDK 17; do not change it |
| Accessibility switch greyed out | App info -> ⋮ -> Allow restricted settings |
| Mic button says speech service missing | Install/update the Google app or Speech Services |
| App closes in background on 2 GB phone | Battery -> Unrestricted for JARVIS; keep Accessibility enabled |
