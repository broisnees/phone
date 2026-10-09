package com.nees.talk

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.PersistableBundle
import android.provider.BlockedNumberContract.BlockedNumbers

// ---------------------------------------------------------------- OTP

object Otp {
    private val keyword = Regex(
        "(otp|one[- ]?time|verification|verify|code|passcode|password|pin\\b|token|login|कोड|ओटीपी)",
        RegexOption.IGNORE_CASE,
    )
    private val candidate = Regex("(?<![\\d.,:/-])(\\d{4,8})(?![\\d.,:/-])")
    private val money = Regex("(rs\\.?|npr|inr|usd|\\$|balance|amount)\\s*$", RegexOption.IGNORE_CASE)

    /** The code in [body] if it reads like a one-time code, picking the number nearest the keyword. */
    fun find(body: String): String? {
        val kw = keyword.find(body) ?: return null
        return candidate.findAll(body)
            .filterNot { m -> money.containsMatchIn(body.substring(0, m.range.first).takeLast(8)) }
            .filterNot { m -> m.value.length == 4 && m.value.toInt() in 1900..2100 } // years
            .minByOrNull { m -> kotlin.math.abs(m.range.first - kw.range.first) }
            ?.value
    }

    /** Copies quietly: the clip is flagged sensitive so the system preview hides the digits. */
    fun copy(ctx: Context, code: String) {
        val clip = ClipData.newPlainText("Code", code).apply {
            description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }
}

// ---------------------------------------------------------------- Smart inbox

enum class Category(val label: String) { Personal("Personal"), Transactions("Money"), Codes("Codes"), Promotions("Offers") }

object Classifier {
    private val money = Regex(
        "(credited|debited|a/c|acct|account|balance|txn|transaction|npr|rs\\.?\\s?\\d|paid|payment|received|transfer|withdraw|deposit|esewa|khalti|fonepay|ime ?pay|wallet)",
        RegexOption.IGNORE_CASE,
    )
    private val promo = Regex(
        "(offer|% ?off|discount|sale|deal|cashback|bonus|recharge|data pack|pack|subscribe|visit|click|https?://|www\\.|win|free|limited|hurry|dial \\*)",
        RegexOption.IGNORE_CASE,
    )

    /** Senders with letters (NABIL_ALERT, NCELL) or short codes are businesses, not people. */
    fun isBusiness(address: String) = address.any(Char::isLetter) || address.count(Char::isDigit) in 1..6

    fun of(address: String, body: String, known: Boolean): Category = when {
        Otp.find(body) != null -> Category.Codes
        known || !isBusiness(address) -> Category.Personal
        money.containsMatchIn(body) -> Category.Transactions
        promo.containsMatchIn(body) -> Category.Promotions
        else -> Category.Promotions
    }
}

// ---------------------------------------------------------------- Caller info (Nepal-first)

data class NumberInfo(val carrier: String?, val region: String?, val kind: String)

object NumberIntel {
    private val mobile = mapOf(
        "984" to "NTC", "985" to "NTC", "986" to "NTC", "974" to "NTC", "975" to "NTC", "976" to "NTC",
        "980" to "Ncell", "981" to "Ncell", "982" to "Ncell", "970" to "Ncell",
        "961" to "Smart", "962" to "Smart", "988" to "Smart",
        "972" to "UTL",
    )
    private val areas = mapOf(
        "1" to "Kathmandu Valley", "61" to "Pokhara", "21" to "Biratnagar", "56" to "Chitwan", "71" to "Butwal",
        "81" to "Nepalgunj", "51" to "Birgunj", "41" to "Janakpur", "25" to "Dharan", "23" to "Jhapa",
        "91" to "Dhangadhi", "64" to "Syangja", "82" to "Dang", "57" to "Hetauda", "33" to "Rajbiraj",
    )
    private val countries = mapOf(
        "91" to "India", "1" to "USA / Canada", "44" to "United Kingdom", "61" to "Australia", "971" to "UAE",
        "974" to "Qatar", "966" to "Saudi Arabia", "965" to "Kuwait", "968" to "Oman", "973" to "Bahrain",
        "81" to "Japan", "82" to "South Korea", "60" to "Malaysia", "86" to "China", "49" to "Germany", "33" to "France",
    )

    fun of(raw: String): NumberInfo {
        if (Classifier.isBusiness(raw)) return NumberInfo(null, null, "Business")
        val digits = raw.filter(Char::isDigit)
        val intl = raw.trim().startsWith("+") || raw.trim().startsWith("00")
        val national = when {
            digits.startsWith("977") && intl -> digits.removePrefix("977")
            intl -> return countries.entries.sortedByDescending { it.key.length }
                .firstOrNull { digits.removePrefix("00").startsWith(it.key) }
                ?.let { NumberInfo(null, it.value, "International") } ?: NumberInfo(null, null, "International")
            else -> digits
        }
        if (national.length == 10 && national.startsWith("9")) {
            return NumberInfo(mobile[national.take(3)], null, "Mobile")
        }
        val local = national.removePrefix("0")
        val area = areas.entries.sortedByDescending { it.key.length }.firstOrNull { local.startsWith(it.key) }
        return NumberInfo(null, area?.value, if (local.length in 7..9) "Landline" else "Phone")
    }

    fun label(raw: String): String {
        val i = of(raw)
        return listOfNotNull(i.carrier, i.region, i.kind.takeIf { i.carrier == null && i.region == null }).joinToString(" · ")
    }
}

// ---------------------------------------------------------------- Preferences: pins, templates

object Prefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("talk", Context.MODE_PRIVATE)

    fun pinned(ctx: Context): Set<Long> = p(ctx).getStringSet("pins", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    fun togglePin(ctx: Context, threadId: Long) {
        val now = pinned(ctx).toMutableSet().apply { if (!add(threadId)) remove(threadId) }
        p(ctx).edit().putStringSet("pins", now.map(Long::toString).toSet()).apply()
    }

    val templates = listOf("On my way", "Call you in a bit", "Okay, done 👍", "Thank you!", "In a meeting, will text soon")
}

// ---------------------------------------------------------------- Blocking (system-wide via BlockedNumberContract)

