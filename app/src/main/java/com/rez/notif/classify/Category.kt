package com.rez.notif.classify

/**
 * Built-in categories. The id is what is stored in the database, so never rename an id.
 * "Valuable" is not a category: it is a flag that any item can carry (see Item.isValuable).
 */
enum class Category(val id: String, val label: String, val description: String, val color: Long) {
    OTP("otp", "کد تأیید", "کدهای ورود و رمزهای پویا", 0xFF5CF2FF),
    BANK("bank", "بانکی", "واریز، برداشت و مانده حساب", 0xFF3D7BFF),
    MISSED("missed", "تماس از دست رفته", "میس‌کال‌ها، از پیامک اپراتور یا اپ تلفن", 0xFFFB7185),
    PERSONAL("personal", "شخصی", "پیامک از شماره‌های معمولی", 0xFF34D399),
    SOCIAL("social", "پیام‌رسان‌ها", "تلگرام، واتس‌اپ، اینستاگرام و…", 0xFF8B5CF6),
    EMAIL("email", "ایمیل", "Gmail و بقیه اپ‌های ایمیل", 0xFFF472B6),
    SHOPPING("shopping", "خرید و ارسال", "سفارش، مرسوله و تاکسی", 0xFFFF8A3D),
    OPERATOR("operator", "اپراتور", "همراه اول، ایرانسل، شارژ و بسته", 0xFFFACC15),
    SERVICES("services", "خدمات و دولتی", "قبض، بیمه، یارانه و سامانه‌ها", 0xFF22D3EE),
    APPS("apps", "برنامه‌ها", "بقیه اپ‌های گوشی", 0xFF94A3B8),
    SYSTEM("system", "سیستم", "پیام‌های خود گوشی", 0xFF64748B),
    ADS("ads", "تبلیغاتی", "پیامک و نوتیف تبلیغی", 0xFFEF4444),
    OTHER("other", "سایر", "هر چیزی که دسته نگرفت", 0xFFA1A1AA);

    companion object {
        private val byId = entries.associateBy { it.id }
        fun of(id: String?): Category = byId[id] ?: OTHER
    }
}
