# Home Panel

Turn an old Android phone into a wall-mounted smart-home control panel for
**Home Assistant** — independent of the official app.

![concept](https://m.media-amazon.com/images/I/51MYOf1523L._SL1500_.jpg)

## Features

- **Standalone** — talks directly to Home Assistant's local API; no cloud, no
  official app required.
- **Live control of 4 lights** with on/off and brightness sliders (sliders appear
  automatically for dimmable lights).
- **Temperature & humidity** read from any HA sensors.
- **Always-on display** — fullscreen, immersive, keeps the screen awake.
- **Night mode** — at a configurable time the screen powers fully off (true zero
  backlight via device-admin), and wakes again in the morning. Opening the panel
  at night shows it briefly, then sleeps after 30 s.
- **Auto-rotate** — layout adapts to portrait and landscape.
- **Live updates** over the Home Assistant WebSocket API (state changes appear
  instantly), with automatic reconnect.

## First-time setup

1. **Create a Home Assistant token**
   In Home Assistant: click your user (bottom-left) → *Security* →
   *Long-lived access tokens* → **Create token**. Copy it.

2. **Open the app** → tap the **gear / Open settings**.
   - **Server URL**: `http://10.0.0.5:8123`
   - **Token**: paste the long-lived token
   - Tap **Load entities** — this fetches your lights and sensors.
   - Pick **Light 1–4**, the **Temperature** and **Humidity** sensors from the
     dropdowns.

3. **Night mode**
   - Set the **Sleep at** and **Wake at** times.
   - Tap **Grant** next to *Screen-off permission* and confirm the device-admin
     prompt. This lets the app power the screen off at night.

4. Tap **Save & connect**.

## Build & install

```bash
cd ~/AndroidStudioProjects/HomePanel
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.tigerworkshop.homepanel/.MainActivity
```

## Notes / tuning

- **Cleartext HTTP** to the LAN HA instance is allowed via
  `res/xml/network_security_config.xml`. For an `https://` HA, the app switches to
  `wss://` automatically.
- **Keeping it always in front**: this build is a normal always-on app. To make it
  a locked-down kiosk (boots straight into the panel, hard to exit), it can be set
  as the device HOME launcher and pinned — ask if you want that.
- **True zero backlight** uses Android device-admin `lockNow()`. A finger tap will
  *not* wake it (Android requirement for a locked screen); it wakes on the morning
  schedule or the power button.

## Tech

Kotlin · Jetpack Compose (Material 3) · OkHttp WebSocket · AGP 9.2.0 (built-in
Kotlin) · Gradle 9.4.1 · Kotlin 2.1.20 · minSdk 26 / targetSdk 35.
