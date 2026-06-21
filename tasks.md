## Prerequisites

- [x] Cloudflare account (free)
- [x] Google AI Studio API key (free)
- [ ] Android CLI tools installed — run `sdkmanager --licenses` then `sdkmanager "platforms;android-34" "build-tools;34.0.0"`
- [ ] Java 21+ installed — run `sudo pacman -S jdk21-openjdk`
- [ ] Node.js 18+ installed — needed for deploying the Worker

## Implementation Tasks

### CF Worker ✓

- [x] Create `worker/` with `wrangler.toml`, `package.json`, `tsconfig.json`
- [x] Write `src/feeds.ts` — 15 RSS feeds across all categories
- [x] Write `src/prompt.ts` — system + user prompt for Gemini
- [x] Write `src/types.ts` — TypeScript interfaces
- [x] Write `src/index.ts` — main Worker (RSS fetch → filter → Gemini → KV store)
- [ ] **Deploy**: `cd worker && npm install && wrangler secret put GEMINI_API_KEY && wrangler deploy`

### Android Widget ✓

- [x] Create `app/build.gradle.kts` with Glance + WorkManager deps
- [x] Create `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`
- [x] Write `NotizieWidget.kt` — Glance widget (6 cards)
- [x] Write `RefreshWorker.kt` — WorkManager daily refresh
- [x] Write `DataFetcher.kt` — OkHttp client
- [x] Write `MainActivity.kt` — URL config screen
- [x] Write `AndroidManifest.xml` with widget receiver
- [ ] **Generate Gradle wrapper**: `sudo pacman -S gradle && gradle wrapper`
- [x] **Build**: `./gradlew assembleDebug` — APK ready at `app/build/outputs/apk/debug/app-debug.apk`
- [ ] **Sideload**: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

### Security & Config ✓

- [x] Create `.gitignore` at repo root
- [x] Create `worker/.env.example`
- [x] No secrets in tracked files

## Verification

- [ ] Manually trigger Worker — confirm 6 summaries returned
- [ ] Install APK on phone — widget renders correctly
- [ ] Wait for next 07:00 cron — auto-refresh works
- [ ] Test with phone in airplane mode — widget shows cached brief
