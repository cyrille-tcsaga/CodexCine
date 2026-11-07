# Decisions

Non-trivial choices made while building Codex Ciné, including every place the result deviates from the
implementation brief.

## Deviations from the brief

| Brief | What was done | Why |
|---|---|---|
| Sample stream is the Zeitgeist MP4 from the template | `SampleStreamResolver` uses `exoplayer-test-media-0/BigBuckBunny_320x180.mp4` | The template URL now answers HTTP 403, so it could not "verify the player end to end". The clip is Media3's own public test media. |
| Project is the untouched template | Partly migrated code was found, audited and mostly rewritten | See "Starting point" below. |
| Each row ends with "Voir tout" | Not on "Ma liste" or "À propos" | "Ma liste" already shows everything and has no paging. "À propos" is a single card. |
| Package layout | Added `ui/common` and `ui/details/SeasonCardPresenter`, `ui/error/AboutActivity` | Shared hosts, presenters and helpers would otherwise be copied into each screen. |
| `kotlinx-serialization` "at least 1.9.x", latest stable | Pinned to 1.9.0, not 1.12.0 | See "Versions". |

## Starting point

The brief describes an untouched template. The repository actually held an earlier partial attempt: a data
layer, part of the UI, and the old template files side by side, without a commit. The code was kept where
sound (DTOs, API interface, domain models) and otherwise rewritten. Defects found in it:

- Home rows only appeared once all of them had loaded, instead of as they arrived.
- `loadHome` re-subscribed to the list on every `onResume`, leaking collectors and reloading every row.
- Filmography was sorted by vote average rather than popularity.
- `safeApiCall` caught `Exception`, swallowing coroutine cancellation.
- A row failure lost its cause, so a rejected token was reported as a generic error.
- Mappers and repositories contained French UI strings.
- "Fall back to similar" only ran when recommendations were null, never when they were empty.
- Trending rows could contain people.

## Architecture

- **Leanback kept on purpose.** It is officially deprecated in favour of Compose for TV. For a personal app
  whose owner wants this exact layout, migrating has a cost and no benefit. No Compose dependency is present.
- **No DI framework.** `AppContainer` is a few lazy properties. Hilt, Koin or Dagger, and the KSP or kapt
  they bring, would cost more build time than the wiring they would save.
- **minSdk 28, Java/Kotlin 17.** API 28 is the oldest target device. `java.time` is available from API 26, so
  no desugaring is needed.
- **`FragmentHostActivity`.** Each activity hosts one fragment in `android.R.id.content`, and an error replaces
  it. Retrying creates a new fragment, whose new ViewModel starts loading. No screen needs retry logic, and
  a retry cannot race the old error state.
- **Errors keep their data in fragment arguments**, not in callbacks, so the screen survives process death.
- **A rejected or missing token shows "Retour", not "Réessayer".** The token is compiled in, so a retry can
  never succeed.
- **`AboutActivity` instead of a fragment transaction.** `BrowseSupportFragment` uses the same back stack for
  its headers transition, and adding entries to it risks corrupting that state.
- **Grid and search ViewModels take a loader function**, so their paging and debounce logic is testable
  without implementing the whole Retrofit interface.
- **Details builds its overview row before the network answers.** Play only needs the intent extras, and the
  shared element transition needs a target immediately, or the postponed transition holds the window back.

## Images and memory (2 GB RAM)

- Posters `w342`, stills `w780`, backgrounds `w1280`, profiles `w185`. `original` is not offered by `ImageUrls`.
- Cards also decode as RGB_565 with an explicit size, beyond what the brief required: posters are opaque, and
  this halves their memory.
- **Backgrounds are copied out of Glide.** `BackgroundManager` cross-fades, so the old bitmap is still drawn
  after the new one is ready. Clearing the Glide request could recycle a bitmap still on screen, which crashes.
  Not clearing would pin a full-screen bitmap per card visited. Owning a copy has neither problem.
- **`BaseCardPresenter` clears through the application context.** Leanback recycles cards during activity
  destruction, where `Glide.with(activity)` throws. This crashed on the device when leaving any screen.
- **`BaseCardPresenter` restores the image view on bind.** `setMainImage(null)` hides it, and Glide writes
  directly to the `ImageView`, so a recycled card would otherwise stay blank.

## Network

- `Retry-After` is honoured up to 15 s. A longer wait returns the response instead of freezing a screen.
- Logging is `debugImplementation` with per-variant source sets, so release builds do not ship the logging
  interceptor. The `Authorization` header is redacted.
- The token is only ever sent as a header. `InterceptorsTest` asserts this.
- TMDB's French data contains the certification `NR` ("not rated"), which is treated as no certification.

## Versions

Checked on Google Maven and Maven Central on 2026-11-07. The newest stable release was used unless noted.

| Dependency | Version | Note |
|---|---|---|
| AGP | 9.5.1 | Unchanged |
| Gradle | 9.6.0 | Unchanged. 9.9.0 exists; not needed. |
| Kotlin | 2.2.10 | Must equal what AGP bundles (`./gradlew buildEnvironment`) |
| kotlinx-serialization-json | 1.9.0 | 1.10+ is built for Kotlin 2.3+, 1.12 for 2.4, ahead of the bundled compiler |
| core-ktx | 1.19.1 | |
| leanback | 1.2.0 | 1.3.0 is still alpha |
| fragment-ktx | 1.9.1 | |
| lifecycle | 2.11.0 | |
| kotlinx-coroutines | 1.11.0 | |
| retrofit, converter | 3.0.0 | |
| okhttp, logging-interceptor | 5.5.1 | |
| glide | 5.0.9 | Above the brief's 4.16.0 minimum |
| media3 | 1.11.1 | Above the brief's 1.10.1 minimum |
| datastore-preferences | 1.3.0 | |
| junit | 4.13.2 | |

## Lint

**No lint check is suppressed.** `lintDebug` reports 0 errors. These warnings remain, and are accepted:

| Warning | Why it stays |
|---|---|
| `NewerVersionAvailable` (serialization library and plugin) | Deliberate pin, see "Versions" |
| `AndroidGradlePluginVersion` (Gradle 9.9.0) | Not needed, and the brief fixes this toolchain |
| `IconMissingDensityFolder` | The brief asks for an xhdpi-only banner, which is what Android TV expects |

## Other

- The details overview uses `default_background` rather than the template's `selected_background`: white text
  on amber is hard to read at TV distance.
- All app strings are French regardless of device language. Strings inside Leanback itself (the search hint,
  for example) follow the system language, so they are French on a French-configured box.
- Voice search starts automatically when Search opens, which is Leanback's default. If the permission is denied
  or no recogniser exists, nothing happens and the keyboard remains available.
- Release minification uses `optimization { enable = true }`. No custom keep rule was needed; the libraries'
  consumer rules were enough. A minified build was exercised on an API 36 TV emulator: home rows, details,
  "Ma liste" toggle, season, person and the no-source screen.
- `assembleRelease` output is unsigned, since no signing configuration was requested. See the README.
- `allowBackup` stays `true`, as in the template: "Ma liste" is the only user data, and the token lives in the
  APK, not in app data.
