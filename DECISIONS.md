# Decisions

Non-trivial choices made while building Codex Ciné, including every place the result deviates from
the
implementation brief.

## Deviations from the brief

| Brief                                                   | What was done                                                                    | Why                                                                                                                                |
|---------------------------------------------------------|----------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| Sample stream is the Zeitgeist MP4 from the template    | `SampleStreamResolver` uses `exoplayer-test-media-0/BigBuckBunny_320x180.mp4`    | The template URL now answers HTTP 403, so it could not "verify the player end to end". The clip is Media3's own public test media. |
| Project is the untouched template                       | Partly migrated code was found, audited and mostly rewritten                     | See "Starting point" below.                                                                                                        |
| Each row ends with "Voir tout"                          | Not on "Ma liste" or "À propos"                                                  | "Ma liste" already shows everything and has no paging. "À propos" is a single card.                                                |
| Package layout                                          | Added `ui/common` and `ui/details/SeasonCardPresenter`, `ui/error/AboutActivity` | Shared hosts, presenters and helpers would otherwise be copied into each screen.                                                   |
| `kotlinx-serialization` "at least 1.9.x", latest stable | Pinned to 1.9.0, not 1.12.0                                                      | See "Versions".                                                                                                                    |

## Starting point

The brief describes an untouched template. The repository actually held an earlier partial attempt:
a data
layer, part of the UI, and the old template files side by side, without a commit. The code was kept
where
sound (DTOs, API interface, domain models) and otherwise rewritten. Defects found in it:

- Home rows only appeared once all of them had loaded, instead of as they arrived.
- `loadHome` re-subscribed to the list on every `onResume`, leaking collectors and reloading every
  row.
- Filmography was sorted by vote average rather than popularity.
- `safeApiCall` caught `Exception`, swallowing coroutine cancellation.
- A row failure lost its cause, so a rejected token was reported as a generic error.
- Mappers and repositories contained French UI strings.
- "Fall back to similar" only ran when recommendations were null, never when they were empty.
- Trending rows could contain people.

## Vidzy player

Everything here was checked against the live site on 2026-11-07. The embed is a video.js player with
the
Nuevo plugin, VAST ad support and `pop.js`, on a file host at `vidzy.cc`.

### Why a WebView and an injected bridge

**Not extracting the stream URL.** Vidzy only documents the embed. Scraping the file host's page for
a stream
URL would depend on its private markup and on signed URLs, and would break the moment either
changes.

**A bridge in every frame.** The `<video>` is in a cross-origin frame, out of reach of
`evaluateJavascript`.
`addDocumentStartJavaScript` and `addWebMessageListener` reach it, and `addJavascriptInterface` is
never used.

**Origin rule `*` with a nonce.** The host chain is unknown and has to survive changes. The nonce is
not a
real secret: every frame gets the same script, so a hostile frame could read it from what the script
posts.
It only rejects stray and stale messages. The real protection is that the channel carries **state
only**:
messages are schema-checked and whitelisted, no message can navigate or run anything, and URLs from
the page
are never used. The worst a hostile frame can do is fake the progress display or the saved position.
 
**`autonext` and `info` are off.** `autonext` navigates by itself, which would bypass progress
saving and the
overlay. `info` would duplicate the overlay's title.

### Deviations from the follow-up brief

