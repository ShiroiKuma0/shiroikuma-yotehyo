---
name: build-apk
description: Build the signed foss release APK with the buildFoss Gradle task. Build PROACTIVELY as soon as a coherent code change is complete and compiles — do NOT wait for the user to say "build it". But NEVER send the APK to the phone on your own — ALWAYS ask the user first whether to scp it to skhw (first choice) or adb push it, no exceptions. Also use whenever the user explicitly asks to build the app, build the APK, make a release build, or build and send to the phone.
---

# Build the foss release APK and optionally send to phone

## When to build

Build **proactively** — do NOT wait for the user to say "build it" and do NOT ask "want me to
build?" first. As soon as you have finished a coherent set of code changes and they compile, run the
steps below. Don't rebuild after every tiny intermediate edit — build once the change is in a
testable state.

This removes only the *ask-before-build* wait. It does **not** touch sending to the phone: the
transfer (`scp` to skhw or `adb push`) is **always** gated on the user's explicit answer for *this*
build (step 3) — never send on your own, never assume a prior answer carries over, never batch a
transfer in with the build. The repo's commit/push rules are unchanged.

## Steps

1. **Note the output filename.** Read the current version and build number:
   - `grep -E 'VERSION_NAME|BUILD_NUMBER' gradle.properties`
   - The APK will be `shiroikuma-yotehyo_<VERSION_NAME>+<BUILD_NUMBER>_arm64-v8a.apk`, using the `BUILD_NUMBER` value **before** the build (the task bumps it afterward).

2. **Build** (the toolchain needs JDK 21 — the default `java` on PATH is too old for AGP 9.x):
   - `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFoss < /dev/null`  (the `< /dev/null` guarantees it never blocks on stdin — see caveat)
   - This runs `assembleFossRelease`, copies the signed APK to `~/tmp/<apk name>`, and auto-increments `BUILD_NUMBER` in `gradle.properties`.
   - The task prints `>>> ~/tmp/<apk name>`; use that line to confirm the exact filename, and confirm `BUILD SUCCESSFUL`.

3. **Always ask** (via AskUserQuestion) how to transfer the APK to the phone — every build, no assuming. Options, in this order: "Scp to skhw" (FIRST choice) / "adb push" / "No, just build".

4. **Transfer per the answer** (do NOT rely on the buildFoss task's own prompt — see caveat):
   - **Scp to skhw** — invoke the global **scp** skill (copies the newest APK in `~/tmp/` to `skhw:~/tmp/`). If skhw is unreachable (its tunnel is served by the phone's sshd and may be down), report that and offer the adb push instead.
   - **adb push:**
     - `adb devices` — confirm a device is connected.
     - `adb shell mkdir -p /sdcard/tmp`
     - `adb push ~/tmp/<apk name> /sdcard/tmp/<apk name>`
     - Verify: `adb shell ls -l /sdcard/tmp/<apk name>` (size should match the local file in `~/tmp`).
     - Never install the APK — only push it to `/sdcard/tmp/`. The user installs it manually.

## Caveat — why transfer directly instead of via the task

The `buildFoss` task (`app/build.gradle.kts`) has an interactive `read -p "Push to phone? (y/n)"` prompt, but it runs in a subprocess of the **Gradle daemon**, whose stdin/stdout are not connected to Claude's Bash tool. Piping `y`/`n` into `./gradlew buildFoss` does not reach the prompt — the daemon subprocess gets EOF, silently skips the push, and its output is invisible. So the task's prompt is effectively dead under this tooling: ask the user via AskUserQuestion and run the `scp` / `adb push` yourself.

## Signing

Release signing is non-interactive: `app/build.gradle.kts` reads credentials from `keystore.properties` (falling back to `SIGNING_*` env vars). This fork reuses the `shiroikuma-denwa` keystore (`/home/shiroikuma/.android-keystores/shiroikuma-denwa.jks`, alias `denwa`). If neither `keystore.properties` nor the env vars are present the build is unsigned and the APK will not install.

## Prerequisite — patched Commons in mavenLocal

This app builds against our patched Fossify Commons (`commons = "6.1.6-sk2"` in
`gradle/libs.versions.toml`), resolved from `mavenLocal()` (`~/.m2`). On this machine it is already
published, so `buildFoss` just works. **On a fresh machine, or if `~/.m2` was cleared**, the build fails
with `Could not resolve org.fossify:commons:6.1.6-sk2` — publish it first:

```bash
cd ~/git/shiroikuma-commons && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  ./gradlew :commons:publishToMavenLocal -PVERSION=6.1.6-sk2
```

See the `shiroikuma-commons` repo's CLAUDE.md for the patch details.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` / "Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
