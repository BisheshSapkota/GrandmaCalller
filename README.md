# फोन गर्नुहोस् (GrandmaCaller)

A one-button, voice-activated launcher for Messenger calls. Grandma taps the
mic, says a relative's name in Nepali (e.g. "बुबा"), and it opens Messenger
straight to that person's chat so she can start the call with one more tap.

## How it works
1. **Mic button** → Android's built-in speech recognizer (Nepali, `ne-NP`)
   transcribes what she says.
2. **Name matching** → matches the transcript against a list of relatives
   you configure (name, Messenger username, and words she might say for them).
3. **Deep link** → opens `m.me/<username>` directly in Messenger, landing on
   that person's chat.
4. Grandma taps the call icon once Messenger opens. (There's an optional,
   off-by-default accessibility service that tries to auto-tap the call
   button for her — see the warning in `CallTapAccessibilityService.kt`
   before turning it on; it's the most fragile part of this project.)

No AI/LLM involved — just speech-to-text + a lookup table. Runs fully
on-device, no server needed.

## Building the APK (no local Android Studio needed)

This repo includes a GitHub Actions workflow that builds the APK in the
cloud every time you push.

1. Create a new GitHub repo and push this project:
   ```
   cd GrandmaCaller
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<your-username>/<repo-name>.git
   git push -u origin main
   ```
2. Go to your repo on GitHub → **Actions** tab. The "Build APK" workflow
   should run automatically.
3. Once it finishes (green check), click into the workflow run →
   scroll to **Artifacts** → download `GrandmaCaller-debug-apk`. It's a zip
   containing `app-debug.apk`.

## Installing on grandma's phone

1. Transfer `app-debug.apk` to her phone (send it to yourself on
   Messenger/email/Google Drive and download it there, or use a USB cable).
2. Tap the file to install. Android will show an "install from unknown
   sources" warning the first time — this is normal for any app not from
   the Play Store. Allow it.
3. Open the app once, grant microphone permission when prompted.
4. Tap **⚙ Manage Relatives** and add each person:
   - **Name** — just for your reference (e.g. "Dad")
   - **Messenger username** — the part after `m.me/` in their profile link
     (open their Messenger profile → share/copy link to find this)
   - **Words she might say** — comma-separated, e.g. `बुबा, बा`

## Known limitations / things to test before relying on this
- Messenger's deep link (`m.me/username`) opens the chat, not the call
  directly — there's no public API for the latter. Confirm the one-extra-tap
  flow feels okay for her.
- On-device Nepali speech recognition quality varies by phone/Android
  version. Test recognition accuracy with her actual phone before finishing
  setup — you may need to adjust the `spokenNames` list to match how the
  recognizer transcribes her voice/accent.
- `messengerId` must be a real Messenger username (not just a display name)
  for the deep link to resolve correctly.