| Brief                                                                          | What was done                                                        | Why                                                                                                                                                                                                                                                         |
|--------------------------------------------------------------------------------|----------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Next episode and retry reuse the same WebView, with a fresh nonce              | **Each load gets a new WebView and bridge**, in the same activity    | Removing the listener and script while a page ran crashed the whole process (`SIGSEGV` inside Chromium, 12 to 100 s later), reproduced twice on the API 36 emulator. A fresh nonce needs a new script, and a new WebView also returns memory on a 2 GB box. |
| Keep the `ScriptHandler` and call `remove()` in `onDestroyView`                | Not called: bindings die with the WebView                            | Same crash.                                                                                                                                                                                                                                                 |
| Web errors end on an error screen                                              | An in-player panel with "Réessayer" and "Retour"                     | `ErrorSupportFragment` has one button. The provider-unreachable screen before the player keeps it, and Back acts as "Retour" there.                                                                                                                         |
| The first key press only reveals the overlay, except media keys and Left/Right | OK when hidden toggles play/pause and reveals                        | Section 7.1 contradicts the table in 7.2, and the more specific table was followed.                                                                                                                                                                         |
| `blocklist.txt` holds well-known hosts                                         | Also two hosts seen on the embed page                                | `fencingnora.click` and `s0-greate.net` were loading ad scripts.                                                                                                                                                                                            |
| Message protocol                                                               | Added `bf` (end of the buffered range) and the event `mutedAutoplay` | The buffered bar and the muted hint have no other data source.                                                                                                                                                                                              |
| Watchdog: 25 s after `onPageFinished`                                          | Also 60 s from the start of the load                                 | A stuck ad script can hold back `onPageFinished` indefinitely.                                                                                                                                                                                              |
| Basic-mode keys                                                                | Also focuses the first iframe once the page has loaded               | Synthetic keys go to the focused document, and the video is in a nested frame.                                                                                                                                                                              |
| Continue Watching opens the player                                             | Resumes without the prompt                                           | "Clicking resumes playback directly". The prompt remains when starting from the details screen.                                                                                                                                                             |
| Next episode: `(s, e + 1)` if it exists                                        | Also not if its air date is in the future                            | TMDB lists announced episodes that cannot be available yet.                                                                                                                                                                                                 |

### Other player choices

- **Typed failure.** A resolver throws `StreamServiceException` when the provider is unreachable,
  and returns
  null when it has no source. `PlaybackViewModel` maps these to two different screens.
- **`PlaybackResolverFragment`.** It resolves first, then replaces itself with the player that fits
  the source.
  Each player then only knows how to play, and its source lives in its arguments.
- **Back uses `OnBackPressedDispatcher`.** With a target SDK this recent, Back may not arrive as a
  key event.
- **The Vidzy call runs on `Dispatchers.IO`, with its client built lazily.** Building them on the
  main thread
  from the details screen caused an ANR on the emulator.
- **Progress writes use an application scope**, single-threaded so that a late periodic save cannot
  bring back
  an entry that was just removed as finished. Quitting waits for the write, so the home row is
  current.
- **`onSafeBrowsingHit` backs out.** The default interstitial needs a pointer, so a flagged host
  would trap the
  user. A flagged main frame ends on the error panel.
- **A video `error` event only counts after 8 s without progress.** Ad plugins can use the same
  element.
- **`ActiveFrameTracker` drops a frame after 5 s of silence.** A reloaded frame gets a new reply
  proxy, so the
  old one would otherwise stay active and swallow every command.
- **Debug harness.** Its pages and asset loader exist only in `src/debug`, so release builds can
  neither serve
  nor navigate to them. The harness adds 7 presses (broken page) and 9 presses (basic mode) to the
  brief's 5.

### Playback smoothness (follow-up)

Reported on the TV: playback stops, buffers, jumps back a second or two, and resumes. The emulator decodes
in software and is too slow to reproduce it faithfully, so these are the findings and what changed.

- **A TLS error on any resource stopped playback.** `onReceivedSslError` fired for ads and trackers with
  certificates the box rejected, and every one of them ended on "La connexion à la source n'est pas
  sécurisée". The request is still always cancelled, but only the displayed page failing is fatal now
  (`isFatalSslError`). The refused host is logged.
- **The bridge kept rescanning the page.** video.js changes the DOM several times a second, and each change
  triggered a full walk of the document every 500 ms for the whole film, on the thread that also feeds the
  video. The `MutationObserver` now disconnects once a video of at least 60 s is found.
- **The page demultiplexes HLS in JavaScript** (video.js VHS, `blob:` source) unless the agent names a TV
  (`smart-tv`, `googletv`…), in which case it lets the WebView play the HLS itself. `WEB_PLAYER_TV_MODE`
  adds `SMART-TV` to the agent. It is off by default: on the emulator one run advanced 2.5 times further
  than without it, and another stalled, so it has to be compared on the real box. The token describes the
  device class and names no browser, so the rule against impersonating a browser still holds.
- **No native playback of the extracted stream.** The page does expose its HLS URL, and Media3 played it,
  but the host only serves the first segments without a `gk` key. The page obtains that key from
  `gate.php` by sending a browser fingerprint (canvas and WebGL hashes, screen, CPU count). Producing one
  outside a browser would mean forging that fingerprint to get past a check built to stop exactly this,
  which was ruled out. The prototype was removed rather than left as dead code.
