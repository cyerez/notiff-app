package com.rez.notif.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Plain SQLite storage on the phone. No internet, nothing leaves the device.
 * (Phase 2 plan: encrypt this file with SQLCipher + Android Keystore.)
 */
class Db(context: Context) : SQLiteOpenHelper(context, "notif.db", null, 1) {

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
        db.execSQL("CREATE INDEX idx_items_cat_time ON items(category, timestamp DESC)")
        db.execSQL("CREATE INDEX idx_items_time ON items(timestamp DESC)")
        db.execSQL("CREATE INDEX idx_items_sender ON items(sender)")
        // What the user taught the app: "everything from this sender goes to that category".
        db.execSQL("CREATE TABLE sender_overrides (sender_key TEXT PRIMARY KEY, category TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 only. Future migrations go here; never drop user data.
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

    fun overrides(): Map<String, String> {
        val out = HashMap<String, String>()
        readableDatabase.rawQuery("SELECT sender_key, category FROM sender_overrides", null).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = c.getString(1)
        }
        return out
    }

    // ---------- reads ----------

    fun get(id: Long): Item? =
        readableDatabase.rawQuery("SELECT * FROM items WHERE id = ?", arrayOf(id.toString())).use { c ->
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
        if (q.onlyAds) where += "is_ad = 1" else if (!q.includeAds && q.category == null) where += "is_ad = 0"
        q.text?.trim()?.takeIf { it.isNotEmpty() }?.let { t ->
            where += "(body LIKE ? OR title LIKE ? OR sender LIKE ? OR app_name LIKE ?)"
            val like = "%$t%"
            repeat(4) { args += like }
        }
        return (if (where.isEmpty()) "1=1" else where.joinToString(" AND ")) to args.toTypedArray()
    }

    fun query(q: ItemQuery): List<Item> {
        val (where, args) = whereOf(q)
        val sql = "SELECT * FROM items WHERE $where ORDER BY timestamp DESC LIMIT ${q.limit}"
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
            "SELECT category, COUNT(*), SUM(CASE WHEN is_read = 0 THEN 1 ELSE 0 END) FROM items GROUP BY category",
            null,
        ).use { c ->
            val list = ArrayList<CategoryCount>()
            while (c.moveToNext()) list += CategoryCount(c.getString(0), c.getInt(1), c.getInt(2))
            list
        }

    fun count(where: String = "1=1", args: Array<String>? = null): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM items WHERE $where", args).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    fun stats(dayStarts: List<Long>): Stats {
        val db = readableDatabase
        val topApps = db.rawQuery(
            "SELECT app_name, package_name, COUNT(*) n FROM items WHERE source = 'NOTIFICATION' " +
                "GROUP BY package_name ORDER BY n DESC LIMIT 6",
            null,
        ).use { c ->
            val l = ArrayList<AppCount>()
            while (c.moveToNext()) l += AppCount(c.getString(0), c.getString(1), c.getInt(2))
            l
        }
        val topSenders = db.rawQuery(
            "SELECT sender, '', COUNT(*) n FROM items WHERE source = 'SMS' GROUP BY sender ORDER BY n DESC LIMIT 6",
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
        return Stats(
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
        )
    }
}
