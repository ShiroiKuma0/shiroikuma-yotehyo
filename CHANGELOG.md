# Changelog — 白い熊 予定表 (fork) and Fossify Calendar (upstream)

This file carries **both** histories. The 白い熊 予定表 fork releases come first, newest first;
everything below the `# Changelog` heading further down is Fossify Calendar's own changelog,
kept verbatim.

## 白い熊 予定表 1.10.3+056 — 2026-09-03
Built on Fossify Calendar 1.10.3.

### Major features
- **Tick a task off from the calendar.** The icon on every task row is now a checkbox — an empty
  box, a ticked box once done. One tap writes the completion and restyles the row in place,
  struck through and dimmed, with no navigation. Works in both places task rows are drawn, the
  agenda list and the day view, and keys the completion on the occurrence actually tapped, so a
  repeating task's other occurrences are untouched.
- **Unfinished tasks roll over to today.** A one-off task still unticked once its own day is over
  moves forward to today, keeping its clock time, and keeps moving each day until it is ticked —
  so a task set for a day is either done or still in front of you. Ticking it freezes it on the
  day it was completed. It is a real move: `start_ts`/`end_ts` are rewritten, reminders reschedule
  onto the new day, and a CalDAV task is pushed upstream. Repeating tasks are left alone, since
  their occurrences come from the repetition rule rather than the stored timestamp.
- **Fixed entries.** Not everything shaped like a task is a to-do — a record of something that
  happened on its date should stay on that date. A "Fixed entry" switch in the task editor, under
  the date and time, marks one: no checkbox in either list, no "Mark completed" button, no
  mark-completed action on its notification, and the rollover skips it. Turning it on also clears
  any completion the entry picked up while it was a plain task.

### Behavior
- The rollover runs at most once a day, from the single fetch path every view and both widgets go
  through, so it happens whether the app is opened, a widget refreshes, or the day flips while the
  app sits open. It never unfilters a hidden calendar, and refreshes the widgets once rather than
  once per moved task.
- Clock times survive a DST change across a rollover.

### Settings
- **Settings → Tasks → "Roll unfinished tasks over to today"**, on by default, and carried by
  settings export/import. Switching it back on catches up immediately instead of waiting a day.

## 白い熊 予定表 1.10.3+053 — 2026-08-01
Built on Fossify Calendar 1.10.3.

### UI
- Event and task editors: the calendar (account) selector sits directly under the description —
  one of the first decisions about an entry, rather than something to scroll past reminders,
  repetition, attendees and status to reach.

### Packaging
- The fork build counter is zero-padded to three digits everywhere it is rendered as text — the
  `versionName`, the APK filename and the release tag (`+001`, `+053`). Unpadded counters sort
  wrongly in a file list, burying the newest build; three digits fixes the order up to `+999`,
  which the version-code multiplier already caps the counter at. The `versionCode` keeps the plain
  integer, so in-place updates keep working. Nothing already built was renamed.

## 白い熊 予定表 1.10.3+50 — 2026-07-31
Built on Fossify Calendar 1.10.3.

### Integrations
- `LIST_CATEGORIES` states, per item, whether it should start ticked in the caller's picker,
  rather than leaving the picker to guess.
- An export can be **cancelled mid-flight**, and it takes its half-written archive with it, so a
  cancelled backup leaves the directory exactly as it found it.

## 白い熊 予定表 1.10.3+49 — 2026-07-25
Built on Fossify Calendar 1.10.3.

### Integrations
- **Headless backup over a token-gated intent.** The same category export, runnable without
  touching the phone: a sister automation app broadcasts a token-gated intent, the app exports
  itself in the background and replies with the written path and its real size. Progress comes
  back as real counts, never a percentage (`Events 1234/8942`). Off by default; the switch and its
  token live under Export / Import, and the token never travels inside a backup.

## 白い熊 予定表 1.10.3+48 — 2026-07-25
Built on Fossify Calendar 1.10.3. First fork release, so this entry lists the fork in full.

### Major features
- **Granular theming — the 白い熊 予定表 UI page.** One consolidated page controls every colour in
  the app: around 30 slots with a cascading foundation (background / primary / text), covering the
  search bar, top-bar chrome, menus, event text, day boxes, today and weekend styling, grid lines
  and dialogs.
- **Pimlical-style day-box weekly view.** A selectable weekly view built from day boxes:
  configurable headers (Japanese date format by default, Japanese era or any custom pattern),
  per-day-type colours and border thicknesses for today and weekends, and seven individually
  toggleable grid lines each with its own colour and thickness. Weekly views split into
  independent toggles; long-press empty space to add an event or task.
