# WakeMeThere

A native Android app that wakes you up when you approach your stop, so you can
safely doze off on the bus or metro. Pick a destination on the map, arm the
alarm, fall asleep — when you get within the trigger radius, a full-screen
alarm rings over the lock screen until you dismiss it.

Built for use in Iran (Tehran): the map is **OpenStreetMap via OSMdroid** and
geocoding uses **Nominatim** — no Google Maps SDK, no API keys, no billing.

## Tech stack

| Concern | Choice |
| --- | --- |
| Language / UI | Kotlin, Jetpack Compose, Material 3 |
| Architecture | MVVM + Repository |
| DI | Hilt |
| Map | OSMdroid (OpenStreetMap tiles) |
| Geocoding | Nominatim free API (debounced, custom User-Agent) |
| Location | FusedLocationProviderClient, falls back to `LocationManager` without Play Services |
| Storage | Room (favorites) + DataStore (settings, armed state) |
| Background | Foreground Service, `foregroundServiceType="location"`, `START_STICKY` |
| Min / target SDK | 26 / 34 |

## Building & running

1. Open the repository in Android Studio (Koala or newer) — it is a
   self-contained Gradle project.
2. Let Gradle sync (requires Android SDK 34 and JDK 17+).
3. Run the `app` configuration on a device or emulator, or from the CLI:

   ```bash
   ./gradlew :app:assembleDebug     # build the APK
   ./gradlew :app:testDebugUnitTest # run unit tests
   ```

Unit tests cover the alarm trigger logic (including stale/inaccurate fix
rejection and the tunnel-emergence case) and the adaptive interval policy —
see `app/src/test/`.

GitHub Actions CI (`.github/workflows/android-ci.yml`) runs the unit tests
and builds the debug APK on every push and pull request; the APK is
downloadable from the workflow run's **Artifacts** section — handy if you
don't have Android Studio at hand.

## Design — Liquid Transit (liquid glass)

The UI follows a custom "Liquid Transit" design system:

- **Palette:** a fixed Material 3 brand scheme (blue primary `#0058bc`) with
  dedicated Metro (`#007AFF`) and BRT (`#FF9500`) accents — see
  `ui/theme/Color.kt`. Dynamic color is intentionally disabled so the look is
  consistent across devices.
- **Typography:** the Plus Jakarta Sans scale (`ui/theme/Type.kt`). To ship the
  actual font, drop the files into `res/font/` and point `BrandFontFamily` at
  them; until then it falls back to the platform sans-serif at the exact
  sizes/weights.
- **Glass components:** `ui/components/LiquidGlass.kt` provides the ambient
  blurred-orb background, translucent glass cards/panels and a pulse animation
  used by the live tracking card.

## Metro & BRT station picker

The map screen has three bold modes — **Metro**, **BRT** and **Anywhere**:

- Metro/BRT mode draws the whole Tehran network graphically: colored
  polylines per line and circular station markers (classic transit-map
  style). Tap a station to select it as the wake-up destination; the
  selected station gets an enlarged marker plus the trigger-radius circle.
- The station data ships **offline** inside the APK
  (`app/src/main/assets/transit/`) — no connectivity needed in the metro.
- Bundled coordinates are approximate (within a few hundred meters). Run
  `python3 tools/fetch_stations.py` once on a machine with internet access
  to regenerate the JSON assets with exact OpenStreetMap data.
- "Anywhere" mode keeps the original free pin drop + Nominatim search.

## How it works

1. **Pick a destination** on the OSMdroid map (tap to drop a pin, or search a
   station via Nominatim). Adjust the trigger radius (100–2000 m, default
   500 m) — shown as a translucent circle.
2. **Start tracking**: a foreground service requests high-accuracy location
   updates — every 5 s while more than 3 km away, every 2 s once closer
   (adaptive, to save battery). The persistent notification shows the live
   remaining distance and a Stop action.
