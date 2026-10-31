package com.nees.launcher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.nees.launcher.AppRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** App icon loaded off the main thread and cached in [AppRepo]. */
@Composable
fun AppIcon(appKey: String, size: Dp, modifier: Modifier = Modifier) {
    val bmp by produceState<Bitmap?>(null, appKey) {
        value = withContext(Dispatchers.IO) { AppRepo.icon(appKey) }
    }
    val b = bmp
    if (b != null) {
        Image(b.asImageBitmap(), null, modifier.size(size), contentScale = ContentScale.Fit)
    } else {
        Box(modifier.size(size).clip(RoundedCornerShape(size / 4)).background(C.raised))
    }
}

/** Icon with a label under it, the standard home/drawer cell. */
@Composable
fun LabeledIcon(appKey: String, label: String, iconSize: Dp, showLabel: Boolean = true, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AppIcon(appKey, iconSize)
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(label, style = T.iconLabel, color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}
