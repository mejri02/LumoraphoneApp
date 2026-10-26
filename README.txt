LUMORA PLAYER (native Android) - build in the cloud, no Android Studio needed

1. Create a free account at github.com and a NEW repository (private is fine).
2. Upload ALL files of this folder to the repository (keep the .github folder).
   (Easiest: on the repo page choose "Add file > Upload files" and drag the contents of the LumoraNative folder.)
3. (Optional, to sign with your own key so it can update your old app)
   Repo > Settings > Secrets and variables > Actions > New repository secret:
     KEYSTORE_B64      = base64 text of lumora-signing-key.p12
     KEYSTORE_PASSWORD = lumora
     KEY_ALIAS         = lumora
   Without these secrets the APK is signed with a debug key (still installs fine).
4. Open the Actions tab > "Build APK" > wait ~5 minutes > open the run > download "Lumora-Player-apk".
5. Unzip it and install the APK. It installs next to your old app (package com.lumora.player).

If the build fails, open the failed run, copy the red error lines and send them to me.

Pairing: TV home > "Link phone" shows the TV ID. Phone: cast icon > type the code > Link.

ANDROID v1.3.0 / TV v3.9.0
- TV: Settings > Phone > "Pair with code" opens the link screen (code + QR).
- Android: cast icon > switch "Play on TV when it is connected" (off = everything plays on the phone; the player still has a manual Cast button).
- Android: "Pair with QR code" scans the QR shown on the TV (camera permission requested on first use).
- Android: the remote is now a separate button (remote icon in the top bar, visible when a TV is linked).
