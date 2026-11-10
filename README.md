# Scrollwerk

An Android home-screen launcher built on the ideas of [niri](https://github.com/YaLTeR/niri)'s
scrollable tiling: workspaces of app columns on a scrolling strip. It adds a rofi-style app launcher and the look and feel
of the [Noctalia](https://github.com/noctalia-dev/noctalia) shell. Scrollwerk isn't affiliated with niri or Noctalia.

## What it does

- **Home:** a vertical stack of pages, like niri's workspaces. Page 1 is the widget desktop. Below it come your workspaces,
  then always one empty workspace. A floating Noctalia bar (launcher · workspace dots · clock · battery ·
  Control Center) replaces the system status bar.
- **Workspaces:** each one is a horizontal strip of columns. The focused column sits in the centre with a focus ring.
  - Tap the focused column to switch to its app. Tap another column to focus it.
  - A column can hold **two apps, which open together in split screen**. This is Android's closest equivalent to a niri column with two windows.
  - Long-press a column to add a second app, move the column left or right, or remove it.
  - Adding an app to the empty workspace creates a new one. A workspace disappears once its last column is removed.
  - Home from another app returns you to the workspace you were on. Home or Back on the home screen goes to the desktop page.
  - Android doesn't let a launcher tile or move other apps' windows, so a column is a shortcut to that app's task, not a live window.
- **Desktop widgets** (Noctalia's widget types that work on Android):
  - **Clock:** a big clock whose text contrast follows the wallpaper.
  - **Weather:** current conditions and a 5-day forecast from [Open-Meteo](https://open-meteo.com/), like Noctalia's. Asks for coarse location, or uses `weatherLocation`. Coordinates are rounded to about 1 km before they're sent.
  - **Media:** what's playing, with previous / play-pause / next. Needs "notification access" so it can read media sessions.
  - **Agenda:** your next events over two days, read from Android's calendar storage. Needs calendar access.
    The chip in the card's header picks which app events open in: an installed calendar app, the system default,
    or "Other app…". Tap an event to open it, or the title to open the app.
  - **System:** memory, storage and battery temperature.
- **Notification bar** (off by default; turn it on in the Control Center): a second strip under the bar, styled like it,
  with alerts from **Slack, SMS and email**.
  - It shows the newest alert, then a capsule per source with its count. Tap a capsule to show only that source's alerts in the
    strip and the list; tap it again for all of them. Long-press a capsule to open Slack, your default SMS app or your email client.
  - Tap the strip to drop down the list. It has an "Open …" link while a source is selected.
  - Tap an alert to open it (the message or thread), or × to dismiss it, which also clears it from the phone. Back closes the list.
  - **Email client:** choose which app counts as email in the Control Center: any installed email app, one from the list,
    or "Other app…" for clients that don't register as one. Long-pressing the email capsule opens that app.
  - Uses the same "notification access" as the media widget. Scrollwerk keeps only Slack, SMS and email-client
    notifications, and only in memory while the bar is on. Everything else is dropped as it arrives.
- **Launcher:** tap the search pill or the bar's launcher button. Long-press a result for **Add to workspace N**.
  - fzf-style fuzzy matching (`gm` → Google Maps). Matched letters are highlighted, and apps you launch often rank higher in search results.
  - Enter or the keyboard's Go key launches the selected row. On a hardware keyboard, use arrows or Tab to move and Esc to close.
  - Calculator: type `12*7` and press Enter to copy `84`.
  - Chips: All, Recent, and app categories. With an empty search, pinned apps come first, then everything A–Z.
  - Long-press a result to pin it or open its app info.
- **Control Center:** pull down on the widgets, or tap the battery or sliders in the bar.
  - Quick tiles: flashlight, Wi-Fi, Bluetooth, volume, wallpaper, settings.
  - **Theme switcher:** Noctalia's 10 built-in palettes (Ayu, Catppuccin, Dracula, Eldritch, Gruvbox, Kanagawa,
    Noctalia, Nord, Rosé Pine, Tokyo-Night) or wallpaper-derived colours, each in Dark / Light / System mode.
    The choice is written to `config.json`.
  - **Matching wallpapers:** each palette has a wallpaper generated in its own colours, in dark and light variants, sized to
    your screen. It has a gradient base, accent glows, and a strip of columns with the focused one lit in the primary colour.
    Each palette card shows a preview. "Apply" sets it once. The **Matching wallpaper** toggle (off by default) sets it
    automatically whenever you switch theme. This replaces the home-screen wallpaper only, and Android doesn't let an app
    back up the old one, so save it first if you want to keep it.
  - **Widgets:** a switch for each desktop widget (clock, weather, media, agenda, system), saved to `config.json`.
  - **Notifications:** the notification bar switch, a switch per source (Slack, SMS, email), and the email client.
- Back or Home closes whatever is open. Back on the bare home screen does nothing.

## Config

`config.json` is written with every default filled in and is re-read each time the home screen comes back.
Comments and trailing commas are allowed. If an edit breaks the file, a banner shows the error and the last good config stays in use.
The theme switcher rewrites the file, so any comments you added are lost.

```jsonc
{
  "theme":    { "palette": "Noctalia" /* any palette name, or "wallpaper" */, "mode": "dark" /* light, system */,
                "cornerRadiusScale": 1.0, "matchingWallpaper": false },
  "bar":      { "hideSystemStatusBar": true, "floating": true, "use24Hour": null, "showDate": true },
  "launcher": { "searchPosition": "bottom" /* or "top" */, "sortByUsage": true, "categories": true,
                "calculator": true },
  "widgets":  { "clock": true, "weather": true, "media": true, "agenda": true, "system": true,
                "weatherLocation": null /* e.g. "Stockholm" */, "temperatureUnit": "celsius",
                "calendarApp": null /* package name, e.g. "org.fossify.calendar"; null = system default */ },
  "notifications": { "enabled": false, "slack": true, "sms": true, "email": true,
                     "emailApp": null /* package name, e.g. "com.fsck.k9"; null = any email app */ }
}
```

```sh
adb pull /sdcard/Android/data/dev.scrollwerk.launcher/files/config.json
adb push config.json /sdcard/Android/data/dev.scrollwerk.launcher/files/config.json
```

Launch counts, recents and pins live in the app's private `usage.json`, and workspaces in `workspaces.json`.
Palettes are generated from Noctalia's `builtin_palettes.cpp` (MIT).

## Build

Toolchain: AGP 9.5, Gradle 9.9 (wrapper checked in), Kotlin 2.4, compileSdk 37, minSdk 31.
Runs on JDK 21+ — Android Studio's bundled JDK 25 works.

**Android Studio:** open the folder and sync.

**Command line:** point `JAVA_HOME` at a JDK 21+ (e.g. Studio's `jbr` directory), then:

```sh
./gradlew :app:testDebugUnitTest      # search, calculator, weather parsing, palettes, config, notification filtering
./gradlew :app:installDebug           # then pick "Scrollwerk" as the home app
```

On a new phone, after installing:

1. Press Home and pick **Scrollwerk**, or set it in Settings → Apps → Default apps → Home app.
2. For the media widget and the notification bar, grant notification access. The widget's **Enable** button and
   the bar's **Allow** button both open the right settings page.
3. Turn the notification bar on in the Control Center (**Notifications → Notification bar**) and pick your email client.
