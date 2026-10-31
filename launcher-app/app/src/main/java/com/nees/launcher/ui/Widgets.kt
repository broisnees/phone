package com.nees.launcher.ui

import android.app.AlarmManager
import android.content.Context
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nees.launcher.App
import com.nees.launcher.Claude
import com.nees.launcher.ClaudeUsage
import com.nees.launcher.ClockStyle
import com.nees.launcher.LauncherState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Premium minimal clock + date + battery + next alarm + Claude usage — true black, mono. */
@Composable
fun NativeWidgets(state: LauncherState, onClockTap: () -> Unit, onAlarmTap: () -> Unit) {
    val ctx = LocalContext.current
    val s = state.store
    // One minute-aligned ticker; sleeps to the next minute boundary (no per-second redraw).
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay((60_000 - System.currentTimeMillis() % 60_000).coerceIn(1_000, 60_000))
        }
    }
    val is24 = remember(ctx) { DateFormat.is24HourFormat(ctx) }
    val timePat = remember(is24) { if (is24) "H:mm" else "h:mm" }
    val ampmPat = remember(is24) { if (is24) "" else " a" }
    val time = remember(now, timePat) { DateFormat.format(timePat, Date(now)).toString() }
    val ampm = remember(now, ampmPat) { if (ampmPat.isEmpty()) "" else DateFormat.format("a", Date(now)).toString().lowercase() }
    val date = remember(now) { DateFormat.format("EEE, d MMM", Date(now)).toString() }

    Column(Modifier.fillMaxWidth().padding(horizontal = S.edge)) {
        if (s.showClock) {
            when (s.clockStyle) {
                ClockStyle.Stacked -> {
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.tappable(Haptic.LIGHT, onClick = onClockTap)) {
                        Text(time, style = T.clock.copy(fontWeight = FontWeight.Normal, letterSpacing = (-3.5).sp).tabular(), color = C.text)
                        if (ampm.isNotEmpty()) Text(ampm, style = T.title.copy(fontWeight = FontWeight.Normal), color = C.text2, modifier = Modifier.padding(bottom = 14.dp, start = 6.dp))
                    }
                    if (s.showDate) {
                        Spacer(Modifier.height(S.xs))
                        Text(date, style = dateStyle(), color = C.text2)
                    }
                }
                ClockStyle.Inline -> {
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.tappable(Haptic.LIGHT, onClick = onClockTap)) {
                        Text(time + if (ampm.isNotEmpty()) " $ampm" else "", style = T.display.copy(fontWeight = FontWeight.Normal, fontSize = 46.sp, lineHeight = 50.sp, letterSpacing = (-2.0).sp).tabular(), color = C.text)
                        if (s.showDate) {
                            Spacer(Modifier.width(S.m))
                            Text(date, style = dateStyle(), color = C.text2, modifier = Modifier.padding(bottom = 8.dp))
                        }
                    }
                }
            }
        }

        if (s.showBattery || s.showAlarm) {
            Spacer(Modifier.height(S.m))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (s.showBattery) {
                    val batt by produceState(-1 to false, now) { value = withContext(Dispatchers.IO) { battery(ctx) } }
                    val (pct, charging) = batt
                    if (pct >= 0) Text((if (charging) "charging · " else "") + "$pct%", style = T.body.tabular(), color = C.text2)
                }
                if (s.showBattery && s.showAlarm) Text("      ", style = T.body)
                if (s.showAlarm) {
                    val alarm = remember(now) { nextAlarm(ctx) }
                    if (alarm != null) Text("alarm $alarm", style = T.body.tabular(), color = C.text2, modifier = Modifier.tappable(Haptic.LIGHT, onClick = onAlarmTap))
                }
            }
        }

        if (s.showClaude) {
            Spacer(Modifier.height(S.l))
            ClaudeWidget()
        }
    }
}

@Composable
private fun dateStyle() = T.title.copy(fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = (-0.3).sp)

/** Claude Code 5-hour window: tokens used + reset countdown + thin elapsed bar. Reads via root. */
@Composable
private fun ClaudeWidget() {
    val ctx = LocalContext.current
    // Own 60s refresh loop; root read on IO. Cancels when it leaves composition (home hidden).
    val usage by produceState<ClaudeUsage?>(null) {
        while (true) {
            value = withContext(Dispatchers.IO) { runCatching { Claude.read(ctx) }.getOrNull() }
            delay(60_000)
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Text("CLAUDE", style = T.label, color = C.text3)
        Spacer(Modifier.height(S.xs))
        val u = usage
        when {
            u == null || !u.present -> Text("—", style = T.title.copy(fontWeight = FontWeight.Normal), color = C.text3)
            !u.active -> {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("ready", style = T.title.copy(fontWeight = FontWeight.Normal), color = C.text)
                    Text("  new 5h window", style = T.sub, color = C.text2, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            else -> {
                val now = System.currentTimeMillis()
                val remain = (u.resetAt - now).coerceAtLeast(0)
                val h = remain / 3_600_000
                val m = (remain % 3_600_000) / 60_000
                val elapsed = ((now - u.windowStart).toFloat() / (u.resetAt - u.windowStart)).coerceIn(0f, 1f)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmt(u.usedTokens), style = T.title.copy(fontWeight = FontWeight.Normal).tabular(), color = C.text)
                    Text("  tokens · resets in ${h}h ${m}m", style = T.sub.tabular(), color = C.text2, modifier = Modifier.padding(bottom = 3.dp))
                }
                Spacer(Modifier.height(S.s))
                Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(C.hairline)) {
                    Box(Modifier.fillMaxWidth(elapsed).height(2.dp).background(C.text))
                }
            }
        }
    }
}

private fun fmt(n: Long): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
    n >= 1_000 -> "${n / 1000}k"
    else -> n.toString()
}

private fun battery(ctx: Context): Pair<Int, Boolean> {
    val bm = ctx.getSystemService(BatteryManager::class.java)
    return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) to bm.isCharging
}

private fun nextAlarm(ctx: Context): String? {
    val info = ctx.getSystemService(AlarmManager::class.java).nextAlarmClock ?: return null
    val cal = Calendar.getInstance().apply { timeInMillis = info.triggerTime }
    val today = Calendar.getInstance()
    val sameDay = cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) && cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
    val pat = if (DateFormat.is24HourFormat(ctx)) (if (sameDay) "HH:mm" else "EEE HH:mm") else (if (sameDay) "h:mm a" else "EEE h:mm a")
    return DateFormat.format(pat, cal).toString()
}

/** Third-party AppWidget (secondary), inside the shared host. */
@Composable
fun ThirdPartyWidget(id: Int, modifier: Modifier = Modifier) {
    val provider = remember(id) { runCatching { App.instance.widgetManager.getAppWidgetInfo(id) }.getOrNull() }
    if (provider == null) return
    AndroidView(
        factory = { ctx -> App.instance.widgetHost.createView(ctx, id, provider).apply { setAppWidget(id, provider) } },
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(S.m)),
    )
}
