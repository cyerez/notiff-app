package com.rez.notif.data

/** Where an item came from. */
enum class Source { SMS, NOTIFICATION }

/** One stored SMS or notification. */
data class Item(
    val id: Long = 0,
    val source: Source,
    /** Unique key so the same message is never stored twice. */
    val sourceKey: String,
    /** Package of the app that posted it (SMS: empty). */
    val packageName: String,
    /** Human name of the app (SMS: "پیامک"). */
    val appName: String,
    /** SMS sender number/name, or notification title. */
    val sender: String,
    val title: String,
    val body: String,
    val timestamp: Long,
    /** SIM slot 1 or 2 for SMS, 0 when unknown. */
    val simSlot: Int = 0,
    val category: String,
    val isAd: Boolean = false,
    val isValuable: Boolean = false,
    val isStarred: Boolean = false,
    val isRead: Boolean = false,
    /** Id of the chosen person this belongs to, 0 when none. */
    val vipId: Long = 0,
)

data class CategoryCount(val category: String, val total: Int, val unread: Int)

data class AppCount(val appName: String, val packageName: String, val total: Int)

data class DayCount(val dayStart: Long, val total: Int, val ads: Int)

data class Stats(
    val total: Int,
    val sms: Int,
    val notifications: Int,
    val ads: Int,
    val valuable: Int,
    val byCategory: List<CategoryCount>,
    val topApps: List<AppCount>,
    val topSenders: List<AppCount>,
    val last7Days: List<DayCount>,
    /** Items per hour of day (0..23) over the last 30 days. */
    val byHour: List<Int> = List(24) { 0 },
    /** Items per day for the last 30 days, oldest first. */
    val last30Days: List<Int> = emptyList(),
    val sim1: Int = 0,
    val sim2: Int = 0,
)

/** Filters for list and search screens. */
data class ItemQuery(
    val category: String? = null,
    val text: String? = null,
    val source: Source? = null,
    val simSlot: Int? = null,
    val onlyValuable: Boolean = false,
    val onlyAds: Boolean = false,
    /** When false, ads are left out (unless onlyAds). */
    val includeAds: Boolean = false,
    val sinceMillis: Long? = null,
    val beforeMillis: Long? = null,
    val vipId: Long? = null,
    val onlyVip: Boolean = false,
    val sender: String? = null,
    val packageName: String? = null,
    /** Leave out what must never be auto-deleted: chosen people, starred, valuable. */
    val excludeKept: Boolean = false,
    val onlyUnread: Boolean = false,
    /** Any of these categories (a home tab's group). */
    val categories: Set<String> = emptySet(),
    /** With [text]: also match senders ending in these digits (numbers of contacts whose name matches). */
    val textSenderTails: List<String> = emptyList(),
    /** Categories to leave out (for "today without the clutter"). */
    val excludeCategories: Set<String> = emptySet(),
    val limit: Int = 300,
)
