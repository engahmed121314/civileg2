package com.civileg.app.utils.telegram

/**
 * إعدادات بوت تيلجرام.
 *
 * ⚠️ تنبيه أمني: لا تقم برفع التوكن الفعلي إلى مستودع Git.
 * ضع القيم الفعلية في ملف `local.properties` تحت المفاتيح:
 *   telegram.bot.token=YOUR_BOT_TOKEN
 *   telegram.chat.id=YOUR_CHAT_ID
 *
 * لأغراض التطوير يمكن استخدام قيم افتراضية للاختبار فقط.
 */
object TelegramConfig {

    /**
     * توكن البوت الذي تم الحصول عليه من @BotFather.
     * يُفضل حقنه عبر BuildConfig أو local.properties.
     */
    const val BOT_TOKEN: String = "YOUR_BOT_TOKEN_HERE"

    /**
     * معرف المحادثة (Chat ID) الذي ستُ gửi إليه الإشعارات.
     */
    const val CHAT_ID: String = "YOUR_CHAT_ID_HERE"

    // نقطة نهاية API لإرسال رسالة
    const val SEND_MESSAGE_URL = "https://api.telegram.org/bot%s/sendMessage"
}
