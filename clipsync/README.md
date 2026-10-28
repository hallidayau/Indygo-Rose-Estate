# ClipSync

Copy on one Android phone, paste on the other — over Wi-Fi or mobile data.

## Install
1. Open **Releases → latest** on each phone and download `ClipSync.apk`.
2. Tap it and allow "Install unknown apps" for your browser when asked.
3. Phone A: open ClipSync → **Generate code** → **Save & connect**.
4. Phone B: type the same code → **Save & connect**.
5. On both: tap **Allow running in background** and **Add quick tile**.

## Use
- **Send:** Share → ClipSync from any app (links and text), or copy then tap the *Send clipboard* tile.
- **Receive:** it's put on the clipboard automatically. Tap your keyboard's paste suggestion or long-press → Paste.

## How it works
Clips are encrypted on the phone (AES-256-GCM, key derived from your pairing code) and relayed through the free
[ntfy.sh](https://ntfy.sh) service. The relay only sees scrambled data, on a channel name also derived from the code.
Text only (send photos by message). Relayed items expire from the server within hours.

Android 10+ doesn't let apps read the clipboard in the background, which is why sending is a share or one tap on the tile.
