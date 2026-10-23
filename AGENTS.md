# Notif app: guide for coding agents

> فارسی: این فایل راهنمای هر ایجنتیه که روی این پروژه کار می‌کنه. اول این رو بخون، بعد `ROADMAP.md`.

## What this is
A **personal** Android app (not published) for one user, rez. It reads all SMS (including old ones)
and all app notifications, sorts them into categories, and shows them on its own screens with a
frosted-glass, neon UI. Target phone: **Samsung Galaxy S25 Ultra** (Android 15+, One UI 7+).

The user writes Persian and is new to Kotlin. Explain changes in simple Persian when talking to them.

## Hard rules
- **Offline only.** The app has no `INTERNET` permission. Never add one, never add analytics or network libraries.
- **Persian, right-to-left UI.** All user text is Persian. The theme forces RTL (`ui/theme/Theme.kt`).
- **Never lose user data.** Database changes need a migration in `Db.onUpgrade`; never drop tables.
- **Do not become the default SMS app** unless the user asks (that is phase 5).
- Keep the classifier pure Kotlin (no Android imports) so it stays unit-testable.
- Ask the user before writing large new features; follow `ROADMAP.md` order.

## Layout
```
app/src/main/java/com/rez/notif/
  NotifApp.kt            Application; holds the single Repository
  MainActivity.kt        Compose host; syncs new SMS on resume
  classify/
    Category.kt          built-in categories (ids are stored in the DB: never rename)
    Classifier.kt        rule-based "first brain" (keywords, sender patterns, packages)
    Analyzer.kt          first-scan pass: sender-majority smoothing + category suggestions
    Vip.kt               chosen people ("افراد منتخب") + VipMatcher (number or name match)
  data/
    Db.kt                SQLiteOpenHelper v2: items, sender_overrides, vip. Reads go through the
                         live_items view; delete is soft (is_deleted=1) so SMS sync never re-imports it
    Repository.kt        all reads/writes, first scan, open-in-source-app
    Prefs.kt             settings (SharedPreferences + StateFlow), CleanupRule list
    Models.kt            Item, ItemQuery, Stats...
  sms/   SmsReader.kt (inbox via content provider), SmsReceiver.kt (new SMS broadcast)
  notif/ NotifListener.kt (NotificationListenerService; hides notifications), SummaryNotifier.kt
  ui/
    AppRoot.kt           tiny back-stack navigator + screen switch
    theme/Theme.kt       glass colors (light/dark x accents), Vazirmatn font
    components/          Glass.kt (glass panel, neon icons, buttons, ring), Glow.kt (softShadow), Items.kt (rows + detail sheet), Format.kt
    screens/             Onboarding, Scan, Home, Category/Special lists, Search, Stats, Settings, Cleanup, Vip
app/src/test/java/...    ClassifierTest, AnalyzerTest, MissedCallTest, VipMatcherTest (plain JUnit)
```

## How it works
1. Onboarding asks for SMS permissions, Notification access, battery exemption.
   On Android 13+ a sideloaded APK must first get "Allow restricted settings" (App info > ⋮).
2. First scan: `Repository.firstScan` reads the whole SMS inbox, classifies each message,
   runs `Analyzer.smoothBySender`, stores everything, and returns suggestions. The user ticks main categories.
3. Live: `SmsReceiver` stores new SMS; `NotifListener` stores notifications. Ads are flagged; when
   "hide ads" is on, the listener cancels the ad notification (also the SMS app's notification for an ad SMS).
4. Learning: in the detail sheet, changing category with "all from this sender" writes a
   `sender_overrides` row; the classifier always applies overrides first.
5. Chosen people: `VipMatcher` tags items with `vip_id`. VIP items are never ads, never cleaned, and
   stay in the status bar. Adding a person re-tags old items and un-deletes theirs.
6. Tidy-up: on resume, `NotifListener.clearNonEssential` cancels shade notifications that
   `Repository.keepInShade` rejects; `autoCleanIfDue` runs the chosen `CleanupRule`s once a day.
   Deleting only hides items in this app; the phone's SMS inbox is untouched.
8. Hide-all (default on): `Repository.hideNow` decides per notification; the listener cancels it and
   `SummaryNotifier` posts one quiet summary (cleared, and `lastSeen` reset, when the app opens).
7. Glass panels must not use Compose `Modifier.shadow`/elevation: it shows through the translucent
   fill as a white box. Use `softShadow` (Glow.kt).

## Build
- CI: `.github/workflows/build.yml` runs unit tests and builds the APK on every push and PR.
  On `main` it also publishes a pre-release `build-N` with `notif-N.apk` attached.
- Local: Android Studio, or `./gradlew assembleDebug` (needs Android SDK 35, JDK 17+).
- Signing: fixed debug key `app/notif-debug.jks` (password `android`) so updates install over old builds.

## Working agreement
- Pick one unchecked item from `ROADMAP.md`, finish it, tick it, and add one line to the changelog there.
- Keep commits small with clear messages. Run `./gradlew testDebugUnitTest` before pushing.