- **Category-based Export / Import.** Pick a persistent export directory (it shows the latest
  export at a glance), then export or import by category — all calendars (events and tasks, as
  standard ICS), general settings, UI and theme with imported fonts riding along, widgets, and
  calendar categories with their styling. Optional in-place app restart after an import.
- **Per-element fonts.** Any text element — events, day-box headers, menus, the view switcher —
  can take its own font family (import your own font files), weight and size, with a live sample.
- **Per-event timezones.** Events carry independent start and end timezones, annotated in the day
  view.
- **Rich calendar categories.** A category can carry its own font and background colour, applied
  throughout the views; Manage calendars shows both the foreground and background colour drops.

### UI & theming
- Pure yellow `#FFFF00` replaces material yellow `#FFEB3B` as the palette yellow, with a one-time
  migration of already-stored colours.
- Dialogs get a configurable accent border and boxed buttons, so they stand out against the black
  background.
- Colour pickers gain an alpha slider and recently-used colours.
- 日週月年 view-switcher buttons in the search bar; a 設定 two-glyph shortcut (設 opens the UI page,
  定 opens Settings); configurable top-bar icons, menu text and settings title/arrow; long-press
  the overflow button for the UI page.
- Swipe up in the left quarter to jump to today, in every view.
- A keyboard-entry HH:MM time picker instead of the clock face.
- Configurable event time format, with a Japanese clock/duration style as the default.
- Black/yellow thin-line launcher icon.
- The UI settings page is indented by hierarchy level, with kxkb-style headings and spacers.

### Fixes & behavior
- Day view: long-press empty space to add an event or task.
- Fixed the week-grid refresh on resume, and event/border overlap.

### Packaging
- Rebranded fork: app id `shiroikuma.yotehyo`, launcher name 白い熊 予定表, so it installs
  side-by-side with Fossify Calendar. The code namespace stays `org.fossify.calendar`.
