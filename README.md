<div align="center">

<img src="graphics/icon.webp" width="120" alt="白い熊 予定表 icon" />

# 白い熊 予定表

**A black-and-yellow, fully themeable, Pimlical-style calendar.**

A fork of [Fossify Calendar](https://github.com/FossifyOrg/Calendar) with **major additions**: a granular per-element theming system, a Pimlical-style day-box weekly view, category-based Export/Import of everything (events, settings, fonts, calendars), headless backup over a token-gated intent, per-element fonts, Japanese date/time formats, and one-tap navigation shortcuts.

Installs **side-by-side** with Fossify Calendar (app id `shiroikuma.yotehyo`).

**📥 Latest release: [`1.10.3+50`](https://github.com/ShiroiKuma0/shiroikuma-yotehyo/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-yotehyo/releases)

</div>

---

## 🎨 Granular black/yellow theming — the 白い熊 予定表 UI page
One consolidated page controls every colour in the app — ~30 slots with a cascading foundation (background / primary / text), covering the search bar, top-bar chrome, menus, event text, day boxes, today/weekend styling, grid lines, and dialogs. Every colour picker has an alpha slider and recent colours; defaults are pure yellow `#FFFF00` on black. Dialogs get a configurable accent border and boxed buttons so they stand out on the black background.

## 📦 Export / Import everything, by category
The first section of the UI page: pick a persistent export directory (it shows the latest export at a glance), then export or import by category — **all calendars (all events & tasks, as standard ICS)**, general settings, UI & theme (imported fonts ride along), widgets, and calendar categories with their styling. Round-pill panel buttons, chain-closing success dialogs, optional in-place app restart after import.

## 🤖 Headless backup over a token-gated intent
The same category export, runnable without touching the phone: a sister automation app broadcasts a token-gated intent, this app exports itself in the background and replies with the written path and its real size. Progress comes back as **real counts, never a percentage** — `Events 1234/8942`. A run can be **stopped mid-flight**, and it takes its half-written archive with it, so a cancelled backup leaves the directory exactly as it found it. The app also **states which items should start ticked** in the caller's picker rather than leaving it to guess. Off by default; the switch and its token live under Export / Import, and the token never travels inside a backup.

## 📅 Pimlical-style day-box weekly view
A selectable weekly view built from day boxes: configurable headers (Japanese date format by default, Japanese era or any custom pattern), per-day-type colours and border thicknesses for today and weekends, and seven individually toggleable grid lines, each with its own colour and thickness. Long-press empty space to add an event or task.

## ✍️ Per-element fonts
Any text element — events, day-box headers, menus, the view switcher — can get its own font family (import your own font files), weight, and size, with a live sample. Calendar categories can carry their own background colour and font, applied throughout the views.

## 🇯🇵 Japanese time formats & quick navigation
Event times in a Japanese clock/duration style (or any custom pattern), 日週月年 view-switcher glyphs in the search bar, a 設定 two-glyph shortcut (設 opens the UI page, 定 opens Settings), long-press the overflow button for the UI page, swipe up in the left quarter to jump to today, and a keyboard-entry HH:MM time picker instead of the clock face.

## 🌐 Per-event timezones
Events can carry independent start and end timezones, annotated in the day view.

---

## Built on Fossify Calendar
A fork of [Fossify Calendar](https://github.com/FossifyOrg/Calendar) (app id `shiroikuma.yotehyo`, so it coexists with the official build). Fossify builds privacy-first, ad-free, open-source Android apps; this fork keeps that foundation and its syncing (CalDAV etc.) intact. It builds against a [patched Fossify Commons](https://github.com/ShiroiKuma0/shiroikuma-commons) that removes the anti-tamper checks a re-signed fork would trip. The code remains under the [GPL-3.0 license](LICENSE).

## Building
```bash
git clone git@github.com:ShiroiKuma0/shiroikuma-yotehyo.git
cd shiroikuma-yotehyo
# needs JDK 21 and the patched commons published to mavenLocal (see CLAUDE.md)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFoss
```
The signed foss release APK lands in `~/tmp/shiroikuma-yotehyo_<version>+<NNN>_arm64-v8a.apk`
— the fork build counter is always zero-padded to three digits (`+001`, `+053`) so builds sort
in order.
