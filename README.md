# V Keyboard

Samsung-keyboard-style Android keyboard (no AI features). Kotlin, zero dependencies.

## Get the APK (no Android Studio needed)
1. Create a GitHub repo and upload everything in this folder (keep `.github/workflows/build.yml`).
2. Actions tab -> "Build APK" -> download the `VKeyboard-apk` artifact when it finishes.
3. Unzip, copy `app-debug.apk` to your phone and install it.

## Enable (Xiaomi / HyperOS)
1. Open the V Keyboard app -> step 1 -> turn the keyboard on. Step 2 -> select it.
2. Settings > Apps > V Keyboard > Battery saver > No restrictions.

## What is inside
- Samsung layout measured from screenshots: number row, QWERTY, symbol pages 1/2 and 2/2, shift / caps lock
- Long-press accents, swipe typing, 3-word suggestions, auto-correct (undo with backspace), learned words
- Emoji panel: full Unicode set (Google glyphs), categories, recents, skin tones, in-keyboard search, text emoticons
- GIF and sticker tabs (Giphy, needs a free API key in Settings)
- Clipboard panel (cards, pin, delete, paste chip), "More" grid, text editing pad, keyboard size, one-handed mode
- Themes: System, Light, Dark, Black, Ocean, Rose, Forest, Lavender, Sunset, Custom colours, background photo
- Key height, corner radius, text size, key borders, haptics, sound, key preview
