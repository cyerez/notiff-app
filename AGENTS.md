# Notif app: guide for coding agents

> فارسی: این فایل راهنمای هر ایجنتیه که روی این پروژه کار می‌کنه. اول این رو بخون، بعد `ROADMAP.md`.

## What this is
A **personal** Android app (not published) for one user, rez. It reads all SMS (including old ones)
and all app notifications, sorts them into categories, and shows them on its own screens with a
frosted-glass, neon UI. Target phone: **Samsung Galaxy S25 Ultra** (Android 15+, One UI 7+).

The user writes Persian and is new to Kotlin. Explain changes in simple Persian when talking to them.

## Hard rules
- **Money manager first.** Since 2.2 the notification module (hide/summary/code pop-up/Home+Messages tabs) is off by default (`Prefs.notifModule`). With it off the app must not touch any notification; only Blu bank notifications are read.
- **Offline, except prices.** Since 3.0 the app has `INTERNET` for one thing only: `data/PriceStore` fetches the public gold/dollar price file (tgju.org) at most every 3 hours. No SMS, bank data, names or ids ever leave the phone; no analytics or network libraries. Any other network use needs the user's OK first.
- **Persian, right-to-left UI.** All user text is Persian. The theme forces RTL (`ui/theme/Theme.kt`).
- **Every user-facing string goes through `t(...)`** (see i18n/).
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
    Priority.kt          importance score for the "Important" section
    Learner.kt           small offline naive Bayes; sorts what rules left in "Other"
    Vip.kt               chosen people ("افراد منتخب") + VipMatcher (number or name match)
    Rules.kt             the user's own rules (app/sender/words or regex/category/hours -> action); first match wins;
                         one-line codec stored in Db `rules`; templates. Pure Kotlin, tested
    Money.kt             amounts out/in and balance from bank SMS text (rial). Pure Kotlin, tested
    Bills.kt             due date (+ amount) of bill/installment SMS; Jalali->Gregorian. Pure Kotlin, tested
    Spending.kt          bank SMS transactions -> kinds (card transfer, online, store, ATM, bills, salary...); an out + in of the
                         same amount within 30 min = own accounts (INTERNAL, left out of totals). Pure Kotlin, tested
    Records.kt           tab-separated escaped text records with a header line; the transfer file format (see TransferScreen). Pure Kotlin, tested
    Budget.kt            monthly income/spending lines (every month from a Persian yyyymm, optional end, or one month); totals. Pure Kotlin, tested
    Banks.kt             SMS sender / user-given name -> bank (name, color, logo res bank_<id>); names the user typed live in data/BankNames. Pure Kotlin, tested
    Jalali.kt            Persian calendar math on epoch days (month length, leap, weekday from Saturday). Pure Kotlin, tested
    Planner.kt           the calendar: PlanEvent (event/expense/trip with days + reminder) + installments + cheques -> marks per day, upcoming. Pure Kotlin, tested
    Ledger.kt            the ledger ("دفتر حساب"): bank SMS + Blu notifications + hand entries -> entries with account (bank + last 4),
                         party (card:XXXX / shop:...), category (user tag > own card > learned party > default > ask). Cards seen as an
                         account are the user's own. gaps(): balance before/after each SMS disagree = missing SMS. Pure Kotlin, tested
    Commitments.kt       Debt, Cheque, Goal (savings / emergency piggy bank) + totals, cheques due soon, per-person balance, CSV for Excel. Pure Kotlin, tested
    Dong.kt              split costs ("دنگ") for trips: people with weights, per-expense payer/day/sharers, who was
                         present each day, balances + fewest transfers; one-text codec stored in Db `trips`. Pure Kotlin, tested
  data/
    Db.kt                SQLiteOpenHelper v10 (v10: events table for the calendar, bills.start): items, sender_overrides, vip, wallet, rules, reminders, bills (hand-entered; their reminders use negative ids and repeat every morning until the due day), trips (dong), budget (monthly income/spending), ledger_tags/ledger_learn/ledger_manual (v8, the ledger), ledger_limits/debts/cheques/goals (v9). Reads go through the
                         live_items view; delete is soft (is_deleted=1) so SMS sync never re-imports it
    Repository.kt        all reads/writes, first scan, open-in-source-app
    Prefs.kt             settings (SharedPreferences + StateFlow), CleanupRule list
    Models.kt            Item, ItemQuery, Stats...
  i18n/  I18n.kt + En.kt: t("persian") returns English when the app is in English. Wrap every new
         user-facing Persian string in t(...) and add its English to En.kt (I18nTest fails otherwise).
  backup/ BackupCrypto.kt (password -> PBKDF2 -> AES-256-GCM; pure Kotlin, tested)
  security/ AppLock.kt (system BiometricPrompt; relocks after 30 s away), DbKey.kt (DB key wrapped by Keystore),
          WalletVault.kt (password wallet key: Keystore AES-GCM, needs fingerprint/PIN every 60 s; clipboard wipe)
  wallet/ WalletModel.kt (WalletKind, WalletEntry, WalletCodec, PasswordGen; pure Kotlin, tested; keep Android out),
         PasswordCsv.kt (import of Google/Edge/Firefox/Bitwarden CSV exports by header names; tested)
  ocr/   Ocr.kt (offline picture-to-text with Tesseract4Android from JitPack; fas+eng language files are
         downloaded by CI into assets/tessdata, not kept in git). Share target: activity-alias .OcrShare
  widget/ NotifWidget.kt (RemoteViews home-screen widget; refreshed 2 s after data changes)
  sms/   SmsReader.kt (inbox via content provider), SmsReceiver.kt (new SMS broadcast; skipped when we are default)
         Phase 5 (default SMS app): DefaultSms.kt (role, send, write/delete in the phone's SMS store, threads),
         SmsDeliverReceiver.kt (SMS_DELIVER: must write to the inbox, then notify via SmsNotifier),
         ContactNames.kt (READ_CONTACTS: number → name by last 10 digits; UI uses senderLabel(); VIPs are merged
         per contact by Repository.tidyVips)
         MmsReceiver.kt + MmsPdu.kt (MMS is only recorded, not downloaded), HeadlessSmsSendService.kt
  notif/ NotifListener.kt (NotificationListenerService; hides notifications after Decision.holdMs = Settings holdSeconds,
         ads/codes/quiet hours at once; Settings watchMode: while the phone is locked they wait for ACTION_USER_PRESENT so a smartwatch keeps them), CodeOverlay.kt (optional "copy code" box over other apps for codeSeconds,
         needs SYSTEM_ALERT_WINDOW; falls back to the pop-up notification), SummaryNotifier.kt (live, or only at
         `digestHours`), BankAlerts.kt (low balance / big spend, from Repository.afterNew),
         CodeTile.kt (quick-settings "copy newest code"), Reminders.kt (remind later, bill reminders / until seen, Digest alarms, AlarmReceiver incl. boot;
         inexact setAndAllowWhileIdle, no exact-alarm permission)
  ui/
    AppRoot.kt           tiny back-stack navigator + screen switch
    theme/Backdrops.kt   cyber/steam/pixel/meadow backgrounds; GlassColors.frame changes panel shape+outline (Glass.kt styledShape)
    theme/Theme.kt       glass colors (light/dark x accents), Vazirmatn font
    components/          Glass.kt (glass panel, neon icons, buttons, ring), Glow.kt (softShadow), Items.kt (rows + detail sheet), Format.kt
    screens/             Onboarding, Scan, Home (4 bottom tabs: HomeTab = Home (Repository.homeFeed) / Messages (all non-ad categories) / Apps (tool grid) / Settings (SettingsScreen embedded)), Category/Special lists, Search, Stats, Settings, Cleanup, Vip, Learned, Rules + RuleEdit, Money (bank summary, bills, alert settings), Dong + Trip (split costs), Budget (دخل و خرج: month switcher, lines + installments + bank SMS totals), Commitments (debts, cheques with reminders (Reminders ids <= -1e9), savings goals, installments), Ledger (دفتر حساب, the first tab when Settings notifModule is off (default): left to spend, per-category limits, unreadable bank SMS queue, CSV export, month, gaps, "what was this?", per category, my cards; notif/LedgerAsk.kt asks after an unknown spending with category buttons), Transfer (chosen categories/VIP/starred -> password-locked .notifmsg file; import merges by source_key and can write SMS into the phone inbox when default SMS app), Backup, Lock, Wallet (FLAG_SECURE), Conversation (phase 5), Ocr
app/src/test/java/...    ClassifierTest, AnalyzerTest, MissedCallTest, VipMatcherTest, BackupCryptoTest, I18nTest, WalletTest (plain JUnit)
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
9. Default SMS app (phase 5): opt-in from Settings. Then SMS_DELIVER replaces SMS_RECEIVED, Notif writes every incoming
   and sent SMS to the provider, `Repository.delete/cleanup` also delete on the phone when `phoneDelete` is on (undo
   re-inserts), and `blockAdSms` keeps ads out of the phone inbox. Never skip the provider write: that loses messages.
7. Password wallet: rows in `wallet` hold title+kind readable (inside SQLCipher) and the rest encrypted again with
   `WalletVault` (user-auth Keystore key). Backup decrypts them into a `wallet_export` table of the temp copy
   (the whole file is then password-locked); restore re-encrypts with this phone's key. Both need the wallet unlocked.
12. User rules (v1.4): `Repository.applyRule` runs MOVE/DELETE before storing; `hideNow(rule=)` gives ALERT/QUIET/
    REMIND priority over hide-all and quiet hours (`quietFrom/quietTo`). The rule editor previews on the last 7 days
    (`previewRule`) and MOVE/DELETE also re-sort the last 30 days on save. Weekly card in Stats offers "quiet this app".
8. Hide-all (default on): `Repository.hideNow` decides per notification; the listener cancels it and
   `SummaryNotifier` posts one quiet summary (cleared, and `lastSeen` reset, when the app opens).
10. Database is SQLCipher (`notif-secure.db`). `Db.open` moves the old plain `notif.db` once and deletes
    it only after row counts match; if anything fails it keeps running on the plain file.
11. Styles: `AppStyle` in Prefs -> `themeColors` in Theme.kt. Vector scenes live in theme/Scenes.kt
    (pure DrawScope paths + a palette); a new mood or season is a new `ScenePalette`.
9. Backup: `Db.snapshotTo` (VACUUM INTO) -> encrypt -> SAF file. Restore checks the file, closes the
   helper, swaps the DB file, and SQLiteOpenHelper reopens (and migrates) it on next use.
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
