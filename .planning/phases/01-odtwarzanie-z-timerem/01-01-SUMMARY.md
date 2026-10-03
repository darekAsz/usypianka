---
phase: 01-odtwarzanie-z-timerem
plan: 01
subsystem: infra
tags: [gradle, agp-9, kotlin, jetpack-compose, material3, media3, android]

requires: []
provides:
  - Gradle 9.6.0 wrapper pinned by sha256, runnable from the repo root on Windows
  - Version catalog (AGP 9.4.1, Kotlin 2.4.20 via Compose plugin, Compose BOM 2026.09.00, Media3 1.11.1, coroutines 1.11.0)
  - Single-module :app (namespace and applicationId pl.usypianka, compileSdk 37, targetSdk 36, minSdk 26)
  - Dark platform theme Theme.Usypianka (windowBackground 0xFF0B0B0D) and edge-to-edge MainActivity with NightColors scheme
  - Android SDK platform 37.0 installed on the build machine
affects: [01-02, 01-03, 01-04, 01-05, 01-06, 01-07, phase-02, phase-03]

plan_head_before: b7bce27b0f627a76677544fe9d93509553fee4b9
plan_head_after: c9958dda3b399e7325ba8c795885be0f708548e6

actuals:
  tokens: 3500
  tasks: 2
  commits: 2

tech-stack:
  added: [Gradle 9.6.0, AGP 9.4.1, Kotlin 2.4.20 (Compose compiler plugin), Compose BOM 2026.09.00, Material3 1.4.0, Media3 1.11.1, Activity Compose 1.13.0, Lifecycle 2.11.0, kotlinx-coroutines 1.11.0, JUnit 4.13.2]
  patterns:
    - "AGP 9 built-in Kotlin: only the Compose plugin is applied; it lifts embedded KGP 2.2.10 to 2.4.20 (no kotlin-android, no kapt)"
    - "Dark-from-first-frame: platform theme windowBackground plus Compose NightColors, no pure white anywhere"
    - "Repositories declared only in settings (FAIL_ON_PROJECT_REPOS), google() content-filtered"

key-files:
  created:
    - .gitignore
    - settings.gradle.kts
    - build.gradle.kts
    - gradle.properties
    - gradle/libs.versions.toml
    - gradle/wrapper/gradle-wrapper.properties
    - gradle/wrapper/gradle-wrapper.jar
    - gradlew
    - gradlew.bat
    - app/build.gradle.kts
    - app/src/main/AndroidManifest.xml
    - app/src/main/res/values/themes.xml
    - app/src/main/res/values/strings.xml
    - app/src/main/kotlin/pl/usypianka/MainActivity.kt
  modified: []

key-decisions:
  - "Kept applicationId and namespace pl.usypianka as locked by the user (one-way door at first Play upload)"
  - "Omitted generateLocaleConfig and release minify block in this plan; they arrive in plan 01-07 with resources.properties and proguard-rules.pro"
  - "Generated the Gradle 9.6.0 wrapper in a throw-away directory using the already-cached 9.6.0 distribution, because AGP 9.4 refuses older Gradle in-tree"

patterns-established:
  - "Kotlin sources live in app/src/main/kotlin/"
  - "NightColors scheme in MainActivity.kt for now; plan 01-06 moves it to ui/theme/Theme.kt"

requirements-completed: [UI-01]

coverage:
  - id: D1
    description: "Toolchain reproducible from repo root: SDK platform 37, Gradle 9.6.0 wrapper pinned by sha256, root build configures"
    verification:
      - kind: other
        ref: "./gradlew.bat --version | grep 'Gradle 9.6.0' && ./gradlew.bat help (BUILD SUCCESSFUL)"
        status: pass
    human_judgment: false
  - id: D2
    description: "assembleDebug and lintDebug green; Kotlin 2.4.20 lifted by Compose plugin; merged debug manifest has no INTERNET permission and allowBackup=false"
    requirement: UI-01
    verification:
      - kind: other
        ref: "./gradlew.bat assembleDebug lintDebug; ./gradlew.bat buildEnvironment | grep 2.4.20; merged manifest grep checks"
        status: pass
    human_judgment: false
  - id: D3
    description: "UI-01 cold start: first frame is near-black with no white flash, light-grey (not pure white) title, dark system bars"
    requirement: UI-01
    verification: []
    human_judgment: true
    rationale: "Visual cold-start behaviour on a real device needs a human watching a screen recording; the theme values are asserted statically (windowBackground #FF0B0B0D, no #FFFFFFFF) but the absence of a flash is not machine-checkable"

