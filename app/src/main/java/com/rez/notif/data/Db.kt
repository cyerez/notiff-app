package com.rez.notif.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.rez.notif.security.DbKey
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import java.io.File

/**
 * Encrypted SQLite storage on the phone (SQLCipher, AES-256). No internet, nothing leaves the device.
 * The key is random, made on this phone and wrapped by the hardware keystore (see DbKey).
 * Use [Db.open]; it also moves data from the old unencrypted file (v0.1 to v0.4) once.
 */
class Db private constructor(
    context: Context,
    private val file: File,
    /** Hex key, or "" when running unencrypted because the move failed (data is never thrown away for this). */
    private val key: String,
) : SQLiteOpenHelper(context, file.name, key.toByteArray(Charsets.US_ASCII), null, VERSION, 0, null, null, false) {

    val encrypted: Boolean get() = key.isNotEmpty()

    companion object {
        const val VERSION = 9
        private const val PLAIN = "notif.db"
        private const val SECURE = "notif-secure.db"

        fun open(context: Context): Db {
            System.loadLibrary("sqlcipher")
            val plain = context.getDatabasePath(PLAIN)
            val secure = context.getDatabasePath(SECURE)
            val key = DbKey.get(context)
            if (key == null) {
                // The phone lost the wrapping key (very rare). Keep the old file aside and start fresh.
                if (secure.exists()) secure.renameTo(File(secure.path + ".locked-" + System.currentTimeMillis()))
                return if (plain.exists()) Db(context, plain, "") else Db(context, secure, DbKey.get(context) ?: "")
            }
            if (plain.exists() && !secure.exists()) {
                val moved = runCatching { encryptFile(plain, secure, key) }.isSuccess
                if (!moved) {
                    secure.delete()
                    return Db(context, plain, "")
                }
                deleteWithSidecars(plain)
            }
            return Db(context, secure, key)
        }

        /** Copies an unencrypted database into a new encrypted one and checks nothing was lost. */
        private fun encryptFile(source: File, target: File, key: String) {
            val tmp = File(target.path + ".tmp")
            deleteWithSidecars(tmp)
            SQLiteDatabase.openDatabase(source.path, "", null, SQLiteDatabase.OPEN_READWRITE, null, null).use { db ->
                val version = db.version
                db.execSQL("ATTACH DATABASE ? AS enc KEY ?", arrayOf<Any>(tmp.path, key))
                db.rawQuery("SELECT sqlcipher_export('enc')", null).use { it.moveToFirst() }
                db.execSQL("PRAGMA enc.user_version = $version")
                db.execSQL("DETACH DATABASE enc")
            }
            val before = countItems(source, "")
            val after = countItems(tmp, key)
            check(before == after) { "row count changed: $before -> $after" }
            deleteWithSidecars(target)
            check(tmp.renameTo(target)) { "rename failed" }
        }

        private fun countItems(f: File, key: String): Long =
            SQLiteDatabase.openDatabase(f.path, key, null, SQLiteDatabase.OPEN_READONLY, null, null).use { db ->
                db.rawQuery("SELECT COUNT(*) FROM items", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
            }

        private fun deleteWithSidecars(f: File) {
            listOf("", "-wal", "-shm", "-journal").forEach { File(f.path + it).delete() }
        }
    }

    /** Writes an unencrypted copy of the whole database to [target] (it is locked with the backup password right after). */
    fun snapshotTo(target: File) {
        deleteWithSidecars(target)
        val db = writableDatabase
        if (!encrypted) {
            db.execSQL("VACUUM INTO ?", arrayOf<Any>(target.absolutePath))
            return
        }
        db.execSQL("ATTACH DATABASE ? AS plain KEY ''", arrayOf<Any>(target.absolutePath))
        try {
            db.rawQuery("SELECT sqlcipher_export('plain')", null).use { it.moveToFirst() }
            db.execSQL("PRAGMA plain.user_version = ${db.version}")
        } finally {
            db.execSQL("DETACH DATABASE plain")
        }
    }

    /** Replaces everything with the unencrypted database in [source] (from a backup). */
    fun restoreFrom(source: File) {
        close()
        if (encrypted) {
            encryptFile(source, file, key)
        } else {
            deleteWithSidecars(file)
            source.copyTo(file, overwrite = true)
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source TEXT NOT NULL,
                source_key TEXT NOT NULL UNIQUE,
                package_name TEXT NOT NULL,
                app_name TEXT NOT NULL,
                sender TEXT NOT NULL,
                title TEXT NOT NULL,
                body TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                sim_slot INTEGER NOT NULL DEFAULT 0,
                category TEXT NOT NULL,
                is_ad INTEGER NOT NULL DEFAULT 0,
                is_valuable INTEGER NOT NULL DEFAULT 0,
                is_starred INTEGER NOT NULL DEFAULT 0,
                is_read INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        createV2(db)
        createV3(db)
        createV4(db)
        createV5(db)
        createV6(db)
        createV7(db)
        createV8(db)
        createV9(db)
        db.execSQL("CREATE INDEX idx_items_cat_time ON items(category, timestamp DESC)")
        db.execSQL("CREATE INDEX idx_items_time ON items(timestamp DESC)")
        db.execSQL("CREATE INDEX idx_items_sender ON items(sender)")
        // What the user taught the app: "everything from this sender goes to that category".
        db.execSQL("CREATE TABLE sender_overrides (sender_key TEXT PRIMARY KEY, category TEXT NOT NULL)")
    }

    /** Version 2: chosen people, soft delete. */
    private fun createV2(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE items ADD COLUMN vip_id INTEGER NOT NULL DEFAULT 0")
        // Deleted items stay as a hidden row, so a later SMS sync does not bring them back.
        db.execSQL("ALTER TABLE items ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX idx_items_vip ON items(vip_id, timestamp DESC)")
        db.execSQL("CREATE VIEW live_items AS SELECT * FROM items WHERE is_deleted = 0")
        db.execSQL("CREATE TABLE vip (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, numbers TEXT NOT NULL, created INTEGER NOT NULL)")
    }

    /** Version 3: offline password wallet. `data` is locked by the wallet's hardware key (WalletVault). */
    private fun createV3(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE wallet (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, kind TEXT NOT NULL, data BLOB NOT NULL, updated INTEGER NOT NULL)")
    }

    /** Version 4: the user's own rules, and "remind me later". */
    private fun createV4(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS rules (id INTEGER PRIMARY KEY AUTOINCREMENT, pos INTEGER NOT NULL, data TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS reminders (item_id INTEGER PRIMARY KEY, at INTEGER NOT NULL, repeats INTEGER NOT NULL DEFAULT 0)")
    }

    /** Version 5: bills and installments the user entered by hand (amount in toman, Persian day of month). */
    private fun createV5(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS bills (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, amount INTEGER NOT NULL, day INTEGER NOT NULL, remaining INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL)")
    }

    /** Version 6: dong (shared costs) trips; the whole trip is one text field (see classify/Dong.kt). */
    private fun createV6(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS trips (id INTEGER PRIMARY KEY AUTOINCREMENT, data TEXT NOT NULL, updated INTEGER NOT NULL)")
    }

    /** Version 7: monthly income and spending lines (classify/Budget.kt). Amount in toman, month as Persian yyyymm. */
    private fun createV7(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS budget (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, amount INTEGER NOT NULL, income INTEGER NOT NULL, monthly INTEGER NOT NULL, month INTEGER NOT NULL, until INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL)")
    }

    /**
     * Version 8: the ledger (classify/Ledger.kt). ledger_tags: category the user picked for one bank message.
     * ledger_learn: party ("card:4521" / "shop:...") -> category, also "__own" for the user's own cards.
     * ledger_manual: hand-entered spending/income and named gaps (rial; account as in Ledger.accountOf).
     */
    private fun createV8(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS ledger_tags (item_id INTEGER PRIMARY KEY, category TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS ledger_learn (party TEXT PRIMARY KEY, category TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS ledger_manual (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, account TEXT NOT NULL, out_rial INTEGER NOT NULL, in_rial INTEGER NOT NULL, category TEXT NOT NULL, note TEXT NOT NULL DEFAULT '')")
    }

    /** Version 9: monthly limit per category, debts/credits, cheques, savings goals (classify/Commitments.kt). Toman. */
    private fun createV9(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS ledger_limits (category TEXT PRIMARY KEY, toman INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS debts (id INTEGER PRIMARY KEY AUTOINCREMENT, person TEXT NOT NULL, toman INTEGER NOT NULL, i_owe INTEGER NOT NULL, due INTEGER NOT NULL DEFAULT 0, note TEXT NOT NULL DEFAULT '', settled INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS cheques (id INTEGER PRIMARY KEY AUTOINCREMENT, person TEXT NOT NULL, toman INTEGER NOT NULL, due INTEGER NOT NULL, incoming INTEGER NOT NULL, number TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'PENDING', created INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS goals (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, target INTEGER NOT NULL, saved INTEGER NOT NULL DEFAULT 0, monthly INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Never drop user data here; only add.
        if (oldVersion < 2) {
            createV2(db)
            // v0.1 marked every old SMS as unread; anything older than a day counts as read.
            db.execSQL("UPDATE items SET is_read = 1 WHERE timestamp < ?", arrayOf<Any>(System.currentTimeMillis() - 86_400_000L))
        }
        if (oldVersion < 3) createV3(db)
        if (oldVersion < 4) createV4(db)
        if (oldVersion < 5) createV5(db)
        if (oldVersion < 6) createV6(db)
        if (oldVersion < 7) createV7(db)
        if (oldVersion < 8) createV8(db)
        if (oldVersion < 9) createV9(db)
    }

    // ---------- password wallet ----------

    /** Wallet rows: the readable part and the still-locked secret data. */
    fun walletRows(): List<Pair<com.rez.notif.wallet.WalletEntry, ByteArray>> =
        readableDatabase.rawQuery("SELECT id, title, kind, data, updated FROM wallet ORDER BY title COLLATE NOCASE", null).use { c ->
            val l = ArrayList<Pair<com.rez.notif.wallet.WalletEntry, ByteArray>>()
            while (c.moveToNext()) {
                val kind = runCatching { com.rez.notif.wallet.WalletKind.valueOf(c.getString(2)) }.getOrDefault(com.rez.notif.wallet.WalletKind.LOGIN)
                l += com.rez.notif.wallet.WalletEntry(id = c.getLong(0), title = c.getString(1), kind = kind, updated = c.getLong(4)) to c.getBlob(3)
            }
            l
        }

    fun walletCount(): Int = count("1=1", null, table = "wallet")

    /** Inserts (id 0) or replaces a wallet row. Returns its id. */
    fun walletPut(id: Long, title: String, kind: String, data: ByteArray, updated: Long): Long {
        val v = ContentValues().apply {
            if (id > 0) put("id", id)
            put("title", title); put("kind", kind); put("data", data); put("updated", updated)
        }
        return writableDatabase.insertWithOnConflict("wallet", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun walletDelete(id: Long) {
        writableDatabase.delete("wallet", "id = ?", arrayOf(id.toString()))
    }

    // ---------- rules and reminders ----------

    fun rules(): List<com.rez.notif.classify.Rule> =
        readableDatabase.rawQuery("SELECT id, data FROM rules ORDER BY pos, id", null).use { c ->
            val l = ArrayList<com.rez.notif.classify.Rule>()
            while (c.moveToNext()) com.rez.notif.classify.Rules.decode(c.getLong(0), c.getString(1))?.let { l += it }
            l
        }

    fun saveRule(r: com.rez.notif.classify.Rule): Long {
        val db = writableDatabase
        val data = com.rez.notif.classify.Rules.encode(r)
        if (r.id > 0) {
            db.execSQL("UPDATE rules SET data = ? WHERE id = ?", arrayOf<Any>(data, r.id))
            return r.id
        }
        val pos = db.rawQuery("SELECT COALESCE(MAX(pos), 0) + 1 FROM rules", null).use { it.moveToFirst(); it.getInt(0) }
        return db.insert("rules", null, ContentValues().apply { put("pos", pos); put("data", data) })
    }

    fun deleteRule(id: Long) {
        writableDatabase.delete("rules", "id = ?", arrayOf(id.toString()))
    }

    /** Saves the rules' order (first match wins). */
    fun reorderRules(ids: List<Long>) {
        val db = writableDatabase
        ids.forEachIndexed { i, id -> db.execSQL("UPDATE rules SET pos = ? WHERE id = ?", arrayOf<Any>(i, id)) }
    }

    fun putReminder(itemId: Long, at: Long, repeats: Int) {
        writableDatabase.insertWithOnConflict(
            "reminders", null,
            ContentValues().apply { put("item_id", itemId); put("at", at); put("repeats", repeats) },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun removeReminder(itemId: Long) {
        writableDatabase.delete("reminders", "item_id = ?", arrayOf(itemId.toString()))
    }

    /** (item id, time, repeats left), soonest first. */
    fun reminders(): List<Triple<Long, Long, Int>> =
        readableDatabase.rawQuery("SELECT item_id, at, repeats FROM reminders ORDER BY at", null).use { c ->
            val l = ArrayList<Triple<Long, Long, Int>>()
            while (c.moveToNext()) l += Triple(c.getLong(0), c.getLong(1), c.getInt(2))
            l
        }

    /** A bill or installment the user entered: due on Persian [day] each month; [remaining] 0 = no end. */
    data class MyBill(val id: Long = 0, val title: String, val amount: Long, val day: Int, val remaining: Int = 0)

    fun myBills(): List<MyBill> =
        readableDatabase.rawQuery("SELECT id, title, amount, day, remaining FROM bills ORDER BY day", null).use { c ->
            val l = ArrayList<MyBill>()
            while (c.moveToNext()) l += MyBill(c.getLong(0), c.getString(1), c.getLong(2), c.getInt(3), c.getInt(4))
            l
        }

    fun myBill(id: Long): MyBill? = myBills().firstOrNull { it.id == id }

    fun saveMyBill(b: MyBill): Long {
        val v = ContentValues().apply {
            put("title", b.title); put("amount", b.amount); put("day", b.day); put("remaining", b.remaining)
            put("created", System.currentTimeMillis())
        }
        return if (b.id > 0) { writableDatabase.update("bills", v, "id = ?", arrayOf(b.id.toString())); b.id } else writableDatabase.insert("bills", null, v)
    }

    fun deleteMyBill(id: Long) {
        writableDatabase.delete("bills", "id = ?", arrayOf(id.toString()))
    }

    // ---------- dong trips ----------

    fun trips(): List<com.rez.notif.classify.DongTrip> =
        readableDatabase.rawQuery("SELECT id, data, updated FROM trips ORDER BY updated DESC", null).use { c ->
            val l = ArrayList<com.rez.notif.classify.DongTrip>()
            while (c.moveToNext()) com.rez.notif.classify.Dong.decode(c.getLong(0), c.getString(1), c.getLong(2))?.let { l += it }
            l
        }

    fun saveTrip(t: com.rez.notif.classify.DongTrip): Long {
        val v = ContentValues().apply { put("data", com.rez.notif.classify.Dong.encode(t)); put("updated", System.currentTimeMillis()) }
        return if (t.id > 0) { writableDatabase.update("trips", v, "id = ?", arrayOf(t.id.toString())); t.id } else writableDatabase.insert("trips", null, v)
    }

    fun deleteTrip(id: Long) {
        writableDatabase.delete("trips", "id = ?", arrayOf(id.toString()))
    }

    // ---------- ledger ----------

    fun ledgerTags(): Map<Long, String> = readableDatabase.rawQuery("SELECT item_id, category FROM ledger_tags", null).use { c ->
        val m = HashMap<Long, String>()
        while (c.moveToNext()) m[c.getLong(0)] = c.getString(1)
        m
    }

    fun setLedgerTag(itemId: Long, category: String?) {
        if (category == null) writableDatabase.delete("ledger_tags", "item_id = ?", arrayOf(itemId.toString()))
        else writableDatabase.insertWithOnConflict("ledger_tags", null, ContentValues().apply { put("item_id", itemId); put("category", category) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun ledgerLearned(): Map<String, String> = readableDatabase.rawQuery("SELECT party, category FROM ledger_learn", null).use { c ->
        val m = HashMap<String, String>()
        while (c.moveToNext()) m[c.getString(0)] = c.getString(1)
        m
    }

    fun setLearned(party: String, category: String?) {
        if (category == null) writableDatabase.delete("ledger_learn", "party = ?", arrayOf(party))
        else writableDatabase.insertWithOnConflict("ledger_learn", null, ContentValues().apply { put("party", party); put("category", category) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    data class ManualTx(val id: Long, val time: Long, val account: String, val outRial: Long, val inRial: Long, val category: String, val note: String)

    fun manualTxs(since: Long = 0, until: Long = Long.MAX_VALUE): List<ManualTx> =
        readableDatabase.rawQuery(
            "SELECT id, time, account, out_rial, in_rial, category, note FROM ledger_manual WHERE time >= ? AND time < ? ORDER BY time DESC",
            arrayOf(since.toString(), until.toString()),
        ).use { c ->
            val l = ArrayList<ManualTx>()
            while (c.moveToNext()) l += ManualTx(c.getLong(0), c.getLong(1), c.getString(2), c.getLong(3), c.getLong(4), c.getString(5), c.getString(6))
            l
        }

    fun saveManualTx(m: ManualTx): Long {
        val v = ContentValues().apply {
            put("time", m.time); put("account", m.account); put("out_rial", m.outRial); put("in_rial", m.inRial)
            put("category", m.category); put("note", m.note)
        }
        return if (m.id > 0) { writableDatabase.update("ledger_manual", v, "id = ?", arrayOf(m.id.toString())); m.id } else writableDatabase.insert("ledger_manual", null, v)
    }

    fun deleteManualTx(id: Long) {
        writableDatabase.delete("ledger_manual", "id = ?", arrayOf(id.toString()))
    }

    // ---------- limits, debts, cheques, goals ----------

    fun limits(): Map<String, Long> = readableDatabase.rawQuery("SELECT category, toman FROM ledger_limits", null).use { c ->
        val m = HashMap<String, Long>()
        while (c.moveToNext()) m[c.getString(0)] = c.getLong(1)
        m
    }

    fun setLimit(category: String, toman: Long) {
        if (toman <= 0) writableDatabase.delete("ledger_limits", "category = ?", arrayOf(category))
        else writableDatabase.insertWithOnConflict("ledger_limits", null, ContentValues().apply { put("category", category); put("toman", toman) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun debts(): List<com.rez.notif.classify.Debt> =
        readableDatabase.rawQuery("SELECT id, person, toman, i_owe, due, note, settled, created FROM debts ORDER BY settled, created DESC", null).use { c ->
            val l = ArrayList<com.rez.notif.classify.Debt>()
            while (c.moveToNext()) l += com.rez.notif.classify.Debt(c.getLong(0), c.getString(1), c.getLong(2), c.getInt(3) == 1, c.getLong(4), c.getString(5), c.getInt(6) == 1, c.getLong(7))
            l
        }

    fun saveDebt(d: com.rez.notif.classify.Debt): Long {
        val v = ContentValues().apply {
            put("person", d.person); put("toman", d.toman); put("i_owe", if (d.iOwe) 1 else 0); put("due", d.due)
            put("note", d.note); put("settled", if (d.settled) 1 else 0); put("created", if (d.created > 0) d.created else System.currentTimeMillis())
        }
        return if (d.id > 0) { writableDatabase.update("debts", v, "id = ?", arrayOf(d.id.toString())); d.id } else writableDatabase.insert("debts", null, v)
    }

    fun deleteDebt(id: Long) { writableDatabase.delete("debts", "id = ?", arrayOf(id.toString())) }

    fun cheques(): List<com.rez.notif.classify.Cheque> =
        readableDatabase.rawQuery("SELECT id, person, toman, due, incoming, number, status, created FROM cheques ORDER BY due", null).use { c ->
            val l = ArrayList<com.rez.notif.classify.Cheque>()
            while (c.moveToNext()) {
                val st = runCatching { com.rez.notif.classify.Cheque.Status.valueOf(c.getString(6)) }.getOrDefault(com.rez.notif.classify.Cheque.Status.PENDING)
                l += com.rez.notif.classify.Cheque(c.getLong(0), c.getString(1), c.getLong(2), c.getLong(3), c.getInt(4) == 1, c.getString(5), st, c.getLong(7))
            }
            l
        }

    fun saveCheque(q: com.rez.notif.classify.Cheque): Long {
        val v = ContentValues().apply {
            put("person", q.person); put("toman", q.toman); put("due", q.due); put("incoming", if (q.incoming) 1 else 0)
            put("number", q.number); put("status", q.status.name); put("created", if (q.created > 0) q.created else System.currentTimeMillis())
        }
        return if (q.id > 0) { writableDatabase.update("cheques", v, "id = ?", arrayOf(q.id.toString())); q.id } else writableDatabase.insert("cheques", null, v)
    }

    fun deleteCheque(id: Long) { writableDatabase.delete("cheques", "id = ?", arrayOf(id.toString())) }

    fun goals(): List<com.rez.notif.classify.Goal> =
        readableDatabase.rawQuery("SELECT id, title, target, saved, monthly, created FROM goals ORDER BY created", null).use { c ->
            val l = ArrayList<com.rez.notif.classify.Goal>()
            while (c.moveToNext()) l += com.rez.notif.classify.Goal(c.getLong(0), c.getString(1), c.getLong(2), c.getLong(3), c.getLong(4), c.getLong(5))
            l
        }

    fun saveGoal(g: com.rez.notif.classify.Goal): Long {
        val v = ContentValues().apply {
            put("title", g.title); put("target", g.target); put("saved", g.saved); put("monthly", g.monthly)
            put("created", if (g.created > 0) g.created else System.currentTimeMillis())
        }
        return if (g.id > 0) { writableDatabase.update("goals", v, "id = ?", arrayOf(g.id.toString())); g.id } else writableDatabase.insert("goals", null, v)
    }

    fun deleteGoal(id: Long) { writableDatabase.delete("goals", "id = ?", arrayOf(id.toString())) }

    // ---------- monthly budget ----------

    fun budgetLines(): List<com.rez.notif.classify.BudgetLine> =
        readableDatabase.rawQuery("SELECT id, title, amount, income, monthly, month, until FROM budget ORDER BY amount DESC", null).use { c ->
            val l = ArrayList<com.rez.notif.classify.BudgetLine>()
            while (c.moveToNext()) l += com.rez.notif.classify.BudgetLine(c.getLong(0), c.getString(1), c.getLong(2), c.getInt(3) == 1, c.getInt(4) == 1, c.getInt(5), c.getInt(6))
            l
        }

    fun saveBudgetLine(b: com.rez.notif.classify.BudgetLine): Long {
        val v = ContentValues().apply {
            put("title", b.title); put("amount", b.amount); put("income", if (b.income) 1 else 0); put("monthly", if (b.monthly) 1 else 0)
            put("month", b.month); put("until", b.until); put("created", System.currentTimeMillis())
        }
        return if (b.id > 0) { writableDatabase.update("budget", v, "id = ?", arrayOf(b.id.toString())); b.id } else writableDatabase.insert("budget", null, v)
    }

    fun deleteBudgetLine(id: Long) {
        writableDatabase.delete("budget", "id = ?", arrayOf(id.toString()))
    }

    // ---------- writes ----------

    /** Inserts, or skips when source_key already exists. Returns true when inserted. */
    fun insert(item: Item): Boolean =
        writableDatabase.insertWithOnConflict("items", null, item.toValues(), SQLiteDatabase.CONFLICT_IGNORE) != -1L

    fun insertAll(items: List<Item>): Int {
        val db = writableDatabase
        var n = 0
        db.beginTransaction()
        try {
            for (item in items) {
                if (db.insertWithOnConflict("items", null, item.toValues(), SQLiteDatabase.CONFLICT_IGNORE) != -1L) n++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return n
    }

    fun updateCategory(id: Long, category: String, isAd: Boolean) {
        val v = ContentValues().apply {
            put("category", category)
            put("is_ad", if (isAd) 1 else 0)
        }
        writableDatabase.update("items", v, "id = ?", arrayOf(id.toString()))
    }

    fun updateCategoryForSender(sender: String, source: Source, category: String, isAd: Boolean): Int {
        val v = ContentValues().apply {
            put("category", category)
            put("is_ad", if (isAd) 1 else 0)
        }
        return if (source == Source.SMS) {
            writableDatabase.update("items", v, "source = ? AND sender = ?", arrayOf(source.name, sender))
        } else {
            writableDatabase.update("items", v, "source = ? AND package_name = ?", arrayOf(source.name, sender))
        }
    }

    fun setStarred(id: Long, starred: Boolean) {
        val v = ContentValues().apply { put("is_starred", if (starred) 1 else 0) }
        writableDatabase.update("items", v, "id = ?", arrayOf(id.toString()))
    }

    fun markRead(ids: List<Long>) {
        if (ids.isEmpty()) return
        val v = ContentValues().apply { put("is_read", 1) }
        ids.chunked(500).forEach { chunk ->
            writableDatabase.update("items", v, "id IN (${chunk.joinToString(",")})", null)
        }
    }

    fun markCategoryRead(category: String) {
        val v = ContentValues().apply { put("is_read", 1) }
        writableDatabase.update("items", v, "category = ?", arrayOf(category))
    }

    /** Soft delete: hidden everywhere, but kept so a sync does not re-import it. */
    fun delete(ids: List<Long>, deleted: Boolean = true) {
        if (ids.isEmpty()) return
        val v = ContentValues().apply { put("is_deleted", if (deleted) 1 else 0) }
        ids.chunked(500).forEach { chunk ->
            writableDatabase.update("items", v, "id IN (${chunk.joinToString(",")})", null)
        }
    }

    /** SMS items by id, deleted or not (to remove them from, or put them back in, the phone's inbox). */
    fun smsByIds(ids: List<Long>): List<Item> = ids.chunked(500).flatMap { chunk ->
        readableDatabase.rawQuery("SELECT * FROM items WHERE source = 'SMS' AND id IN (${chunk.joinToString(",")})", null).use { c ->
            val l = ArrayList<Item>(c.count)
            while (c.moveToNext()) l += c.toItem()
            l
        }
    }

    /** Ids matching a query (used by cleanup and "delete all from sender"). */
    fun ids(q: ItemQuery): List<Long> {
        val (where, args) = whereOf(q)
        return readableDatabase.rawQuery("SELECT id FROM live_items WHERE $where", args).use { c ->
            val l = ArrayList<Long>(c.count)
            while (c.moveToNext()) l += c.getLong(0)
            l
        }
    }

    // ---------- chosen people ----------

    fun vips(): List<com.rez.notif.classify.VipPerson> =
        readableDatabase.rawQuery("SELECT id, name, numbers FROM vip ORDER BY name", null).use { c ->
            val l = ArrayList<com.rez.notif.classify.VipPerson>()
            while (c.moveToNext()) {
                l += com.rez.notif.classify.VipPerson(c.getLong(0), c.getString(1), c.getString(2).split(",").filter { it.isNotBlank() })
            }
            l
        }

    fun addVip(name: String, numbers: List<String>): Long {
        val v = ContentValues().apply {
            put("name", name)
            put("numbers", numbers.joinToString(","))
            put("created", System.currentTimeMillis())
        }
        return writableDatabase.insert("vip", null, v)
    }

    fun updateVip(id: Long, name: String, numbers: List<String>) {
        writableDatabase.update(
            "vip",
            ContentValues().apply { put("name", name); put("numbers", numbers.joinToString(",")) },
            "id = ?", arrayOf(id.toString()),
        )
    }

    /** Folds chosen people with the same name into one (all their numbers, all their items). */
    fun mergeVipsByName(): Boolean {
        val groups = vips().groupBy { it.name.trim().lowercase() }.values.filter { it.size > 1 }
        if (groups.isEmpty()) return false
        val w = writableDatabase
        w.beginTransaction()
        try {
            for (g in groups) {
                val keep = g.minBy { it.id }
                val numbers = g.flatMap { it.numbers }.distinct()
                w.update("vip", ContentValues().apply { put("numbers", numbers.joinToString(",")) }, "id = ?", arrayOf(keep.id.toString()))
                for (other in g) if (other.id != keep.id) {
                    w.update("items", ContentValues().apply { put("vip_id", keep.id) }, "vip_id = ?", arrayOf(other.id.toString()))
                    w.delete("vip", "id = ?", arrayOf(other.id.toString()))
                }
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        return true
    }

    fun removeVip(id: Long) {
        writableDatabase.delete("vip", "id = ?", arrayOf(id.toString()))
        val v = ContentValues().apply { put("vip_id", 0) }
        writableDatabase.update("items", v, "vip_id = ?", arrayOf(id.toString()))
    }

    fun setVip(ids: List<Long>, vipId: Long) {
        if (ids.isEmpty()) return
        val v = ContentValues().apply { put("vip_id", vipId) }
        ids.chunked(500).forEach { chunk ->
            writableDatabase.update("items", v, "id IN (${chunk.joinToString(",")})", null)
        }
    }

    /** Every item (also deleted ones) for re-tagging chosen people: id, source, sender, title, body. */
    fun allForVipScan(): List<Array<String>> =
        readableDatabase.rawQuery("SELECT id, source, sender, title, body FROM items", null).use { c ->
            val l = ArrayList<Array<String>>(c.count)
            while (c.moveToNext()) l += arrayOf(c.getLong(0).toString(), c.getString(1), c.getString(2), c.getString(3), c.getString(4))
            l
        }

    fun vipCounts(): Map<Long, Pair<Int, Long>> =
        readableDatabase.rawQuery("SELECT vip_id, COUNT(*), MAX(timestamp) FROM live_items WHERE vip_id > 0 GROUP BY vip_id", null).use { c ->
            val m = HashMap<Long, Pair<Int, Long>>()
            while (c.moveToNext()) m[c.getLong(0)] = c.getInt(1) to c.getLong(2)
            m
        }

    fun deleteAll() {
        writableDatabase.delete("items", null, null)
    }

    fun putOverride(senderKey: String, category: String) {
        val v = ContentValues().apply {
            put("sender_key", senderKey)
            put("category", category)
        }
        writableDatabase.insertWithOnConflict("sender_overrides", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun removeOverride(senderKey: String) {
        writableDatabase.delete("sender_overrides", "sender_key = ?", arrayOf(senderKey))
    }

    /** Recent categorized messages for the small offline model: (text, category id). */
    fun trainingSamples(limit: Int = 6000): List<Pair<String, String>> =
        readableDatabase.rawQuery(
            "SELECT title, body, category FROM live_items WHERE category != 'other' AND is_ad = 0 OR category = 'ads' " +
                "ORDER BY timestamp DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c ->
            val l = ArrayList<Pair<String, String>>()
            while (c.moveToNext()) l += (c.getString(0) + " " + c.getString(1)) to c.getString(2)
            l
        }

    /** SMS the rules left in "Other" (not chosen people): (id, text). */
    fun uncategorizedSms(): List<Pair<Long, String>> =
        readableDatabase.rawQuery(
            "SELECT id, body FROM live_items WHERE category = 'other' AND source = 'SMS' AND vip_id = 0",
            null,
        ).use { c ->
            val l = ArrayList<Pair<Long, String>>()
            while (c.moveToNext()) l += c.getLong(0) to c.getString(1)
            l
        }

    fun overrides(): Map<String, String> {
        val out = HashMap<String, String>()
        readableDatabase.rawQuery("SELECT sender_key, category FROM sender_overrides", null).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = c.getString(1)
        }
        return out
    }

    // ---------- reads ----------

    fun idOf(sourceKey: String): Long? =
        readableDatabase.rawQuery("SELECT id FROM live_items WHERE source_key = ?", arrayOf(sourceKey)).use { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        }

    fun get(id: Long): Item? =
        readableDatabase.rawQuery("SELECT * FROM live_items WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.toItem() else null
        }

    private fun whereOf(q: ItemQuery): Pair<String, Array<String>> {
        val where = ArrayList<String>()
        val args = ArrayList<String>()
        q.category?.let { where += "category = ?"; args += it }
        q.source?.let { where += "source = ?"; args += it.name }
        q.simSlot?.let { where += "sim_slot = ?"; args += it.toString() }
        q.sinceMillis?.let { where += "timestamp >= ?"; args += it.toString() }
        if (q.onlyValuable) where += "(is_valuable = 1 OR is_starred = 1)"
        q.vipId?.let { where += "vip_id = ?"; args += it.toString() }
        if (q.onlyVip) where += "vip_id > 0"
        q.beforeMillis?.let { where += "timestamp < ?"; args += it.toString() }
        q.sender?.let { where += "sender = ?"; args += it }
        q.packageName?.let { where += "package_name = ?"; args += it }
        if (q.excludeKept) where += "vip_id = 0 AND is_starred = 0 AND is_valuable = 0"
        if (q.onlyUnread) where += "is_read = 0"
        if (q.categories.isNotEmpty()) {
            where += "category IN (" + q.categories.joinToString(",") { "?" } + ")"
            args += q.categories
        }
        if (q.excludeCategories.isNotEmpty()) {
            where += "category NOT IN (" + q.excludeCategories.joinToString(",") { "?" } + ")"
            args += q.excludeCategories
        }
        if (q.onlyAds) where += "is_ad = 1" else if (!q.includeAds && q.category == null) where += "is_ad = 0"
        q.text?.trim()?.takeIf { it.isNotEmpty() }?.let { t ->
            val tails = q.textSenderTails.take(50)
            where += "(body LIKE ? OR title LIKE ? OR sender LIKE ? OR app_name LIKE ?" + tails.joinToString("") { " OR sender LIKE ?" } + ")"
            val like = "%$t%"
            repeat(4) { args += like }
            tails.forEach { args += "%$it" }
        }
        return (if (where.isEmpty()) "1=1" else where.joinToString(" AND ")) to args.toTypedArray()
    }

    fun query(q: ItemQuery): List<Item> {
        val (where, args) = whereOf(q)
        val sql = "SELECT * FROM live_items WHERE $where ORDER BY timestamp DESC LIMIT ${q.limit}"
        return readableDatabase.rawQuery(sql, args).use { c ->
            val list = ArrayList<Item>(c.count)
            while (c.moveToNext()) list += c.toItem()
            list
        }
    }

    fun count(q: ItemQuery): Int {
        val (where, args) = whereOf(q)
        return count(where, args)
    }

    /** Newest stored SMS time, to import only newer messages on sync. */
    fun latestSmsTime(): Long =
        readableDatabase.rawQuery("SELECT MAX(timestamp) FROM items WHERE source = 'SMS'", null).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
        }

    fun categoryCounts(): List<CategoryCount> =
        readableDatabase.rawQuery(
            "SELECT category, COUNT(*), SUM(CASE WHEN is_read = 0 THEN 1 ELSE 0 END) FROM live_items GROUP BY category",
            null,
        ).use { c ->
            val list = ArrayList<CategoryCount>()
            while (c.moveToNext()) list += CategoryCount(c.getString(0), c.getInt(1), c.getInt(2))
            list
        }

    fun count(where: String = "1=1", args: Array<String>? = null, table: String = "live_items"): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $table WHERE $where", args).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    fun stats(dayStarts: List<Long>): Stats {
        val db = readableDatabase
        val topApps = db.rawQuery(
            "SELECT app_name, package_name, COUNT(*) n FROM live_items WHERE source = 'NOTIFICATION' " +
                "GROUP BY package_name ORDER BY n DESC LIMIT 6",
            null,
        ).use { c ->
            val l = ArrayList<AppCount>()
            while (c.moveToNext()) l += AppCount(c.getString(0), c.getString(1), c.getInt(2))
            l
        }
        val topSenders = db.rawQuery(
            "SELECT sender, '', COUNT(*) n FROM live_items WHERE source = 'SMS' GROUP BY sender ORDER BY n DESC LIMIT 6",
            null,
        ).use { c ->
            val l = ArrayList<AppCount>()
            while (c.moveToNext()) l += AppCount(c.getString(0), c.getString(1), c.getInt(2))
            l
        }
        val days = dayStarts.mapIndexed { i, start ->
            val end = dayStarts.getOrNull(i + 1) ?: (start + 86_400_000L)
            val args = arrayOf(start.toString(), end.toString())
            DayCount(
                dayStart = start,
                total = count("timestamp >= ? AND timestamp < ?", args),
                ads = count("timestamp >= ? AND timestamp < ? AND is_ad = 1", args),
            )
        }
        // Hours and the 30-day trend are counted here from timestamps (local time zone).
        val monthStart = dayStarts.first() - 23 * 86_400_000L
        val byHour = IntArray(24)
        val perDay = IntArray(30)
        val cal = java.util.Calendar.getInstance()
        db.rawQuery("SELECT timestamp FROM live_items WHERE timestamp >= ?", arrayOf(monthStart.toString())).use { c ->
            while (c.moveToNext()) {
                val ts = c.getLong(0)
                cal.timeInMillis = ts
                byHour[cal.get(java.util.Calendar.HOUR_OF_DAY)]++
                val day = ((ts - monthStart) / 86_400_000L).toInt()
                if (day in 0..29) perDay[day]++
            }
        }
        return Stats(
            byHour = byHour.toList(),
            last30Days = perDay.toList(),
            sim1 = count("source = 'SMS' AND sim_slot = 1"),
            sim2 = count("source = 'SMS' AND sim_slot = 2"),
            total = count(),
            sms = count("source = 'SMS'"),
            notifications = count("source = 'NOTIFICATION'"),
            ads = count("is_ad = 1"),
            valuable = count("is_valuable = 1 OR is_starred = 1"),
            byCategory = categoryCounts().sortedByDescending { it.total },
            topApps = topApps,
            topSenders = topSenders,
            last7Days = days,
        )
    }

    // ---------- mapping ----------

    private fun Item.toValues() = ContentValues().apply {
        put("source", source.name)
        put("source_key", sourceKey)
        put("package_name", packageName)
        put("app_name", appName)
        put("sender", sender)
        put("title", title)
        put("body", body)
        put("timestamp", timestamp)
        put("sim_slot", simSlot)
        put("category", category)
        put("is_ad", if (isAd) 1 else 0)
        put("is_valuable", if (isValuable) 1 else 0)
        put("is_starred", if (isStarred) 1 else 0)
        put("is_read", if (isRead) 1 else 0)
        put("vip_id", vipId)
    }

    private fun Cursor.toItem(): Item {
        fun s(name: String) = getString(getColumnIndexOrThrow(name)) ?: ""
        fun i(name: String) = getInt(getColumnIndexOrThrow(name))
        return Item(
            id = getLong(getColumnIndexOrThrow("id")),
            source = runCatching { Source.valueOf(s("source")) }.getOrDefault(Source.SMS),
            sourceKey = s("source_key"),
            packageName = s("package_name"),
            appName = s("app_name"),
            sender = s("sender"),
            title = s("title"),
            body = s("body"),
            timestamp = getLong(getColumnIndexOrThrow("timestamp")),
            simSlot = i("sim_slot"),
            category = s("category"),
            isAd = i("is_ad") == 1,
            isValuable = i("is_valuable") == 1,
            isStarred = i("is_starred") == 1,
            isRead = i("is_read") == 1,
            vipId = getLong(getColumnIndexOrThrow("vip_id")),
        )
    }
}
