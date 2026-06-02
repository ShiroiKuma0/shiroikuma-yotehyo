# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

This is **shiroikuma-yotehyo** — a personal fork of [Fossify Calendar](https://github.com/FossifyOrg/Calendar)
(`https://github.com/ShiroiKuma0/shiroikuma-yotehyo`). Upstream is an open-source, privacy-focused
Android calendar/agenda app written in Kotlin (API 26–36), built on `org.fossify:commons`.

We rebrand it and develop our own changes on top of upstream.

## Branches

- **`main`** — tracks upstream Fossify Calendar verbatim (kept in sync via the fork). Do not develop here.
- **`custom`** — our development branch. All rebranding and feature work lives here.

`origin` is the personal fork (`git@github.com:ShiroiKuma0/shiroikuma-yotehyo`). Upstream
(`FossifyOrg/Calendar`) is the source we rebase onto.

## Fork identity (vs. upstream)

| Thing                | Upstream                | This fork                                            |
|----------------------|-------------------------|------------------------------------------------------|
| Application ID       | `org.fossify.calendar`  | `shiroikuma.yotehyo`                                 |
| App label (launcher) | `Calendar`              | `白い熊 予定表`                                       |
| Code namespace       | `org.fossify.calendar`  | `org.fossify.calendar` (**unchanged** — see below)   |

**Why the namespace stays `org.fossify.calendar`:** ~50 source files import `org.fossify.calendar.R`,
`org.fossify.calendar.BuildConfig`, and `org.fossify.calendar.databinding.*`. Those classes are generated
under the Gradle `namespace`, not the `applicationId`. AGP decouples the two, so we change only the
**installed application id** (`APP_ID`) and keep the **namespace** (`APP_NAMESPACE`) at the upstream value.
Changing the namespace would break every one of those imports.

These are wired through `gradle.properties` and `app/build.gradle.kts`:
- `gradle.properties`: `APP_ID=shiroikuma.yotehyo`, `APP_NAMESPACE=org.fossify.calendar`
- `app/build.gradle.kts`: `applicationId = APP_ID`, `namespace = APP_NAMESPACE`
- App label: `app/src/main/res/values/strings.xml` → `app_launcher_name`

## Versioning scheme

We always base our version on upstream and add a fork build increment.

Values live in `gradle.properties`:
- `VERSION_NAME` — upstream version name (e.g. `1.10.3`).
- `VERSION_CODE` — upstream version **code** that corresponds to that name (e.g. `20` for 1.10.3).
- `BUILD_NUMBER` — our fork increment ("+N"). Starts at `1` for the first build of a given upstream version.

`app/build.gradle.kts` computes the installed values:
- **versionName** = `"<VERSION_NAME>+<BUILD_NUMBER>"`  → e.g. `1.10.3+1`
- **versionCode** = `VERSION_CODE * 10000 + BUILD_NUMBER`  → e.g. `20 * 10000 + 1 = 200001`

This makes the versionCode strictly increase across every rebuild of the same upstream version
(`200001`, `200002`, …) so in-place updates on the phone always work, and it jumps to the next
band when upstream changes (e.g. `1.11.0` code 22 → `220001`).

The output APK is named `shiroikuma-yotehyo_<VERSION_NAME>+<BUILD_NUMBER>_arm64-v8a.apk`
(e.g. `shiroikuma-yotehyo_1.10.3+1_arm64-v8a.apk`). The `arm64-v8a` label is a naming convention;
the build produces a single universal APK (no ABI splits), which runs on arm64-v8a.

> Note on the `VERSION_CODE=20` base: upstream's `gradle.properties` briefly carried `21` from a
> `1.11.0` release that was reverted back to `1.10.3` (version code left at 21). We use `20`, the
> code that actually corresponds to the `1.10.3` name. On a clean future upstream release the name
> and code are consistent, so just copy upstream's `VERSION_CODE` verbatim.

### Bump rules
- **Every build with changes** bumps `BUILD_NUMBER` by 1. The `buildFoss` Gradle task does this
  automatically after a successful build (it rewrites `gradle.properties`), so the committed
  `BUILD_NUMBER` is always the *next* build's number.
- **On a new upstream version** (user will instruct a rebase): set `VERSION_NAME`/`VERSION_CODE` to the
  new upstream values and reset `BUILD_NUMBER=1`, then build the new `+1`.

## Building

Toolchain: **JDK 21** (the default `java` on PATH is too old for AGP 9.x), Android SDK at
`/home/shiroikuma/android-sdk` (configured in the gitignored `local.properties`), Gradle via the wrapper.

The easiest path is the **`build-apk` skill** (`.claude/skills/build-apk/`), which encodes the full
build-and-push flow. The core command:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFoss < /dev/null
```

`buildFoss` (defined in `app/build.gradle.kts`):
1. runs `assembleFossRelease`,
2. copies the signed APK to `~/tmp/shiroikuma-yotehyo_<name>+<N>_arm64-v8a.apk`,
3. auto-increments `BUILD_NUMBER` in `gradle.properties`.

Other useful Gradle tasks: `assembleFossRelease`, `assembleFossDebug`, `detekt`, `lintFossDebug`.

**Product flavors:** `core` (F-Droid base), `foss` (fully open — all features unlocked, donate link),
`gplay` (Google Play). **We build `foss`.** Debug builds get a `.debug` app ID suffix.

### Signing
Release signing is non-interactive via `keystore.properties` (gitignored). This fork **reuses the
`shiroikuma-denwa` keystore**: `/home/shiroikuma/.android-keystores/shiroikuma-denwa.jks`, alias `denwa`.
Without `keystore.properties` (or `SIGNING_*` env vars) the build is unsigned and will not install.

## Installing to the phone — STRICT RULE

**Never install the APK to the phone, and never push without asking.** After every build:
1. **Ask** the user whether to push the APK.
2. Only when the user confirms, `adb push` the APK to `/sdcard/tmp/` on the device:
   ```bash
   adb shell mkdir -p /sdcard/tmp
   adb push ~/tmp/<apk name> /sdcard/tmp/<apk name>
   ```
The user installs it manually from `/sdcard/tmp/`.

## Committing & pushing — STRICT RULE

**Never commit or push on your own.** Develop and build; the user tests on their phone. Only after the
user tests and explicitly instructs do you `git commit` and `git push` to the fork (`origin`).

## Rebasing onto a new upstream version

When the user instructs a rebase to a new upstream release:
1. Sync `main` with upstream and fetch the new tag/commit.
2. Rebase (or replay) the `custom` branch onto the new upstream point, resolving conflicts. Our changes
   are intentionally small and isolated (`gradle.properties`, the versioning/`buildFoss`/namespace edits
   in `app/build.gradle.kts`, the `app_launcher_name` string, plus any feature work).
3. Update `VERSION_NAME`/`VERSION_CODE` to the new upstream values and reset `BUILD_NUMBER=1`.
4. Build the new `+1` and continue normal `+N` development from there.

## Architecture (upstream Fossify Calendar)

- **Entry/UI:** `MainActivity` hosts month/week/day/year/event-list views (fragments under
  `fragments/`); event/task editing in `activities/` (e.g. `EventActivity`, `TaskActivity`).
- **Persistence:** Room database (`libs.bundles.room`, KSP-generated; schemas under `app/schemas`).
  Models in `models/`, DAOs/helpers under `helpers/`.
- **Config:** `Config` (SharedPreferences wrapper) accessed via a `context.config` extension.
- **Widgets:** monthly / event-list / today's-date app widgets (declared in `AndroidManifest.xml`).
- **Fossify Commons:** heavy reliance on `org.fossify:commons` for base activities, theming, dialogs,
  and shared utilities (e.g. `ensureBackgroundThread`). Check commons source when base behavior is unclear.

## Key configuration files

- `gradle.properties` — fork identity + versioning (`APP_ID`, `APP_NAMESPACE`, `VERSION_NAME`, `VERSION_CODE`, `BUILD_NUMBER`).
- `app/build.gradle.kts` — Android config, flavors, signing, the fork version computation, and the `buildFoss` task.
- `gradle/libs.versions.toml` — single source of truth for dependency / SDK / Java versions.
- `keystore.properties`, `local.properties` — machine-local, **gitignored** (signing creds + SDK path).

## Patched Fossify Commons (anti-tamper removed)

This fork builds against **our patched Fossify Commons**, not the upstream binary. Upstream Commons
6.1.x shows a "You are using a fake version of the app…" dialog (and silently breaks "Customize
colors") whenever the installed app id is not `org.fossify.*` — always the case for us (`shiroikuma.*`).

- **Source:** the `shiroikuma-commons` fork (`~/git/shiroikuma-commons`, branch `custom`), which strips
  Commons' anti-tamper "fake version" / sideloading checks out entirely.
- **Delivery:** published to the local Maven repo, consumed as `commons = "6.1.6-sk1"` in
  `gradle/libs.versions.toml` (`mavenLocal()` is already a repository in `settings.gradle.kts`).
- Because Commons itself no longer nags, this app carries **no** in-app workaround — no `getPackageName`
  spoof, no `SIDELOADING_FALSE`, no `res/raw/keep.xml`.

**On a fresh machine, or after an upstream bump changes the Commons version — republish before building:**

```bash
cd ~/git/shiroikuma-commons
git checkout <new-commons-tag>     # then re-apply the strip patch (remove the modded-app/sideloading checks)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :commons:publishToMavenLocal -PVERSION=<ver>-sk1
```

Then set this app's `commons` pin to `<ver>-sk1`. The patched AAR lives only in `~/.m2`, not in the repo.
