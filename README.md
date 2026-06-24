# Android Home Assistant Wall Panel

> Give an old Android phone a second life as a beautiful, always-on smart-home
> wall panel for **Home Assistant** — no cloud, no official app, just your local
> network.

A self-contained Kotlin + Jetpack Compose app that turns any spare Android
device into a glanceable control surface: tap tiles to run your lights, switches,
scenes and automations; watch live temperature, humidity and weather; and let it
fade to black at night and wake itself in the morning. Everything talks directly
to Home Assistant over your LAN.

---

## Highlights

- **Standalone & local** — connects straight to Home Assistant's WebSocket/REST
  API. No cloud round-trips, no companion app, live state updates with automatic
  reconnect.
- **Configurable tiles, unlimited** — add as many tiles as you like, reorder
  them, and give each a custom name. Supports:
  - **Lights & switches** — tap to toggle; dimmable lights get a vertical
    brightness bar, plus a **long-press full-screen brightness slider**.
  - **Smart plugs** exposed as `switch.*` (e.g. a plug driving a lamp).
  - **Actions** — `automation`, `scene` and `script` tiles that **trigger** on
    tap (with a quick confirmation flash).
- **Beautiful, dynamic UI**
  - **Dynamic weather background** — the panel gradient shifts with the current
    conditions and time of day.
  - **Animated weather icons** — soft, original Canvas glyphs (rotating sun,
    drifting clouds, falling rain/snow, flashing storm, crescent moon). Toggle on/off.
  - **Live clock**, indoor temperature & humidity, and a translucent "glass" tile
    look over the gradient.
- **Weather & forecast** (any HA `weather.*` entity, e.g. Met.no)
  - Current conditions in the header, plus an optional multi-day **forecast strip**.
  - **Tap the weather** for a full forecast overlay: current details, air
    pressure / humidity / wind, and **Daily / Hourly** tabs.
- **Always-on + night mode**
  - Fullscreen, immersive, keeps the screen awake.
  - At a configurable time the screen powers **fully off** (true zero backlight
    via device-admin) and wakes again in the morning.
- **Adapts to your device**
  - **Auto-rotate** with separate column counts for **portrait** and **landscape**.
  - Easy on-device setup: **autocomplete entity search** and **QR-code scanning**
    for the access token.

---

## First-time setup

1. **Create a Home Assistant token**
   In Home Assistant: open your user profile → *Security* →
   *Long-lived access tokens* → **Create token**. (Home Assistant shows it as a
   QR code, which you can scan in the next step.)

2. **Open the app** → tap the **gear** (bottom-right) to open **Settings**.
   - **Server URL** — e.g. `http://10.0.0.5:8123`
   - **Token** — paste it, or tap the **QR icon** to scan the code from Home
     Assistant.
   - Tap **Load entities** to fetch everything available.

3. **Add tiles** (the *Tiles* section)
   - Tap **Add tile**, search for an entity (light, switch, automation, scene,
     script…), optionally set a **custom name**, and use the arrows to reorder.

4. **Climate & Weather** (optional)
   - Pick **Temperature** and **Humidity** sensors.
   - Pick a **Weather** entity, then toggle **Dynamic background**, **Show
     forecast** and **Animations** to taste.

5. **Layout** — set the number of **columns** for portrait and landscape.

6. **Night mode** (optional)
   - Set **Sleep at** / **Wake at** times.
   - Tap **Grant** next to *Screen-off permission* and confirm the device-admin
     prompt so the app can power the screen off at night.

7. Tap **Save & connect**.

---

## Build & install

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.tigerworkshop.homepanel/.MainActivity
```

Requires the Android SDK; set `sdk.dir` in `local.properties` (not committed).

---

## Notes & tuning

- **Cleartext HTTP** to a LAN Home Assistant is allowed via
  `res/xml/network_security_config.xml`. For an `https://` server the app
  switches to `wss://` automatically.
- **Night wake** uses Android device-admin `lockNow()`. A finger tap will **not**
  wake a locked screen (an Android restriction); it wakes on the morning schedule
  or the power button. Opening the panel during the night shows it briefly, then
  re-sleeps.
- **Always in front** — this is a normal always-on app (Back is swallowed to keep
  the panel up). It can also be set up as the device HOME launcher / pinned for a
  permanent kiosk install.
- **Forecasts** use Home Assistant's `weather.get_forecasts` service and refresh
  on connect and periodically.

---

## Tech

Kotlin · Jetpack Compose (Material 3) · OkHttp WebSocket · CameraX + ML Kit
(QR scanning) · AGP 9.2.0 (built-in Kotlin) · Gradle 9.4.1 · Kotlin 2.1.20 ·
minSdk 26 / targetSdk 35.

## License

Licensed under the **Apache License, Version 2.0** — see [`LICENSE`](LICENSE)
and [`NOTICE`](NOTICE). You may use, modify and distribute this software,
including commercially, under the terms of the license.

> Copyright 2026 Tiger Fok. Licensed under the Apache License, Version 2.0 —
> <http://www.apache.org/licenses/LICENSE-2.0>

## Disclaimer

A personal, open hobby project. Not affiliated with or endorsed by Home Assistant
or any hardware vendor. "Home Assistant" and Met.no are trademarks of their
respective owners.
