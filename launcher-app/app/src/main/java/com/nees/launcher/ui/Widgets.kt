package com.nees.launcher.ui

import android.app.AlarmManager
import android.content.Context
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.viewinterop.AndroidView
import com.nees.launcher.App
import com.nees.launcher.LauncherState
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Date

/** Clock + date + battery + next alarm — true black, text only, monochrome. */
@Composable
fun NativeWidgets(state: LauncherState, onClockTap: () -> Unit, onAlarmTap: () -> Unit) {
    val ctx = LocalContext.current
    val s = state.store
    // One ticker drives everything; it sleeps until the next minute boundary, so near-zero cost.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            val toNextMinute = 60_000 - (System.currentTimeMillis() % 60_000)
            delay(toNextMinute.coerceIn(1_000, 60_000))
        }
    }
    val is24 = remember(ctx) { DateFormat.is24HourFormat(ctx) }
    val timeFmt = remember(is24) { DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), if (is24) "Hm" else "hmm") }
    val dateStr = remember(now) { DateFormat.format("EEE, d MMM", Date(now)).toString() }
    val timeStr = remember(now, timeFmt) { DateFormat.format(timeFmt, Date(now)).toString() }

    Column(Modifier.fillMaxWidth().padding(horizontal = S.edge)) {
        if (s.showClock) {
            Text(
                timeStr, style = T.clock.tabular(), color = C.text,
                modifier = Modifier.tappable(Haptic.LIGHT, onClick = onClockTap),
            )
        }
        if (s.showDate) {
            Spacer(Modifier.height(S.xs))
            Text(dateStr, style = T.title, color = C.text2)
        }
        if (s.showBattery || s.showAlarm) {
            Spacer(Modifier.height(S.m))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (s.showBattery) {
                    val batt by produceState(-1 to false, now) { value = battery(ctx) }
                    val (pct, charging) = batt
                    if (pct >= 0) Text(
                        (if (charging) "charging · " else "") + "$pct%",
                        style = T.body.tabular(), color = C.text2,
                    )
                }
                if (s.showBattery && s.showAlarm) Text("     ", style = T.body)
                if (s.showAlarm) {
                    val alarm = remember(now) { nextAlarm(ctx) }
                    if (alarm != null) Text(
                        "alarm $alarm", style = T.body.tabular(), color = C.text2,
                        modifier = Modifier.tappable(Haptic.LIGHT, onClick = onAlarmTap),
                    )
                }
            }
        }
    }
}

private fun battery(ctx: Context): Pair<Int, Boolean> {
    val bm = ctx.getSystemService(BatteryManager::class.java)
    val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    val charging = bm.isCharging
    return pct to charging
}

private fun nextAlarm(ctx: Context): String? {
    val am = ctx.getSystemService(AlarmManager::class.java)
    val info = am.nextAlarmClock ?: return null
    val cal = Calendar.getInstance().apply { timeInMillis = info.triggerTime }
    val today = Calendar.getInstance()
    val pattern = if (cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) && cal.get(Calendar.YEAR) == today.get(Calendar.YEAR))
        (if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm a")
    else (if (DateFormat.is24HourFormat(ctx)) "EEE HH:mm" else "EEE h:mm a")
    return DateFormat.format(pattern, cal).toString()
}

/** Third-party AppWidget (secondary). Rendered inside the shared host. */
@Composable
fun ThirdPartyWidget(id: Int, modifier: Modifier = Modifier) {
    val provider = remember(id) { runCatching { App.instance.widgetManager.getAppWidgetInfo(id) }.getOrNull() }
    if (provider == null) return
    AndroidView(
        factory = { ctx -> App.instance.widgetHost.createView(ctx, id, provider).apply { setAppWidget(id, provider) } },
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(S.m)),
    )
}

