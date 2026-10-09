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