- **Debug diagnostics.** In debug builds the script logs, every 15 s, the playback mode, dropped frames and
  the HTTP status of the stream segments (`CodexDiag` in logcat).

### Ad panels over the video (follow-up)

- **What it was.** A white panel "Confirm you're not a robot" with a QR code, over the video. It is not a
  check: no real anti-bot test asks to scan a code with a phone. The embed (`vidzy.cc`) loads an obfuscated
  ad loader from a random-looking domain (`*.rest`, `*.cfd`, `*.cyou`…), which tries to open an ad tab on the
  first click ("tabup"). The player already refuses that navigation; on a TV the network then shows a panel
  in the page instead, to be followed on a phone.
- **A guard in the injected script, not only the blocklist.** Those domains rotate, so the hosts seen were
  added to `blocklist.txt` but cannot be the fix. The guard, in every frame, hides two things and nothing
  else: an element whose text is the bait ("not a robot", "scan the QR code"…), and an iframe from another
  host laid over at least a fifth of the screen outside the player's own box. Anything holding the `<video>`
  is never touched, and nothing is clicked or answered: hiding is all it does, so no check is bypassed.
- **Tested** on the emulator with panels injected through DevTools: the bait panel and a full-screen foreign
  iframe were hidden, a small toast without the bait and the player's controls stayed, playback went on. The
  real panel could not be made to appear on demand (the network decides when to serve it), so it is still to
  be confirmed on the box. Debug builds log each hidden element (`CodexGuard` in logcat).

### Known limits

- **Mixed content is effectively off.** `MIXED_CONTENT_COMPATIBILITY_MODE` is set as requested, but
  `usesCleartextTraffic="false"` still blocks HTTP from the WebView. It was left strict because
  nothing has
  needed it. A host requiring HTTP would need a network security config.
- **Language cannot be selected**, since no URL parameter is documented. The web player's own Audio
  and
  Subtitles menus need a pointer.
- **The web player's own UI stays**, as asked: its control bar appears briefly under the overlay,
  including a
  download icon that `hide=download` does not remove.
- **Blocklist.** Host-based only. `pop.js` is served by the video host itself, so it still runs, but
  its popups
  have nowhere to go (`onCreateWindow` returns false and main-frame navigation is restricted).
- **`announceForAccessibility`** is deprecated on recent Android versions and was kept because the
  brief asks
  for it and minSdk 28 needs it.

## Visual identity and home redesign

Direction A ("Bobine") was chosen from three proposals: a film strip bent into a C around a play triangle,
night blue and amber, Space Grotesk for titles and Inter for text.

### Assets

- **One source for every brand asset.** `tools/make_brand_assets.py` builds the mark, the horizontal logo,
  the adaptive launcher icon, the splash icon and the 320 × 180 banner from one geometry. The perforations
  are cut out of the strip (even-odd fill), so the mark works on any background. The wordmark is converted
  to outlines, so it needs no font at runtime. Edit the script, not the generated files.
- **Fonts are bundled, not downloaded.** Space Grotesk (500, 700) and Inter (400, 500, 600), SIL OFL 1.1, are
  subset to Latin and the symbols the app shows (★ ✓ × …): about 240 KB in all. A TV box may have no Google
  Play Services for downloadable fonts. Their licences ship in `assets/licenses`.
- **Adaptive launcher icon** on the ink background, with a monochrome layer. With minSdk 28 the old raster
  icons were removed.
- **Splash screen** through `androidx.core:core-splashscreen` 1.2.0, so that it also exists on Android 9. It
  stays up until the first home row is ready, at most 2.5 s.

### Home: sidebar and billboard

Modelled on the reference the owner supplied (a streaming home with a billboard and rows), with a sidebar
instead of a top bar, and no footer.

- **Sidebar = Leanback's headers, as navigation.** Each entry is a `PageRow` (Accueil, Films, Séries, Ma liste)
  that opens its own `RowsSupportFragment` (`HomePageFragment`). Leanback already hides it while the viewer
  is in the rows and brings it back on Left, which is how TV sidebars behave. Pages read the same
  `MainViewModel`, owned by the home fragment, so the catalogue is still loaded once.