duration: ~20min (approx.; start timestamp not captured because the session was interrupted by an API limit and resumed)
completed: 2026-10-03
status: complete
---

# Phase 1 Plan 01: Toolchain and dark app shell Summary

**Checksum-pinned Gradle 9.6.0 wrapper plus AGP 9.4.1 / Kotlin 2.4.20 / Compose single-module project producing an installable app that opens on a near-black edge-to-edge window**

## Performance

- **Duration:** ~20 min (approximate)
- **Completed:** 2026-10-03T15:46Z (last task commit)
- **Tasks:** 2
- **Files modified:** 14 created, 0 modified (plus SDK platform 37.0 installed outside git)

## Accomplishments
- Installed `platforms;android-37.0` (needed because Compose 1.12.1, core 1.19.1 and lifecycle 2.11.0 require compileSdk 37) and bootstrapped the Gradle 9.6.0 wrapper with `distributionSha256Sum` pinned.
- Version catalog and root build exactly as verified in RESEARCH; `buildEnvironment` shows `kotlin-gradle-plugin:2.2.10 -> 2.4.20`, no `kotlin-android` plugin and no kapt.
- `:app` builds (`assembleDebug lintDebug` green, 15 MB debug APK); Theme.Usypianka sets windowBackground and colorBackground to `#FF0B0B0D`, MainActivity uses `enableEdgeToEdge`, `WindowInsets.safeDrawing` and `NightColors` with no pure white.
- Merged debug manifest: no INTERNET permission (only the dependency-added ACCESS_NETWORK_STATE and WAKE_LOCK), `allowBackup="false"`.

## Task Commits

1. **Task 1: Toolchain bootstrap and Gradle build** - `f904960` (chore)
2. **Task 2: Installable dark app** - `c9958dd` (feat)

**Plan metadata:** committed separately after this file (docs: complete plan)

## Files Created/Modified
- `gradle/wrapper/*`, `gradlew`, `gradlew.bat` - Gradle 9.6.0 wrapper, sha256-pinned
- `gradle/libs.versions.toml` - pinned version catalog
- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties` - root build, repository policy
- `.gitignore` - build output, local.properties, IDE files, tools venv/out, keystores
- `app/build.gradle.kts` - module config, noCompress flac/wav, dependencies
- `app/src/main/AndroidManifest.xml` - application and launcher activity, no permissions, no fixed orientation
- `app/src/main/res/values/themes.xml`, `strings.xml` - dark theme, brand name
- `app/src/main/kotlin/pl/usypianka/MainActivity.kt` - edge-to-edge dark Compose content

## Decisions Made
- applicationId/namespace `pl.usypianka` kept as locked by the user.
- `generateLocaleConfig` and release minification deferred to plan 01-07 as the plan specifies.
- Wrapper generated in a throw-away directory using the cached 9.6.0 distribution (it was already present locally, so the 9.1.0 fallback was not needed).

## Deviations from Plan

None - plan executed exactly as written.

Notes that are not deviations: the SDK manager installed the platform into `C:\Android\platforms\android-37.0` (directory name carries the `.0`, the verify grep for `android-37` still matches). A `local.properties` (`sdk.dir=C\:\\Android`) was created in the worktree for the build; it is gitignored and not committed.

## Issues Encountered
- The shell sandbox of the worktree agent refused compound commands that mix git with other constructs, so git commands and checks were split into separate calls. No effect on the result.
- The first run was interrupted by an API session limit after both task commits; on resume the HEAD assertion was re-run (base b7bce27 is an ancestor of HEAD) and both tasks' `<verify>` commands were re-run green.

## User Setup Required

None - no external service configuration required. The UI-01 human check (cold start on a phone, `./gradlew.bat installDebug`) remains for end-of-phase UAT.

## Known Stubs

None. MainActivity shows only the app name by design; the real Player screen arrives in later Phase 1 plans.

## Next Phase Readiness
- Toolchain and `:app` module are ready for plan 01-02 onwards (Media3, Compose and coroutines dependencies already declared).
- Plan 01-03 adds the playback permissions and service; plan 01-07 adds launcher icon, locale config, proguard and the values-v31 splash variant.

## Self-Check: PASSED

All 14 files in key-files.created exist on disk; commits f904960 and c9958dd exist; `./gradlew.bat assembleDebug lintDebug` and the plan's two `<verify>` chains re-run green after resume.

---
*Phase: 01-odtwarzanie-z-timerem*
*Completed: 2026-10-03*
