# Scrollwerk

An Android home-screen launcher built on the ideas of [niri](https://github.com/YaLTeR/niri)'s
scrollable tiling: workspaces of app columns on a scrolling strip. It adds a rofi-style app launcher and the look and feel
of the [Noctalia](https://github.com/noctalia-dev/noctalia) shell. Scrollwerk isn't affiliated with niri or Noctalia.

<p align="center">
  <img src="screenshot.png" alt="Scrollwerk home screen: the bar, the notification bar, weather and agenda widgets, and the search pill" width="320">
</p>

## What it does

- **Home:** a vertical stack of pages, like niri's workspaces. Page 1 is the widget desktop. Below it come your workspaces,
  then always one empty workspace. A floating Noctalia bar (launcher · workspace dots · clock · battery ·
  Control Center) replaces the system status bar. The clock stays centred, so with many workspaces the date
  shortens ("Wed 11 Nov" → "11 Nov" → "Wed 11") instead of crowding the capsules beside it.
- **Now playing:** while music plays, ♪ and the track show up — tap to play / pause, long-press to open the player.
  A paused track stays for two minutes. With the notification bar on, it shows "Title — Artist" there whenever no
  alert is newer than the track; otherwise the bar's date makes way for the title (squeezed to fit, scrolling if long).
  Uses the media widget's notification access; turn off with `bar.media`.
- **Workspaces:** each one is a horizontal strip of columns. The focused column sits in the centre with a focus ring.
  - Tap the focused column to switch to its app. Tap another column to focus it.
  - A column can hold **two apps, which open together in split screen**. This is Android's closest equivalent to a niri column with two windows.
  - Long-press a column to add a second app, move the column left or right, make an app rule for it, or remove it.
  - Adding an app to the empty workspace creates a new one. A workspace disappears once its last column is removed.
  - Home from another app returns you to the workspace you were on. Home or Back on the home screen goes to the desktop page.
  - Android doesn't let a launcher tile or move other apps' windows, so a column is a shortcut to that app's task, not a live window.
- **Overview** (like niri's): pinch in with two fingers on the home screen, or long-press the workspace dots in the bar.
  - Every workspace is shown as a row of small columns, with the desktop on top and "New workspace" at the end.
    The workspace you're on has the focus ring.
  - Tap a workspace to go there.
  - Long-press a column and drag it to reorder it, move it to another workspace, or drop it on "New workspace" to start one.
    A workspace you empty this way disappears.
  - The ↑ / ↓ arrows reorder workspaces. Back or Home closes the Overview.
- **Named workspaces:** tap a workspace's title (or the pencil in the Overview) to name it, e.g. "Work" or "Chat".
  The name replaces "Workspace N" on the page and in the Overview; the number stays next to it, since the bar's dots
  count by number. Clear the field or tap "Remove name" to go back to the number. Names live in `workspaces.json`.
- **App rules** (like niri's window rules) say which workspace an app belongs to:
  - Launching an app with a rule from the launcher, while it's in no workspace yet, adds it to its workspace.
    A workspace named in a rule that doesn't exist yet is created with that name.
  - Long-press it in the launcher for "Add to Chat" instead of "Add to workspace N".
  - Long-press a column and pick **Always put in this workspace** to make a rule (or remove it again).
    It uses the workspace's name, else its number, so name a workspace first if you reorder them.
  - Or write them in `config.json`: `app` is a package name or label, `workspace` a name or number, and
    `"autoAdd": false` keeps the "Add to" label without adding on launch. Later rules win.
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
  - **Column badges:** a workspace column whose app has alerts shows a count in the source's colour, on the workspace
    pages and in the Overview. A split column adds up both apps.
  - Uses the same "notification access" as the media widget. Scrollwerk keeps only Slack, SMS and email-client
    notifications, and only in memory while the bar is on. Everything else is dropped as it arrives.
- **Launcher:** tap the search pill or the bar's launcher button. Long-press a result for **Add to workspace N**
  (or **Add to Chat** when the app has a rule).
  - fzf-style fuzzy matching (`gm` → Google Maps). Matched letters are highlighted, and apps you launch often rank higher in search results.
  - Enter or the keyboard's Go key launches the selected row. On a hardware keyboard, use arrows or Tab to move and Esc to close.
  - Calculator: type `12*7` and press Enter to copy `84`.
  - **Unit conversion:** `5 km to mi`, `72 f in c`, `2 cups to ml`, `10 gb to gib`, `100 kmh to mph`, `1,5 h till min`.
    Covers length, mass, temperature, volume (including cooking measures), speed, data and time. Enter copies the number.
  - **Emoji:** start with `:` to search emoji only, by English name or English / Swedish keyword (`:fire`, `:thumbs`,
    `:hjärta`, `:sweden`). Enter or a tap copies it. The list comes from Unicode's CLDR data
    (`tools/gen_emoji.py` rebuilds `assets/emoji.tsv`); emoji the phone's font can't draw are left out.
  - **App shortcuts:** the actions apps offer on long-press ("Compose", "New message", "Do not disturb") show up in
    search after the apps: type part of the shortcut (`comp`) or the app's name (`gmail`). A result's long-press menu
    lists its app's shortcuts too. Android only shares shortcuts with the default home app, so Scrollwerk must be set
    as yours. Turn off with `launcher.shortcuts`.
  - **Settings pages:** `wifi`, `bluetooth`, `battery`, `brightness`, `dev`… open that Settings screen directly.
  - **Contacts:** names match fuzzily. Tap opens the contact; long-press for Call (opens the dialer) or Message.
    The first time, a "Search contacts too" row asks for contacts permission. Names are read on the phone, kept in
    memory while the launcher is open, and never sent anywhere. Refusing turns contacts search off (`launcher.contacts`).
  - **Web search:** the last row searches the web, so Enter on a query with no other match does a web search.
    It uses the phone's web search, or `launcher.webSearchUrl` (for example `"https://duckduckgo.com/?q=%s"`).
  - Settings, contacts and web search only appear under the All chip, and not while picking an app for a workspace.
  - Chips: All, Recent, and app categories. With an empty search, pinned apps come first, then everything A–Z.
  - Long-press a result to pin it or open its app info.
- **Control Center:** pull down on the widgets, or tap the sliders icon in the bar.
  - Quick tiles: flashlight, Wi-Fi, Bluetooth, volume, wallpaper, settings.
  - **Theme switcher:** Noctalia's 10 built-in palettes (Ayu, Catppuccin, Dracula, Eldritch, Gruvbox, Kanagawa,
    Noctalia, Nord, Rosé Pine, Tokyo-Night) or wallpaper-derived colours, each in Dark / Light / System mode.
    The choice is written to `config.json`.
  - **Your own palettes:** put colour-scheme files in `palettes/` next to `config.json` and they join the theme grid,
    with their own matching wallpapers. Two formats work:
    - Noctalia colour schemes: JSON with `dark` and `light` objects of `mPrimary`, `mSurface`, `mOnSurface`, … (plain
      `primary`, `surface`, … too). Roles a file leaves out are derived; a file without `dark` / `light` is used for both.
    - base16 / base24 schemes (`.yaml` or `.json` with `base00`…`base0F`, e.g. from tinted-theming). Blue becomes
      primary, purple secondary, cyan tertiary and red error; the opposite light/dark mode is derived by flipping the
      background ramp.

    The palette is named by the file's `name` (or `scheme`) field, else its file name, and replaces a built-in of the same
    name. Changes show up right away. A file that can't be read is named in the error banner; the rest still load.

    ```sh
    adb push gruvbox-dark-medium.yaml /sdcard/Android/data/dev.scrollwerk.launcher/files/palettes/
    ```
  - **Themed icons** (off by default): app icons drawn in the palette's colours, like Android's themed icons, in the
    launcher, the workspaces and the Overview. Apps that ship a monochrome icon (Android 13+) use it. For the rest,
    Scrollwerk takes the parts of the normal icon that stand out from its background colour; a one-colour logo
    becomes its silhouette.
  - **Matching wallpapers:** each palette has a wallpaper generated in its own colours, in dark and light variants, sized to
    your screen. It has a gradient base, accent glows, and a strip of columns with the focused one lit in the primary colour.
    Each palette card shows a preview. "Apply" sets it once. The **Matching wallpaper** toggle (off by default) sets it
    automatically whenever you switch theme. This replaces the home-screen wallpaper only, and Android doesn't let an app
    back up the old one, so save it first if you want to keep it.
  - **Widgets:** a switch for each desktop widget (clock, weather, media, agenda, system), saved to `config.json`.
  - **Notifications:** the notification bar switch, a switch per source (Slack, SMS, email), and the email client.
- Back or Home closes whatever is open. Back on the bare home screen does nothing.

## Config

`config.json` is written with every default filled in. Changes apply as soon as the file is saved while the home screen
is showing (an `adb push` or an editor's save), and the file is re-read whenever the home screen comes back.
Comments and trailing commas are allowed. If an edit breaks the file, a banner shows the error and the last good config stays in use.
The theme switcher rewrites the file, so any comments you added are lost.

```jsonc
{
  "theme":    { "palette": "Noctalia" /* any palette name, or "wallpaper" */, "mode": "dark" /* light, system */,
                "cornerRadiusScale": 1.0, "matchingWallpaper": false, "themedIcons": false },
  "bar":      { "hideSystemStatusBar": true, "floating": true, "use24Hour": null, "showDate": true, "media": true },
  "launcher": { "searchPosition": "bottom" /* or "top" */, "sortByUsage": true, "categories": true,
                "calculator": true, "shortcuts": true, "settings": true, "contacts": true,
                "webSearch": true, "webSearchUrl": null /* e.g. "https://duckduckgo.com/?q=%s" */,
                "emoji": true, "units": true },
  "widgets":  { "clock": true, "weather": true, "media": true, "agenda": true, "system": true,
                "weatherLocation": null /* e.g. "Stockholm" */, "temperatureUnit": "celsius",
                "calendarApp": null /* package name, e.g. "org.fossify.calendar"; null = system default */ },
  "notifications": { "enabled": false, "slack": true, "sms": true, "email": true,
                     "emailApp": null /* package name, e.g. "com.fsck.k9"; null = any email app */ },
  "rules": [ { "app": "com.Slack", "workspace": "Chat" }, { "app": "Firefox", "workspace": "2", "autoAdd": false } ]
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
./gradlew :app:testDebugUnitTest      # search providers, emoji & units, workspaces & rules, palettes & import,
                                      # icon glyphs, notification filtering, weather parsing, config
./gradlew :app:installDebug           # then pick "Scrollwerk" as the home app
```

On a new phone, after installing:

1. Press Home and pick **Scrollwerk**, or set it in Settings → Apps → Default apps → Home app.
2. For the media widget, Now playing and the notification bar, grant notification access. The widget's **Enable**
   button and the bar's **Allow** button both open the right settings page.
3. Turn the notification bar on in the Control Center (**Notifications → Notification bar**) and pick your email client.

Everything else is asked for when you first use it: location for weather (or set `weatherLocation`), calendar for the
agenda, and contacts from the launcher's "Search contacts too" row.
