<div align="center">

<img src="graphics/icon.webp" width="120" alt="白い熊 予定表 icon" />

# 白い熊 予定表

**A black-and-yellow, fully themeable, Pimlical-style calendar.**

A fork of [Fossify Calendar](https://github.com/FossifyOrg/Calendar) with **major additions**: tasks you tick off in the calendar and that roll over until you do, a granular per-element theming system, a Pimlical-style day-box weekly view, boxed monthly days with framed weekends, days off (休み) and holidays, category-based Export/Import of everything (events, settings, fonts, calendars), headless backup plus a verified data door that restores this app **with its data** onto a wiped phone, per-element fonts, Japanese date/time formats, flight-ready time zones (departure and arrival zones, 発 / 着 lines, airport-code search, a travel banner), outlined-pill controls throughout, and one-tap navigation shortcuts.

Installs **side-by-side** with Fossify Calendar (app id `shiroikuma.yotehyo`).

**📥 Latest release: [`1.10.3+078`](https://github.com/ShiroiKuma0/shiroikuma-yotehyo/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-yotehyo/releases)

</div>

---

## ✅ Tasks that don't get forgotten
Every task row carries a checkbox: one tap ticks it off in place, struck through and dimmed, without opening anything. A task still unticked once its own day is over **moves forward to today**, keeping its clock time, and keeps moving each day until it is done — so a task set for a day is either finished or still in front of you. Its reminders follow it to the new day. Not everything shaped like a task is a to-do, so a **Fixed entry** switch marks a record of something that happened on its date: no checkbox, nothing to complete, and the rollover leaves it exactly where it is.

---

## 🎨 Granular black/yellow theming — the 白い熊 予定表 UI page
One consolidated page controls every colour in the app — ~30 slots with a cascading foundation (background / primary / text), covering the search bar, top-bar chrome, menus, event text, day boxes, today/weekend styling, grid lines, and dialogs. Every colour picker has an alpha slider and recent colours; defaults are pure yellow `#FFFF00` on black. Dialogs get a configurable accent border and boxed buttons so they stand out on the black background, and the transient flash messages are drawn in the same colours — black with yellow text and a rounded yellow border — as is the contextual action bar raised by selecting an entry, overflow menus included. Today's day box carries a thick border in **both** the monthly and the weekly view, from one shared colour and thickness, so today is never lost among the event bars.

## 📦 Export / Import everything, by category
The first section of the UI page: pick a persistent export directory (it shows the latest export at a glance), then export or import by category — **all calendars (all events & tasks, as standard ICS)**, general settings, UI & theme (imported fonts ride along), widgets, and calendar categories with their styling. Round-pill panel buttons, chain-closing success dialogs, optional in-place app restart after import. An import onto **another phone** keeps every calendar in view: calendar ids are translated by title, so the shown, quick-filter and default-calendar settings follow the calendars they meant rather than whatever numbers the new phone handed out.

## 🤖 Headless backup, and a restore that survives a wiped phone
The same category export, runnable without touching the phone: a sister app asks, this app exports itself in the background and replies with the written path and its real size. Progress comes back as **real counts, never a percentage** — `Events 1234/8942`. A run can be **stopped mid-flight**, and it takes its half-written archive with it, so a cancelled backup leaves the directory exactly as it found it. The app also **states which items should start ticked** in the caller's picker rather than leaving it to guess.

It is **on out of the box**, and the authorization token is now optional — a switch under Export / Import, off unless you want it. A pasted secret cannot survive a wipe, and surviving a wipe is the point: a second, **authenticated door** lets the backup app pull this app's data and put it back on a clean phone, before this app has ever been launched. That door checks its caller three ways — the exact package name, a cross-check against the uid the kernel reports, and a **pinned signing certificate** — and moves the archive through a file descriptor the caller opens, so nothing is written into a directory this app cannot see being encrypted and checksummed. Restoring is only ever possible through that door; the open one can write a backup and say what it did, and nothing else.

---

## 🗓️ Boxed days, framed weekends, days off and holidays
Every day in the monthly and the weekly view sits in a thin yellow box — in the monthly view one shared grid, each line drawn once, the weekday names included. A **weekend**, a **day off** and a **holiday** each get a second, coloured frame drawn just *inside* that box, with the day's date strip filled in the same colour, so the yellow grid and the coloured frame never hide each other: blue for weekends, blue for 休み, red for national holidays.

- **Holidays — a whole-day frame per calendar.** Any calendar can carry a frame colour (国民祝日 → red): every day holding one of its entries is framed and its date strip filled in that colour.
- **Days off — 休み, wherever it is filed.** An entry titled 休み styles itself and its day the same way whichever calendar holds it. The title, the entry's text colour, font and background, the day's frame and the date colour are all set under 休み on the UI page, with an on/off switch. A holiday outranks a day off, a day off outranks a weekend.
- **Monthly bars wear their category colours** — background fill with the category's text colour and font, exactly as the weekly view draws them.

---

## 📅 Pimlical-style day-box weekly view
A selectable weekly view built from day boxes: configurable headers (Japanese date format by default, Japanese era or any custom pattern), per-day-type colours and border thicknesses for today and weekends, and seven individually toggleable grid lines, each with its own colour and thickness. Long-press empty space to add an event or task.

## ✍️ Per-element fonts
Any text element — events, day-box headers, menus, the view switcher — can get its own font family (import your own font files), weight, and size, with a live sample. Calendar categories can carry their own background colour and font, applied throughout the views.

## 🇯🇵 Japanese time formats & quick navigation
Event times in a Japanese clock/duration style (or any custom pattern), 日週月年 view-switcher glyphs in the search bar, a 設定 two-glyph shortcut (設 opens the UI page, 定 opens Settings), long-press the overflow button for the UI page, swipe up in the left quarter to jump to today, and a keyboard-entry HH:MM time picker instead of the clock face.

## ✈️ Time zones that understand a flight
An event can leave in one zone and land in another. Type the times as the ticket prints them — each is read on its own zone's clock — and the editor shows start and end in their own framed cards with a live line between them: the real flight time, the arrival on the departure clock, the 時差, and a red warning if the arrival would come before the departure. In the day view and the event list the device-clock times are followed by 発 / 着 lines with the city, the local time, an outlined zone tag, the time difference and 翌日 when it lands on another date; an event in one foreign zone gets a single 現地 line. Find a zone by city, by its code or by the airport code on the boarding pass (`PRG`, `IST`, `NRT`…), with your recent zones on top. Set a home zone and, while you are away from it, the day view shows where you are and what time it is at home. The arrival zone syncs over CalDAV and survives ICS export / import, and a ✈ marks such events in the month and week grids.

## 💊 Outlined pills, a centred month
Every action is an unfilled pill with a yellow frame — the date, time and zone fields, the calendar, reminder, repetition and status rows, the + button (a round 3 dp ring) and its Event / Task menu in a framed panel. The month grid sits centred with even margins, the week number tucked into each week's first date strip, under a slimmer search bar.

## 📝 Editor layout
In both the event and task editors the calendar (account) selector sits directly under the description — it is one of the first decisions about an entry, not something to scroll past reminders, repetition, attendees and status to reach.

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
