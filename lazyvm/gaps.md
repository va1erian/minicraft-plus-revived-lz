# LazyVM gaps hit by the Minicraft Plus port

What LazyVM lacked while the port was brought up (LazyVM worktree
`minicraft-lazyvm-port-ab8592`: `f4b8c4d`, S5-04, plus the uncommitted
lazy.gfx prototype, S9-00), how the port works around it, and how often
the game reaches the call. Workarounds are marked `// LAZYVM-GAP:` in the
source; plain AWT/Swing/sound replacements are marked `// LAZYVM:` and are
not gaps (lazy.gfx replaces those by design).

The static view is in `missing-api.txt` (`python lazyvm/build.py --report`:
the port compiled against lazyrt.jar as boot class path). The upstream
game's view, before any port change, is in `missing-api-upstream.txt`,
last made against LazyVM main `f81fa03` (2026-10-29, after S5).

## Status on LazyVM main `f81fa03` (2026-10-29)

S5 is complete, but only #17 closed anything the game calls: the upstream
report lost exactly `String.toUpperCase(Locale)` and `toLowerCase(Locale)`
(668 errors, from 675). Each remaining gap, with the plan item that owns it.
S5-10 to S5-12 were added at the S5 checkpoint (lazyvm#20) and run after S6-01/02:

| Gap | Where the game needs it | LazyVM |
|---|---|---|
| `String.toUpperCase`/`toLowerCase(Locale)` | key mapping every tick, world names, menu search | landed (#17); the port uses them again |
| `Boolean.getBoolean`, `System.getenv` | start-up | branch `s5-followups` (b480bdc), not merged |
| `Locale.forLanguageTag`/`toLanguageTag` | loading a language, saving preferences | in `s5-followups`' scope, not written yet |
| `Math.random()` | 2 sites (`Load`, `MyUtils`) | added to `s5-followups` |
| `System.setProperty` | 1 site: an AWT OpenGL flag, moot under lazy.gfx | added to `s5-followups` |
| `java.security.InvalidParameterException` | `LevelNoise` (world generation) | added to `s5-followups` |
| `java.math` (`BigInteger`, `BigDecimal`, `MathContext`) | every JSON number through org.json (the port ships its own org.json for this); the game's own `BigDecimal` loop was AWT lighting code that lazy.gfx replaced | S5-10, with `Scanner`'s Big readers (lazyvm#20) |
| `java.util.zip` (`ZipFile`, `ZipEntry`) | resource packs in `.zip` files | S5-11, over the VM's `miniz_oxide` (lazyvm#20) |
| `java.net.URL`, `MalformedURLException`, `URI.toURL()`, `Class.getResource` | resource paths at start-up, `file:` and `jar:` only | S5-12: `file:` and `jar:` URLs, `getResource` and `getResources`, split out of S7-02 (lazyvm#20) |
| `Thread.start()` | update check; tinylog's writer thread | S6-01 (branch `s6-threads`) |
| `Thread.setDefaultUncaughtExceptionHandler`, `Thread.UncaughtExceptionHandler` | first line of `main` | S6-01 |
| `Runtime.addShutdownHook` | 1 site: tinylog's provider, flushing on exit | added to S6-01 on `s6-threads` |
| `java.util.concurrent.atomic.AtomicBoolean` | 17 sites, mostly loading saves | S6-03 |
| `ConcurrentHashMap`, `ExecutorService`, `Executors`, `Future`, `locks.ReentrantReadWriteLock` | advancement triggers every tick; tinylog cannot start without the lock | S6-04 |
| streams: `Collection.stream()` and the rest, `Arrays.stream`, `Stream`, `IntStream`, `Collectors`, `String.chars()`, `BufferedReader.lines()`, `Files.walk` | several every tick (the port loops instead, `StreamGap`) | S11-01 (S5-06 defers `Files.walk`/`lines` to S11) |
| `java.time` (`LocalDateTime`, `Month`, `DateTimeFormatter`) | title-screen splash, advancement completion times, screenshot names | S11-02 |
| watch service (`WatchService`, `WatchEvent`, `StandardWatchEventKinds`, `newWatchService`) | live reload of packs and skins | out of scope (`design/io.md` section 15); the port lives without it |
| AWT, Swing, `javax.imageio`, `javax.sound.sampled` | window, dialogs, sprites, sound | replaced by lazy.gfx by design; S12/S13 are explorations, S13-03/04 "if go" |

## Missing API, deduplicated

From both reports plus what only showed at runtime (in the dependency
jars or in port code). Leaving out AWT, Swing, javax.sound and
javax.imageio, which lazy.gfx replaces:

- Packages or classes absent: `java.math` (`BigInteger`, `BigDecimal`,
  `MathContext`), `java.time` (`LocalDateTime`, `Month`,
  `format.DateTimeFormatter`), `java.util.stream` (`Stream`, `IntStream`,
  `Collectors`), `java.util.zip` (`ZipFile`, `ZipEntry`), `java.net.URL`
  and `MalformedURLException`, the `java.nio.file` watch API (`WatchService`,
  `WatchEvent`, `StandardWatchEventKinds`), `java.util.concurrent` beyond
  `Callable`/`TimeUnit` (`ConcurrentHashMap`, `ExecutorService`,
  `Executors`, `Future`, `atomic.AtomicBoolean`,
  `locks.ReentrantReadWriteLock`), `java.lang.Thread.UncaughtExceptionHandler`,
  `java.security.InvalidParameterException`.
- Methods absent: `Collection.stream()` (so `List`, `Set`, `ArrayList`,
  `HashSet` too), `Arrays.stream`, `String.chars()`, `BufferedReader.lines()`,
  `Files.walk`, `FileSystem.newWatchService()`, `Class.getResource`,
  `URI.toURL()`, `Locale.forLanguageTag`, `Locale.toLanguageTag`,
  `String.toLowerCase(Locale)` and `String.toUpperCase(Locale)` (added
  since, LazyVM #17), `Math.random()`, `System.getenv(String)`, `System.setProperty`,
  `Runtime.addShutdownHook`, `Thread.setDefaultUncaughtExceptionHandler`,
  `Boolean.getBoolean`.
- Present but unsupported: `Thread.start()` throws
  `UnsupportedOperationException` (threads are stage S6).

## Hit at runtime

Each entry was hit, in this order, by `python lazyvm/run.py --headless`.
"Frequency" is how often the game reaches the call site.

| # | Error | Call site | Frequency | Workaround |
|---|---|---|---|---|
| 1 | `BootstrapMethodError: call site initialization exception`, caused by `NoClassDefFoundError: java/lang/Thread$UncaughtExceptionHandler` | `Game.main` (`Thread.setDefaultUncaughtExceptionHandler(CrashHandler::crashHandle)`) | once, first line of `main` | `try`/`catch (Throwable)` around `main`, calling the same handler |
| 2 | `NoClassDefFoundError: java/util/concurrent/locks/ReentrantReadWriteLock` | `org.tinylog.configuration.Configuration.<clinit>` (Configuration.java:64), via `org.tinylog.Logger.<clinit>` from `minicraft.util.Logging.<clinit>` | every log call; tinylog cannot start at all. Its `WritingThread` would also need `Thread.start()` | game-side `minicraft.util.Logger`/`TaggedLogger` with the overloads the game uses, writing to the console at once; the 11 `import org.tinylog.Logger` lines point at it. No log file |
| 3 | `NoSuchMethodError: java.lang.Boolean.getBoolean(String)` | port code (`Initializer`, stats flag) | once | `"true".equals(System.getProperty(...))` |
| 4 | `NoSuchMethodError: java.lang.System.getenv(String)` | `FileHandler.<clinit>` (`System.getenv("APPDATA")`) | once at startup | `user.home` + `/AppData/Roaming` (the run script passes `--savedir` anyway) |
| 5 | `NoClassDefFoundError: java/math/BigInteger` | `org.json.JSONObject.stringToNumber` (JSONObject.java:2273), first from `ResourcePackDisplay.loadPackFromStream` reading `pack.json` | every JSON number: pack metadata, localization, achievements, quests, recipes, saves | `lazyvm/shims/org/json`: a small org.json written for the port (Integer/Long/Double numbers); `json-20220320.jar` is left off the class path |
| 6 | `NoSuchMethodError: java.io.BufferedReader.lines()` | `AchievementsDisplay.<clinit>` | once at startup | `ResourcePackDisplay.readStringFromInputStream` (now a `readLine` loop, it used `lines()` too) |
| 7 | `NoSuchMethodError: java.lang.String.toUpperCase(Locale)` | `InputHandler.getMappedKey` | several times every tick | Fixed in LazyVM #17; the upstream calls are back. The port used `toUpperCase()` there, and `toLowerCase()` in `Menu` (search) and `WorldGenDisplay` (world name, hit every frame of that screen) |
| 8 | `NoSuchMethodError: java.util.HashSet.stream()` | `InputHandler$CompoundedKey.isDown` | several times every tick | `minicraft.util.StreamGap` loops (`anyMatch`, `allMatch`, `count`, `filter`, `first`, `map`); applied to every stream on the play path (list below) |
| 9 | `UnsupportedOperationException: threads arrive in stage S6` from `Thread.start()` | `Network.findLatestVersion` (from `TitleDisplay.init`) | once per title screen | runs inline; the offline Unirest stand-in fails at once |
| 10 | `NoClassDefFoundError: java/time/LocalDateTime` | `TitleDisplay.init` (December 19 splash) | once per title screen | `minicraft.util.Clock`: the UTC date from `currentTimeMillis` |

## Worked around before they could be hit

Found with the `--report` compile and fixed with the code above, because
the call is on the start-up or play path. The exact runtime error was not
observed for these.

- `java.net.URL` (whole class) and `Class.getResource`:
  `FileHandler.getJarResourcesPath` (start-up) now finds the class path
  directory holding `assets/`; `ResourcePackDisplay` keys packs by
  `java.net.URI` instead of `URL`.
- `java.util.Locale.forLanguageTag` / `toLanguageTag`: `Localization`
  gained two helpers for the `ll-CC` tags the game uses (localization load,
  preferences save).
- `Files.walk` and `java.util.stream`: `ResourcePackDisplay.getFiles` lists
  a pack directory with `File.listFiles`.
- `Destroyable` / `DestroyFailedException`: the superinterface of
  `SpriteLinker.LinkedSprite`, so every sprite. The port has its own in
  `minicraft.util`, since LazyVM leaves `javax.*` out (owner, 2026-10-29).
- `java.util.concurrent.ConcurrentHashMap.newKeySet()` and
  `Executors.newCachedThreadPool()`: `AdvancementElement.AdvancementTrigger`,
  ticked every game tick. A plain `HashSet` and inline trigger calls.
- `java.time.LocalDateTime` in `AdvancementElement` (criterion completion
  times): kept as the ISO text, `Clock.isoNow()`.
- Streams on the play path, now `StreamGap`: `InputHandler` (key states),
  `CropTile`/`FarmTile` (water check, every farm tile tick), `PopupDisplay`,
  `WorldSelectDisplay`, `CraftingDisplay`, `TutorialDisplayHandler`,
  `AdvancementElement`, `Quest`, `Load` (`String.chars()`).
- Threads: `LoadingDisplay` built the world on a "World Initialization
  Thread" started by a Swing timer; it now builds it inside `tick()` on the
  game thread, so the loading screen stands still meanwhile, and
  `Initializer.run` drops the tick backlog that leaves. The resource pack
  and skin file watchers (`Thread` + `WatchService`) are not started: no
  hot reloading.

## Still in the port (off the paths checked)

See `missing-api.txt` for call sites. None is reached by starting the
game, making a world and walking.

- `java.util.stream` in `QuestsDisplay`, `SignDisplay`, `SignDisplayMenu`,
  `Load` (signs), `ClothingItem`, `Recipe.toString`, `Recipes.main`,
  `OnScreenKeyboardMenu` (controllers only).
- `java.util.concurrent.atomic.AtomicBoolean` in `Load`: the backup prompt
  for old worlds. It also busy-waits for input from another thread, which
  would hang without threads.
- `Executors` in `OptionsWorldDisplay` (world options popup).
- `java.util.zip.ZipFile`: zip resource packs.
- `java.nio.file.WatchService`: the (unstarted) file watchers.
- `Math.random()`: `Load` (old-world conversion) and `MyUtils.randInt`
  (unused).
- `java.security.InvalidParameterException`: `LevelNoise` error path.
- `Runtime.addShutdownHook`, tinylog internals: the unused tinylog
  provider classes.

## Performance

Measured with `-Dminicraft.lazyvm.stats=true` (fps, tps and the mean time
of a tick and of a frame, printed each second; the game targets 60 tps
and, here, 60 fps), release `lazyvm.exe`, Windows 11, headless unless
noted. The game is unchanged in what it computes; only Screen's pixel work
is port code.

| Phase | LazyVM | Note |
|---|---|---|
| Title and menus | 55 to 58 fps, 60 tps; 1 to 3 ms/tick, 10 to 18 ms/frame | Same with the real window |
| Building a 128 x 128 world (6 levels) | about 75 s, in one tick | The smallest size. Upstream ran it on a thread |
| Map generation of one 128 x 128 level (`LevelGen.createAndValidateMap`, seed 0x100) | 11.0 s | 72 ms on HotSpot 8 (Temurin 1.8.0_504), about 150 times faster |
| Entering the world (new or loaded) | ticks of 7 to 115 s for about 4 to 5 minutes | This "infdev" build generates 64 x 64 chunks 3 chunks around the player, a stage per tick, and saves on leaving the loading screen |
| Walking in a loaded area | 1 to 2 fps, 15 to 35 tps; 20 to 45 ms/tick, 200 to 300 ms/frame | Same in the real window. Night (light overlay on): 230 to 300 ms/frame |

One frame in the world, profiled (mean of 20): queuing the tiles and
sprites (game code) 65 ms, drawing them into the pixel array 106 ms, the
light overlay 77 ms, `present` under 1 ms. The overlay was then made
cheaper (a 256-entry table, and skipped while it is invisible), which helps
on the first day only; the rest is game code running in the interpreter.
Walking into a new chunk ring should bring back the multi-second ticks
(from the code; the scripted walks stayed inside the loaded chunks).

Drawing through `lazy.gfx.Surface` (S9-00b): `Screen`'s renderings now make
one native call each (a sprite is one `blit`; the overlay is a `fill` and a
`blendMask`). Same build and `scripts/night.keys`, headless, old loops
(`-Dminicraft.lazyvm.loops=true`) against `Surface`:

| Phase | Loops | Surface |
|---|---|---|
| Title and menus | 10 to 20 ms/frame | 2 to 6 ms/frame |
| Walking, day and night | 174 to 243 ms/frame | 68 to 110 ms/frame |

Ticks are unchanged (game code). `-Dminicraft.lazyvm.drawcheck=true` draws
every frame both ways and compares them: over the title screen and the
whole night script (240 frames), 0 pixels differ before the light overlay,
and with it 5 night frames differ in 6745 pixels by 1 in a channel (the
table pass rounds once, the two native blends twice).

## lazy.gfx: what the port needed and did not find

Not LazyVM gaps in the java.* sense, but notes for the `lazy.gfx` API.

- **A busy VM thread freezes the window.** The window is only pumped from
  `present` and `poll`, so while the game builds a world inside one tick the
  window cannot answer the OS, which on Windows ghosts a window as "Not
  Responding" after 5 s without messages. The port presents a frame at each
  loading step (6 per world), but a step takes 10 s or more here. Real
  threads (S6) fix this for the game; until then a cheap `Window.pump()` (or
  pumping from a timer in the host) would help any long computation.
- **No icon** (`setIconImage` with `resources/logo.png` was dropped), **no
  fullscreen** (F11 now logs a warning), **no display refresh rate** (the fps
  setting defaulted to the monitor's rate; now 60).
- **No clipboard** (world names and seeds could be pasted), **no "open URL"**
  (title screen download link, `LinkEntry`), **no sound** (a 16-bit
  44.1 kHz stereo PCM sink would be enough: the game mixes its own clips).
- **Typed text for control keys.** The desktop host passes winit's key
  text through, which for Enter is `'\r'` where AWT's `keyTyped` gives
  `'\n'`; the port maps `'\r'` to `'\n'`. The game also relies on Backspace
  typing `'\b'`. Neither was checked with a real keyboard in this session
  (the scripted runs inject text directly); a line in `Event`'s doc on what
  TEXT carries for Enter, Backspace, Tab and Escape would settle it.
- **Key names** match AWT's `VK_` names except `BACKSLASH` (AWT:
  `BACK_SLASH`). Numpad keys have no codes yet.
- **No PNG encoder**: the F2 screenshot (ImageIO + java.time) is disabled.
  `KeyScript` writes PPM, converted by `lazyvm/ppm2png.py`.
- The mouse is not used by the game, so window-pixel coordinates were no
  problem here; a letterboxed game that uses the mouse would want frame
  coordinates.
- **Window size and focus events.** In one windowed run, `capture()`,
  `width()` and `height()` gave 0 x 0 for every capture after the world was
  built (minutes without pumping), as for a minimized window; a second run
  gave 864 x 576 throughout, and a 20 s busy-loop test (no `present` or
  `poll`) kept 864 x 576, so the window was most likely minimized meanwhile.
  `capture()` of a minimized window returning an empty array may surprise
  callers. In that test the window also reported `RESIZE` 704 x 380 before
  864 x 576, and no `FOCUS` event at all; the port therefore assumes focus
  until told otherwise.