- **Billboard** (`BillboardRowPresenter`): a custom first row with the title, year, rating, overview and two
  pill buttons, over the title's artwork (the screen backdrop, behind a scrim). It rotates every 8 s through
  up to six titles, but never while it has the focus, so the button being pressed cannot change title.
  It is built with the first rows that arrive: trending if present, else popular films, else the first
  catalogue row. Inserted later, above rows already shown, it left the selection on the row below it, so the
  page did not open on it. This needed `overview` on `MediaItem` (nullable, list endpoints only).
- **Play on the billboard** starts a film, or a show at the episode last watched, like the details screen.
- **Cards**: titles and details always under the image, straight on the page; rounded images. The selected
  card gets an ivory ring and a slight zoom (Leanback's extra-small factor), with no drop shadow and no
  darkening of the other rows (`ui/common/CardRows.kt`, used by every row and grid). Leanback's defaults, a
  strong zoom with heavy shadows, were judged too much by the owner. `BaseCardView` filters focus and selection out of its drawable state, so the ring is
  set from `setSelected`, as the image's foreground, layered over any overlay such as the progress bar.
  "Continuer à regarder" uses wide stills.
- **"Les plus récents" rows** on Films and Séries: TMDB `discover`, sorted by release (or first air) date,
  up to today so announced titles stay out, with at least 10 votes so that obscure entries do not fill the
  row.
- **Rounded buttons everywhere**: details, error and about screens, player panels. Focus turns a pill
  ivory with dark text, which reads at three metres and does not rely on colour alone.

### Details screen

- **Built like the billboard, not with `DetailsSupportFragment`.** The title's artwork fills the screen behind
  the same scrim as the home; over it, the type, the title, the facts (year, length, outlined age rating,
  rating, outlined audio versions from the provider), the genres, the overview and pill buttons (Lecture,
  Bande-annonce, Ma liste with a + or ✓). Seasons, cast and recommendations follow as ordinary rows. Leanback's
  details template (poster on a band, actions above the title) could not take this layout.
- **The hero updates in place** (`DetailsHeroRow` notifies its bound view) rather than through the adapter,
  so the focused button keeps the focus when the trailer, the list state or the audio versions arrive.
- **No shared poster transition any more.** It animated the card into the template's poster, which no longer
  exists; the screen now opens with the standard activity transition.
- **Cast as round portraits** with the name centred underneath (`BaseCardPresenter(circular = true)`), so
  people read apart from titles.

### "Nouveaux épisodes"

- **Which shows**: those in My List, then those in "Continuer à regarder", then those with a watched episode,
  at most 20. Each costs one `tv/{id}` request without extras, at most 4 at a time, made once per home screen
  and kept; on resume only the local state is applied again, so a show watched meanwhile drops out at once.
- **Which episode**: TMDB's `last_episode_to_air`, if it aired in the last 30 days, is past the furthest
  episode watched, and is not already in "Continuer à regarder". Specials (season 0) are left out.
- **Watched episodes are recorded separately** (`WatchedEpisodesRepository`, DataStore, 200 shows at most),
  because a finished episode leaves the progress list. `WatchProgressRepository.save` records it, so the
  player is unchanged. History starts with this version: episodes finished before it are not known.
- **The click**: a show already started plays the episode; otherwise its details page opens, since jumping
  into season 28 of a show never watched makes no sense.
- **The "Nouveau" tag is drawn** over the still, like the progress bar of "Continuer à regarder":
  `ImageCardView` has no room for an extra view over its image. TMDB's placeholder titles ("Épisode 5") are
  hidden, as they only repeat the number.

### Watched episodes (season screen)

- **Shown on the cards**: a watched episode is dimmed, with an ivory disc and a check, and "Vu" in its
  details; a part-watched one has the progress bar of "Continuer à regarder". The grid is updated in place
  (`BaseGridFragment.replaceItems`, with a diff), so the focus stays on the card just changed.
- **Changed with a long press on OK**, in a short menu: mark (or unmark) the episode, mark every episode up
  to it, mark (or unmark) the season. Only aired episodes are marked; an episode with no date counts as aired.
  A hint under the screen title says so, as a long press cannot be discovered otherwise.
- **The menu is a plain `AlertDialog`** restyled with the brand colours: a list of three items needs no
  `GuidedStepSupportFragment`, and it is native to the remote.
- **Marking an episode watched removes its progress**, so it leaves "Continuer à regarder". The marks feed
  "Nouveaux épisodes", and are written in the app's persistence scope so leaving the screen at once keeps them.

### System "Play next" row

- **The platform API, no library.** `TvContract.WatchNextPrograms` exists since API 26 (min is 28), so
  `androidx.tvprovider` would only add a dependency. It needs `WRITE_EPG_DATA`, a permission granted at
  install without a prompt; the provider only ever returns the app's own rows.
- **What is published**: titles in progress ("continue", up to 10, with their position), and the new episodes of
  shows already started ("new", up to 5). Progress is saved every 10 s while playing; the row follows it to the
  minute only, so the provider is not written to all the time. Every write is off the main thread, and a
  failure is logged, never shown. On a device without Leanback (a phone) nothing is published.
- **A card the viewer removes stays removed** (until the title is watched again): the system broadcast is
  recorded, and rows the launcher hid are also picked up on the next sync, in case the broadcast was missed.
- **Opened through `codexcine://play/{movie|tv}/{id}?s=&e=&t=`** handled by `PlayLinkActivity`, the only
  activity other apps can start. It accepts the two playable types, numbers in range, a show with both season
  and episode, a movie with neither, and a title of 200 characters at most without control characters;
  anything else opens the home. It starts the player with the app's home behind it, so Back stays in the app.
- **Channels (an app row of its own) are not published**: Google TV no longer shows them, and the Mi Box's
  launcher already shows "Play next".
- **Android TV only, not Google TV.** On the Android TV emulator the cards appear in "Play next" and open
  the player. Google TV's "Continue watching" row now takes its content from the Engage SDK, which is
  allowlisted: Google grants access to partners and reviews each APK. Watch Next stays supported on Android TV
  devices (the Mi Box), not on Google TV (the Xiaomi TV Box S). Engage was not pursued for a sideloaded app.

### Season screen

- **Three wide cards to a row** (264 × 148 dp, as on the home). Four of 240 dp were wider than the 960 dp
  screen, so the first column slid off the left edge as the focus moved right.

### Sagas (details screen)

- **TMDB's collections.** A film's `belongs_to_collection` gives the saga; `collection/{id}` its films. The
  row sits right under the hero, titled with the saga and the film's place ("… · film 3 sur 8"), and lists the
  other films in release order, undated (announced) ones last. It is loaded after the page, so it never delays
  it, and a failed request just leaves it out.

### Filters on "Voir tout" grids

- **The filters button takes the search orb's place** in the grid's title bar (`FilterTitleView` is the
  title view; to Leanback the button is the search affordance), so the focus reaches it by going up from the
  grid, as Leanback screens do. A plain `AlertDialog` per choice, like the episode menu.
- **Genre, decade, minimum rating, sort.** With any filter on, the grid comes from `discover/movie|tv`
  (`DiscoverQuery`), keeping the category's meaning: best rated stays sorted by rating with a vote floor, in
  cinemas and upcoming keep their date window (and offer no decade), a genre row keeps its genre. A minimum
  rating or a rating sort asks for enough votes for the rating to mean something. Trending mixes movies and
  shows, which `discover` cannot: it has no filters.
- **Changing filters starts again from page 1**; a page still loading for the old filters is dropped. With
  no result, a toast says so and the focus goes back to the button.

### Searching from outside the app

- **The Assistant's search action and `ACTION_SEARCH`** open the search screen, which searches the query at
  once instead of listening ("cherche Dune sur Codex Ciné"). The query is cleaned (control characters, 100
  characters at most) and only ever searched, so the exported screen exposes nothing else.
- **System search suggestions** (`SearchSuggestionProvider`, `searchable.xml`): movies and shows, alternating,
  ten at most, each opening its page through `codexcine://details/…`. Only the system may read the provider
  (`GLOBAL_SEARCH`). Like "Play next", this is for Android TV's launcher: Google TV's search shows its own
  catalogue only.

### Updates from GitHub

- **A tag makes a release.** `git tag v1.2.0 && git push origin v1.2.0` runs `.github/workflows/release.yml`:
  tests, lint, then the APK signed with the release key, published as a GitHub release. The version (and the
  version code, `major × 10000 + minor × 100 + patch`) comes from the tag, through `-PappVersion`.
- **The app offers it.** On the home, at most every 12 hours, it reads the latest release of
  `UPDATE_REPOSITORY`, offers a newer version once (a version put off is not offered again), downloads the
  APK and hands it to `PackageInstaller`. Android asks the viewer to confirm, once to let the app install
  apps, then for each update; it refuses an APK not signed with the installed app's key, so a tampered
  download cannot replace it. Only https downloads from GitHub's hosts, under 100 MB, are followed.
- **The repository must be public**: the app carries no GitHub credentials. Releases can go to a separate
  public repository while the code stays private (`RELEASES_TOKEN` and the `UPDATE_REPOSITORY` variable).
  GitHub is reached through its own HTTP client, so the TMDB token never goes there.

### Measured

On a freshly booted API 36 TV emulator (2 GB): 591 frames while browsing the home, 2.9 % janky, median
18 ms, 111 MB PSS. Earlier ANRs on the home came from an emulator that had been running for hours, in swap,
where the system itself took over 10 s to start the process. They did not occur after a cold boot.

## Architecture

- **Leanback kept on purpose.** It is officially deprecated in favour of Compose for TV. For a
  personal app
  whose owner wants this exact layout, migrating has a cost and no benefit. No Compose dependency is
  present.
- **No DI framework.** `AppContainer` is a few lazy properties. Hilt, Koin or Dagger, and the KSP or
  kapt
  they bring, would cost more build time than the wiring they would save.
- **minSdk 28, Java/Kotlin 17.** API 28 is the oldest target device. `java.time` is available from
  API 26, so
  no desugaring is needed.
- **`FragmentHostActivity`.** Each activity hosts one fragment in `android.R.id.content`, and an
  error replaces
  it. Retrying creates a new fragment, whose new ViewModel starts loading. No screen needs retry
  logic, and
  a retry cannot race the old error state.
- **Errors keep their data in fragment arguments**, not in callbacks, so the screen survives process
  death.
- **A rejected or missing token shows "Retour", not "Réessayer".** The token is compiled in, so a
  retry can
  never succeed.
- **`AboutActivity` instead of a fragment transaction.** `BrowseSupportFragment` uses the same back
  stack for
  its headers transition, and adding entries to it risks corrupting that state.
- **Grid and search ViewModels take a loader function**, so their paging and debounce logic is
  testable
  without implementing the whole Retrofit interface.
- **Details builds its hero row before the network answers.** Play only needs the intent extras, so it
  works at once; the rest of the hero fills in place when the details arrive.

## Images and memory (2 GB RAM)

- Posters `w342`, stills `w780`, backgrounds `w1280`, profiles `w185`. `original` is not offered by
  `ImageUrls`.
- Cards also decode as RGB_565 with an explicit size, beyond what the brief required: posters are
  opaque, and
  this halves their memory.
- **Backgrounds are copied out of Glide.** `BackgroundManager` cross-fades, so the old bitmap is
  still drawn
  after the new one is ready. Clearing the Glide request could recycle a bitmap still on screen,
  which crashes.
  Not clearing would pin a full-screen bitmap per card visited. Owning a copy has neither problem.
- **`BaseCardPresenter` clears through the application context.** Leanback recycles cards during
  activity
  destruction, where `Glide.with(activity)` throws. This crashed on the device when leaving any
  screen.
- **`BaseCardPresenter` restores the image view on bind.** `setMainImage(null)` hides it, and Glide
  writes
  directly to the `ImageView`, so a recycled card would otherwise stay blank.

## Network

- `Retry-After` is honoured up to 15 s. A longer wait returns the response instead of freezing a
  screen.
- Logging is `debugImplementation` with per-variant source sets, so release builds do not ship the
  logging
  interceptor. The `Authorization` header is redacted.
- The token is only ever sent as a header. `InterceptorsTest` asserts this.
- TMDB's French data contains the certification `NR` ("not rated"), which is treated as no
  certification.

## Versions

Checked on Google Maven and Maven Central on 2026-11-07. The newest stable release was used unless
noted.

| Dependency                  | Version | Note                                                                        |
|-----------------------------|---------|-----------------------------------------------------------------------------|
| AGP                         | 9.5.1   | Unchanged                                                                   |
| Gradle                      | 9.6.0   | Unchanged. 9.9.0 exists; not needed.                                        |
| Kotlin                      | 2.2.10  | Must equal what AGP bundles (`./gradlew buildEnvironment`)                  |
| kotlinx-serialization-json  | 1.9.0   | 1.10+ is built for Kotlin 2.3+, 1.12 for 2.4, ahead of the bundled compiler |
| core-ktx                    | 1.19.1  |                                                                             |
| leanback                    | 1.2.0   | 1.3.0 is still alpha                                                        |
| fragment-ktx                | 1.9.1   |                                                                             |
| lifecycle                   | 2.11.0  |                                                                             |
| kotlinx-coroutines          | 1.11.0  |                                                                             |
| retrofit, converter         | 3.0.0   |                                                                             |
| okhttp, logging-interceptor | 5.5.1   |                                                                             |
| glide                       | 5.0.9   | Above the brief's 4.16.0 minimum                                            |
| media3                      | 1.11.1  | Above the brief's 1.10.1 minimum                                            |
| datastore-preferences       | 1.3.0   |                                                                             |
| webkit                      | 1.18.0  | Newest stable. 1.19.0 is still alpha.                                       |
| junit                       | 4.13.2  |                                                                             |

## Lint

`lintDebug` reports 0 errors. These checks are suppressed, each on the declarations listed:

| Suppression | Where | Why |
|---|---|---|
| `SetJavaScriptEnabled` | `HardenedWebViewFactory.create` | The embed cannot run without JavaScript, and the page gets no native interface. |
| `RestrictedApi` | `dispatchKeyEvent` in `PlaybackActivity` and `AboutActivity` | Overrides the public `Activity` method. `ComponentActivity` redeclares it with a library-restricted annotation, a known false positive. |
| `RequiresFeature` | `PlayerBridge` | The class is only created after `PlayerBridge.isSupported()` returned true, which lint cannot follow. |
| `PrivateResource`, `UnusedResources` | three Leanback dimensions in `values/dimens.xml` | Overridden on purpose: card corner radius, sidebar width, and its selection zoom. Leanback reads them, which lint does not see, and they are the only way to change these without replacing its widgets. |
| `VectorRaster` | `splash_icon.xml` | 288 dp is the size the splash screen API requires. |

These warnings remain, and are accepted:

| Warning                                                    | Why it stays                                                              |
|------------------------------------------------------------|---------------------------------------------------------------------------|
| `NewerVersionAvailable` (serialization library and plugin) | Deliberate pin, see "Versions"                                            |
| `AndroidGradlePluginVersion` (Gradle 9.9.0)                | Not needed, and the brief fixes this toolchain                            |
| `IconMissingDensityFolder`                                 | The brief asks for an xhdpi-only banner, which is what Android TV expects |

## Other

- The details overview uses `default_background` rather than the template's `selected_background`:
  white text
  on amber is hard to read at TV distance.
- All app strings are French regardless of device language. Strings inside Leanback itself (the
  search hint,
  for example) follow the system language, so they are French on a French-configured box.
- Voice search starts automatically when Search opens, which is Leanback's default. If the
  permission is denied
  or no recogniser exists, nothing happens and the keyboard remains available.
- Release minification uses `optimization { enable = true }`. No custom keep rule was needed; the
  libraries'
  consumer rules were enough. A minified build was exercised on an API 36 TV emulator: home rows,
  details,
  "Ma liste" toggle, season, person and the no-source screen.
- `assembleRelease` output is unsigned, since no signing configuration was requested. See the
  README.
- `allowBackup` stays `true`, as in the template: "Ma liste" is the only user data, and the token
  lives in the
  APK, not in app data.

## Migration to Compose for TV

Plan and stories: `doc/USER_STORIES_MIGRATION_COMPOSE_TV.md`. Screens move one at a time, behind a flag,
with Leanback kept until the new screen passes its checklist.

### US-01: build setup

- Added the Compose BOM `2026.09.00`, `androidx.tv:tv-material` `1.1.0`, `activity-compose` `1.13.0` and
  `lifecycle-runtime-compose` `2.11.0`. The Compose compiler plugin follows the Kotlin version (`2.2.10`).
- Reference measures taken before and after, with no screen migrated yet:
  - Release APK (R8): 3 937 445 bytes before, 4 041 860 after (+2.6 %).
  - Cold start on the `Television_1080p` emulator: about 17 s (`am start -W`, software emulator, only
    useful as a before/after comparison on the same machine).