- Builds against a [patched Fossify Commons](https://github.com/ShiroiKuma0/shiroikuma-commons)
  (6.1.6-sk2 and later) that removes the anti-tamper "fake version" checks a re-signed fork trips,
  and fixes the spots where Commons hard-codes `org.fossify.*` — including the private-contacts
  provider, which had hidden shared contacts' birthdays and anniversaries from this app. The
  in-app sideloading workaround is gone as a result.
- Fork versioning: upstream version plus a `+NNN` build counter.

---

# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Holidays for New Zealand ([#1157])
- Grid support for monthly calendar widget ([#406])

### Changed
- Updated holiday data

### Fixed
- Fixed event text readability on colored backgrounds ([#1065])
- Fixed invisible current time indicator in weekly view ([#99])
- Fixed stuck zoom level in weekly view on some devices ([#621])

## [1.10.3] - 2026-02-14
### Changed
- Updated translations

### Fixed
- Fixed crash when changing orientation ([#644])

## [1.10.2] - 2026-02-04
### Fixed
- Fixed crash in event editor when CalDAV sync is disabled ([#1024])

## [1.10.1] - 2026-02-03
### Changed
- Updated holiday data
- Updated translations

### Fixed
- Fixed last used default calendar preference for new events ([#1019])

## [1.10.0] - 2026-01-30
### Added
- Added support for custom fonts
- Location suggestions in event editor using recently used locations ([#393])

### Changed
- Unified the local and synchronized calendar pickers in event editor ([#629])
- Updated holiday data ([#1003])
- Updated translations

## [1.9.0] - 2025-12-16
### Changed
- Replaced "event types" concept with "calendars" ([#629])
- Renamed built-in "Regular event" calendar to "Local calendar"
- Weekday labels now use three-letter abbreviations instead of single letters ([#103])
- Converting all-day events to timed events now respects the default start time and duration ([#917])
- Updated translations

### Fixed
- Fixed crashes and freezing on some devices ([#889])

## [1.8.1] - 2025-11-09
### Changed
- Updated holiday data
- Updated translations

### Fixed
- Fixed startup crash in weekly view ([#550])
- Fixed incorrect weekly view start date in some cases ([#45])
- Fixed issue with Up/Arrow button closing the app ([#870])
- Fixed time drift when switching between views ([#590])

## [1.8.0] - 2025-10-29
### Changed
- Compatibility updates for Android 15 & 16
- Removed permission to access network state (it was added accidentally) ([#826])
- Updated holiday data
- Updated translations

## [1.7.0] - 2025-10-16
### Changed
- Events shown in adjacent months are no longer dimmed ([#808])
- Updated translations

### Fixed
- Fixed missing email notifications for attendees in some cases ([#135])
- Fixed missing attendees list when using some specific providers ([#818])

## [1.6.2] - 2025-10-09
### Changed
- Synchronized events with unspecified status are now treated as confirmed ([#761])
- Updated translations

### Fixed
- Fixed event duplication when editing instances of recurring events ([#138])
- Fixed old reminders not being removed when moving events ([#486])
- Fixed drag and drop copying events instead of moving them ([#706])
- Fixed crash when editing events with attendees ([#34])
- Fixed event edits being silently discarded on back press ([#49])
- Fixed synchronization issues when editing events in a recurring series ([#641])

## [1.6.1] - 2025-09-01
### Changed
- Declined events will no longer trigger notifications ([#732])
- Updated translations

### Fixed
- Fixed incorrect widget font size on foldable devices ([#337])
- Fixed missing or delayed reminders in some cases ([#217])

## [1.6.0] - 2025-08-21
### Added
- Holidays for Philippines ([#729])

### Changed
- Updated translations

## [1.5.0] - 2025-07-22
### Added
- Holidays for Guatemala ([#682])

### Changed
- Updated translations

### Fixed
- Audio stream preference now works correctly ([#394])
- Fixed "today" highlight alignment in month view ([#603])

## [1.4.0] - 2025-07-05
### Added
- Holidays for Vietnam ([#613])
- Holidays for Hong Kong ([#574])

### Changed
- Updated translations

## [1.3.0] - 2025-05-13
### Added
- Support for setting event visibility ([#148])
- Option to hide date header in event list widget ([#484])
- Holidays for Bangladesh

### Changed
- Updated some in-app icons for consistency ([#567])
- Updated translations
- Updated holiday data

### Fixed
- Addressed a glitch when long pressing in quick filter
- Fixed age calculation for birthdays from private contacts ([#196])
- Fixed incorrect time in some events imported via ICS files ([#262])
- Fixed "Go to today" button in weekly view ([#551])

## [1.2.0] - 2025-01-26
### Added
- Added ability to export event colors in ICS files (#188)
- Added ability to quickly filter calendars on long press (#309)
- Added state-specific and optional holidays (#379, #413)

### Changed
- Other minor bug fixes and improvements
- Added more translations

### Fixed
- Fixed issue with "Mark completed" notification button (#156)
- Fixed cut-off text in month view on some devices (#265)
- Fixed broken weekly repetition in some timezones (#408)
- Fixed "Mark completed" button color in black & white theme (#357)
- Fixed invisible attendee suggestions (#41)

## [1.1.0] - 2024-11-15
### Added
- Added support for event status

### Changed
- Replaced checkboxes with switches
- Other minor bug fixes and improvements
- Added more translations

### Removed
- Removed support for Android 7 and older versions

### Fixed
- Resolved issue with multi-day all-day events not displaying on the top bar
- Fixed task opening functionality from widgets
- Fixed resizing issue in date widget
- Fixed opacity for incomplete tasks in widgets
- Fixed spanish translation for saturday.

## [1.0.3] - 2024-03-12
### Changed
- Highlight weekends in print mode.
- Updated holidays for some countries.
- Added some translations.

### Fixed
- Fixed month view issue on Google Pixel 8 Pro.
- Fixed event color dots on monthly and daily view.
- Fixed incorrect timezone when import ICS files.

## [1.0.2] - 2024-01-02
### Fixed
- Fixed import compatibility with Simple Calendar.
- Fixed foss flavor configuration.

## [1.0.1] - 2024-01-02
### Fixed
- Fixed import compatibility with Simple Calendar.

## [1.0.0] - 2024-01-01
### Added
- Initial release

[#34]: https://github.com/FossifyOrg/Calendar/issues/34
[#45]: https://github.com/FossifyOrg/Calendar/issues/45
[#49]: https://github.com/FossifyOrg/Calendar/issues/49
[#99]: https://github.com/FossifyOrg/Calendar/issues/99
[#103]: https://github.com/FossifyOrg/Calendar/issues/103
[#135]: https://github.com/FossifyOrg/Calendar/issues/135
[#138]: https://github.com/FossifyOrg/Calendar/issues/138
[#148]: https://github.com/FossifyOrg/Calendar/issues/148
[#196]: https://github.com/FossifyOrg/Calendar/issues/196
[#217]: https://github.com/FossifyOrg/Calendar/issues/217
[#262]: https://github.com/FossifyOrg/Calendar/issues/262
[#337]: https://github.com/FossifyOrg/Calendar/issues/337
[#393]: https://github.com/FossifyOrg/Calendar/issues/393
[#394]: https://github.com/FossifyOrg/Calendar/issues/394
[#406]: https://github.com/FossifyOrg/Calendar/issues/406
[#484]: https://github.com/FossifyOrg/Calendar/issues/484
[#486]: https://github.com/FossifyOrg/Calendar/issues/486
[#550]: https://github.com/FossifyOrg/Calendar/issues/550
[#551]: https://github.com/FossifyOrg/Calendar/issues/551
[#567]: https://github.com/FossifyOrg/Calendar/issues/567
[#574]: https://github.com/FossifyOrg/Calendar/issues/574
[#590]: https://github.com/FossifyOrg/Calendar/issues/590
[#603]: https://github.com/FossifyOrg/Calendar/issues/603
[#613]: https://github.com/FossifyOrg/Calendar/issues/613
[#621]: https://github.com/FossifyOrg/Calendar/issues/621
[#629]: https://github.com/FossifyOrg/Calendar/issues/629
[#641]: https://github.com/FossifyOrg/Calendar/issues/641
[#644]: https://github.com/FossifyOrg/Calendar/issues/644
[#682]: https://github.com/FossifyOrg/Calendar/issues/682
[#706]: https://github.com/FossifyOrg/Calendar/issues/706
[#729]: https://github.com/FossifyOrg/Calendar/issues/729
[#732]: https://github.com/FossifyOrg/Calendar/issues/732
[#761]: https://github.com/FossifyOrg/Calendar/issues/761
[#808]: https://github.com/FossifyOrg/Calendar/issues/808
[#818]: https://github.com/FossifyOrg/Calendar/issues/818
[#826]: https://github.com/FossifyOrg/Calendar/issues/826
[#870]: https://github.com/FossifyOrg/Calendar/issues/870
[#889]: https://github.com/FossifyOrg/Calendar/issues/889
[#917]: https://github.com/FossifyOrg/Calendar/issues/917
[#1003]: https://github.com/FossifyOrg/Calendar/issues/1003
[#1019]: https://github.com/FossifyOrg/Calendar/issues/1019
[#1024]: https://github.com/FossifyOrg/Calendar/issues/1024
[#1065]: https://github.com/FossifyOrg/Calendar/issues/1065
[#1157]: https://github.com/FossifyOrg/Calendar/issues/1157

[Unreleased]: https://github.com/FossifyOrg/Calendar/compare/1.10.3...HEAD
[1.10.3]: https://github.com/FossifyOrg/Calendar/compare/1.10.2...1.10.3
[1.10.2]: https://github.com/FossifyOrg/Calendar/compare/1.10.1...1.10.2
[1.10.1]: https://github.com/FossifyOrg/Calendar/compare/1.10.0...1.10.1
[1.10.0]: https://github.com/FossifyOrg/Calendar/compare/1.9.0...1.10.0
[1.9.0]: https://github.com/FossifyOrg/Calendar/compare/1.8.1...1.9.0
[1.8.1]: https://github.com/FossifyOrg/Calendar/compare/1.8.0...1.8.1
[1.8.0]: https://github.com/FossifyOrg/Calendar/compare/1.7.0...1.8.0
[1.7.0]: https://github.com/FossifyOrg/Calendar/compare/1.6.2...1.7.0
[1.6.2]: https://github.com/FossifyOrg/Calendar/compare/1.6.1...1.6.2
[1.6.1]: https://github.com/FossifyOrg/Calendar/compare/1.6.0...1.6.1
[1.6.0]: https://github.com/FossifyOrg/Calendar/compare/1.5.0...1.6.0
[1.5.0]: https://github.com/FossifyOrg/Calendar/compare/1.4.0...1.5.0
[1.4.0]: https://github.com/FossifyOrg/Calendar/compare/1.3.0...1.4.0
[1.3.0]: https://github.com/FossifyOrg/Calendar/compare/1.2.0...1.3.0
[1.2.0]: https://github.com/FossifyOrg/Calendar/compare/1.1.0...1.2.0
[1.1.0]: https://github.com/FossifyOrg/Calendar/compare/1.0.3...1.1.0
[1.0.3]: https://github.com/FossifyOrg/Calendar/compare/1.0.2...1.0.3
[1.0.2]: https://github.com/FossifyOrg/Calendar/compare/1.0.1...1.0.2
[1.0.1]: https://github.com/FossifyOrg/Calendar/compare/1.0.0...1.0.1
[1.0.0]: https://github.com/FossifyOrg/Calendar/releases/tag/1.0.0
