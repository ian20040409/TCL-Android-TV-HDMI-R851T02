# Compose for TV migration — home and App list

The launcher home screen and App list use Google’s official `androidx.tv.material3` components. This is TV Material 3, **not** the mobile Material 3 Expressive theme or the proprietary Google TV system interface.

## Design policy

- Use the stock TV `MaterialTheme` with `darkColorScheme()` and default typography/shapes.
- Use stock TV `Card`, `ListItem`, `Checkbox`, `Button`, `OutlinedButton`, `Icon`, `Text` and `Surface` components.
- Keep the stock component colors, borders, shapes, glow and focus scaling; use only theme-provided color roles when a static information surface needs hierarchy.
- Customize only content, layout/spacing and navigation required by this launcher.
- Countdown/default-badge updates must not steal focus. Explicit Activity entry/resume and numeric shortcuts may request focus.

`LauncherScreen.kt` owns presentation and D-pad navigation. `MainActivity.kt` still owns countdown scheduling, window-focus/lifecycle checks, CEC coordination, shortcut dispatch and Activity launching. The former hand-built `MainViewBuilder.kt` has been removed.

## Preserved behavior

- Initial/resume focus on the configured default HDMI port.
- Left/right moves among HDMI 1–3, without wrapping at the ends.
- Up goes to Settings; down goes to Apps. Returning from either goes to the most recently focused HDMI port; explicit focus requests still take priority.
- D-pad navigation cancels the countdown without consuming navigation events.
- Short OK launches HDMI; held OK sets the default port and cancels the countdown.
- Numeric keys select and launch HDMI; Input cycles from the focused port (or default on a noncard control).
- Menu/Settings opens the existing settings page; Back does not finish the Home launcher.
- The countdown text retains its mouse/touch settings shortcut without becoming another D-pad stop.

## App list migration

`AppListScreen.kt` replaces the custom View header, selectors, focus animations and ListView adapter with stock TV Material 3 buttons, list items and selection indicators in a lazy list. The app menu and batch menu use Compose `Dialog` with stock TV surfaces, text and buttons, including Cancel; Android package-management confirmation/details screens remain system-owned.

`AppListActivity.kt` retains package discovery, TV-entry priority, locale sorting, recent history (maximum 8), TV/mobile/system/frozen groups, cached launch components, launch fallbacks, accessibility exemptions, preferences, Shizuku operations and auto-open scheduling/lifecycle checks.

Preserved interactions:

- Short OK opens an enabled app, or toggles package selection in batch mode. Frozen apps stay focusable and expose management options; opening them retains the unfreeze-first message.
- Held OK opens the app menu, or the batch menu in selection mode, without launching on release.
- App menu: Open, Batch Select, Set/Clear Auto-Open, uninstall for non-system apps, freeze/unfreeze for disableable system apps, and App Info.
- Batch menu: Freeze, Unfreeze and Select All, with the existing result messages and Shizuku failure behavior.
- Up from the first app goes to Settings/Batch Actions; header Left/Right is bounded; Down returns to the last list row, scrolling it into composition if needed. Sections are not D-pad stops.
- Menu/Settings opens settings or batch actions. Back cancels an active countdown first, exits selection next, otherwise returns to HDMI selection with the existing intent flags/extra. Dialog Back/Cancel only closes the dialog and restores focus.
- Countdown and icon/badge updates do not steal focus. Reloads hide stale rows while loading; headers remain operable and focus is restored when loading completes unless the user navigated the header.
- Icons load only for visible rows on the existing background executor, are cached per package, and are cleared on stop. Stale results cannot refill an evicted cache; transient load failures get one bounded retry.

App-list loading is shown as localized TV Material text; TV Material does not provide a corresponding progress-indicator component here. No mobile Material 3 dependency or custom spinner was introduced.

## Deliberately unchanged

- `HdmiViewerActivity` and its native `TvView`, signal overlay and hardware-session handling.
- CEC, wake/accessibility, Shizuku, boot and power logic.
- SharedPreferences file names, keys and existing settings.
- Leanback settings/subsettings and OOBE. These are later migration stages, so their appearance is not yet unified with the new screens.
- Existing window themes/Activity transition settings, including the opaque HDMI player window.

## Build and automated validation

AGP 9 uses built-in Kotlin. The Compose compiler plugin is aligned with its Kotlin 2.2.10 dependency; do not additionally apply `org.jetbrains.kotlin.android`. Dependencies are centralized in `gradle/libs.versions.toml`, including TV Material 1.1.0.

Run with a compatible JDK (validated with JDK 21):

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest :app:assembleRelease
```

The release task validates R8/resource shrinking and produces an **unsigned** APK unless release signing is configured separately. Never replace the existing release signing key when upgrading an installed app.

`LauncherScreenTest` renders the screen in isolation, without launching hardware HDMI or system settings. It covers initial/default focus, D-pad navigation and boundaries, recomposition preserving focus, explicit focus requests, short/held OK and action callbacks. These tests do **not** prove Activity countdown/CEC lifecycle behavior or TCL hardware compatibility.

`AppListScreenTest` covers categorized/duplicate entries, loading and empty states, bounded header/list navigation, lazy-list scrolling, focus preservation, short/held OK, frozen labels, auto-open badges, selection and scrollable dialog actions/Back/Cancel. `AppListActivityTest` adds non-destructive real-Activity checks for generated menu options, select-all/cancellation, auto-open preference changes, foreground lifecycle/cache reload and countdown cancellation. Tests temporarily override and restore the affected preferences; they do not uninstall, freeze, or launch external apps.

These tests do not validate privileged Shizuku actions, actual third-party app launches or TCL standby/firmware behavior.

## Required TCL device checks before release

Use the same TCL 65C715 firmware and settings for comparisons with the previous release:

- [ ] Cold boot, Home return and standby wake reach an operable screen without white flashes or focus loss.
- [ ] Countdown starts only while resumed and window-focused; dialogs/external settings pause it.
- [ ] D-pad cancellation, Settings/App-list returns and new Home intents retain the established countdown behavior.
- [ ] Numeric shortcuts, Input cycling and held OK work with the physical remote.
- [ ] CEC overrides suppress the default-port countdown; no duplicate or incorrect HDMI launches occur.
- [ ] HDMI 1–3, no-signal screen, retry behavior and sleep remain unchanged.
- [ ] 4K HDR/Dolby Vision and native TCL picture settings show no black screen or residual overlays.
- [ ] App list: all category counts/recents, long-press menus, uninstall/App Info, freeze/unfreeze and batch actions with authorized and unauthorized Shizuku.
- [ ] App list: set/clear auto-open, wake countdown cancellation, first-Back cancellation, returning from external apps/settings and scrolling many apps without lost focus.
- [ ] Measure release cold-start/wake latency, PSS memory, APK size and repeated focus-navigation smoothness.

Compose does not fix firmware HWC/sideband issues. Do not wrap `TvView` in a Compose navigation screen merely for visual consistency.
