# V Keyboard

Samsung-keyboard-style Android keyboard (no AI features). Kotlin, zero dependencies.

## Get the APK (no Android Studio needed)
1. Create a new GitHub repo and upload this whole folder (keep `.github/workflows/build.yml`).
2. Open the **Actions** tab -> "Build APK" -> when it finishes, download the `VKeyboard-apk` artifact.
3. Unzip, copy `app-debug.apk` to your phone and install it.

## Or build in Android Studio
Open this folder, let Gradle sync, then Build > Build APK(s).

## Enable on Xiaomi (HyperOS)
1. Open **V Keyboard** app -> step 1 -> turn the keyboard on.
2. Step 2 -> pick V Keyboard.
3. Settings > Apps > V Keyboard > Battery saver > **No restrictions** (stops HyperOS killing it).

## Features
Samsung layout (number row, symbol pages 1/2 and 2/2, shift / caps-lock), long-press accents and symbols,
swipe typing, word suggestions, auto-correct with undo-on-backspace, learned words, auto-capitalise,
double-space full stop, space-bar cursor swipe, emoji panel, clipboard history, voice typing,
one-handed mode, edit tools (select all / cut / copy / paste), light/dark theme, key height, haptics.