3. **Alarm**: when a *fresh and accurate* fix is within the radius, the app
   posts a full-screen intent notification (like an incoming call), turns the
   screen on over the keyguard, loops the alarm sound on the **alarm audio
   stream** (bypasses silent/vibrate on the ring/media streams) and vibrates
   strongly until you press Dismiss. There is no auto-timeout.
4. **Favorites**: destinations can be saved with a custom name and re-armed
   with one tap from the home screen.

### Metro tunnel / GPS loss handling

- Fixes **older than 30 s** or with **accuracy worse than 200 m** are never
  allowed to fire the alarm (no false alarms from stale or cell-tower fixes).
- After 60 s without any fix the notification switches to
  *"GPS signal weak — still tracking"*.
- When the signal returns (train leaves the tunnel), the very next fix is
  evaluated immediately — if you emerged already inside the radius, the alarm
  fires right away.
- If Android kills the process, the service restarts (`START_STICKY`) and
  restores the armed destination from DataStore.

## Permissions

The app walks you through each permission with an explanation before the
system dialog:

| Permission | Why |
| --- | --- |
| `ACCESS_FINE_LOCATION` (while-in-use) | Track the trip. Background location is **not** requested — the foreground service is enough. |
| `POST_NOTIFICATIONS` (API 33+) | The tracking notification and the alarm itself. |
| `USE_FULL_SCREEN_INTENT` (API 34+ per-app grant) | Show the alarm over the lock screen. The app checks `canUseFullScreenIntent()` and deep-links to the settings page if revoked. |
| Battery optimization exemption | OEM battery savers (Xiaomi, Samsung, …) are known to kill foreground services mid-trip. |

Every denial path shows what will not work plus a button into the relevant
settings screen. Onboarding can be revisited from Settings → Permissions.

## Known limitations

- **Metro deep underground:** with no GPS and no network fix at all, the app
  cannot estimate position between stations. It will not false-alarm, and it
  re-evaluates the moment a signal returns — but if your destination station
  itself is deep underground and the train never surfaces, the alarm may fire
  late (on arrival at the platform where Wi-Fi/cell fixes exist) or not at
  all. Choosing a slightly larger radius helps.
- **Nominatim rate limits:** search is debounced (~1 request/s max as per the
  usage policy); very fast typing briefly delays results.
- **OEM battery killers:** even with the exemption, some ROMs (MIUI in
  particular) may require manually enabling "Autostart" / disabling
  "Battery saver" for the app.
- **Custom alarm sound** uses the system ringtone picker; arbitrary audio
  files are not supported in v1.
- UI is localized in English (default) and Persian (`res/values-fa/`, RTL);
  the app follows the system language.

## Defaults chosen (not specified in the brief)

- Map falls back to central Tehran before the first GPS fix.
- Unknown distance (no fix yet) uses the *fast* 2 s interval so the first fix
  arrives quickly.
- A partial wake lock (12 h cap) is held while tracking, so location
  callbacks keep processing with the screen off.
- The alarm notification channel is silent — sound is played by the service
  on `USAGE_ALARM` so it cannot be muted by the notification channel settings.

## Architecture

```
app/src/main/java/com/wakemethere/app/
├── di/            Hilt modules (DB, HTTP, location client selection, domain wiring)
├── domain/        Pure Kotlin: TriggerEvaluator, AdaptiveIntervalPolicy, models
├── data/
│   ├── local/     Room: DestinationEntity, DestinationDao, database
│   ├── datastore/ SettingsStore, ArmedStateStore (process-death recovery)
│   ├── remote/    NominatimClient (OkHttp)
│   └── repository/DestinationRepository
├── location/      LocationClient abstraction + Fused / LocationManager impls
├── service/       TrackingService (foreground), AlarmRinger, TrackingStateHolder
├── ui/            Compose screens: home, map, alarm, settings, onboarding, theme
└── util/          Distance formatting
```

The domain layer has no Android dependencies (the distance function is
injected), which is what makes the trigger logic unit-testable on the JVM.
`TrackingStateHolder` is a singleton `StateFlow` bridge between the service
and the UI, so the home screen and the alarm activity always show live state
without binding to the service.
