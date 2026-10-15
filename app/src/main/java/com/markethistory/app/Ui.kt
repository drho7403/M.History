package com.markethistory.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaPlayer
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

val Titr = FontFamily(Font(R.font.titr))
val Nazanin = FontFamily(Font(R.font.nazanin))

val BgColor = Color(0xFF0B0F14)
val SurfaceColor = Color(0xFF141B24)
val CardColor = Color(0xFF1B2430)
val TextColor = Color(0xFFE6EDF3)
val MutedColor = Color(0xFF8B98A5)
val GreenColor = Color(0xFF26C281)
val RedColor = Color(0xFFEF5350)
val OutlineColor = Color(0xFF2B3744)

private fun TextStyle.sized(family: FontFamily, size: Int): TextStyle =
    copy(fontFamily = family, fontSize = size.sp, lineHeight = (size * 1.55f).sp)

private fun marketTypography(): Typography {
    val b = Typography()
    return Typography(
        displayLarge = b.displayLarge.copy(fontFamily = Titr),
        displayMedium = b.displayMedium.copy(fontFamily = Titr),
        displaySmall = b.displaySmall.copy(fontFamily = Titr),
        headlineLarge = b.headlineLarge.copy(fontFamily = Titr),
        headlineMedium = b.headlineMedium.copy(fontFamily = Titr),
        headlineSmall = b.headlineSmall.copy(fontFamily = Titr),
        titleLarge = b.titleLarge.sized(Titr, 24),
        titleMedium = b.titleMedium.sized(Titr, 20),
        titleSmall = b.titleSmall.sized(Titr, 17),
        bodyLarge = b.bodyLarge.sized(Nazanin, 19),
        bodyMedium = b.bodyMedium.sized(Nazanin, 17),
        bodySmall = b.bodySmall.sized(Nazanin, 15),
        labelLarge = b.labelLarge.sized(Nazanin, 18),
        labelMedium = b.labelMedium.sized(Nazanin, 16),
        labelSmall = b.labelSmall.sized(Nazanin, 14)
    )
}

@Composable
fun MarketTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = GreenColor,
        onPrimary = Color(0xFF062014),
        secondary = GreenColor,
        background = BgColor,
        onBackground = TextColor,
        surface = SurfaceColor,
        onSurface = TextColor,
        surfaceVariant = CardColor,
        onSurfaceVariant = MutedColor,
        error = RedColor,
        outline = OutlineColor
    )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = scheme, typography = marketTypography()) {
            Surface(modifier = Modifier.fillMaxSize(), color = BgColor) {
                content()
            }
        }
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت", tint = TextColor)
            }
        } else {
            Box(modifier = Modifier.size(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextColor,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp),
            maxLines = 1
        )
        actions()
    }
}

@Composable
fun ConfirmDialog(text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        title = { Text("تأیید", style = MaterialTheme.typography.titleMedium) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = RedColor, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("نام پوشه") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onOk(text.trim()) }) {
                Text("ذخیره", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

// ---------- media views ----------

fun decodeSampled(path: String, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = BitmapFactory.decodeFile(path, opts) ?: return null
    val deg = try {
        when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (e: Exception) {
        0f
    }
    if (deg == 0f) return bmp
    val m = Matrix().apply { postRotate(deg) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
}

@Composable
fun FileImage(path: String, modifier: Modifier, contentScale: ContentScale = ContentScale.Fit) {
    val bmp by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { decodeSampled(path, 1800)?.asImageBitmap() }
    }
    val b = bmp
    if (b != null) {
        Image(bitmap = b, contentDescription = null, modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier = modifier.background(CardColor))
    }
}

@Composable
fun ZoomableImageDialog(path: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)) {
            FileImage(
                path = path,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(8.dp)
                    .align(Alignment.TopStart)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "بستن", tint = Color.White)
            }
        }
    }
}

@Composable
fun VideoPlayer(path: String) {
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoPath(path)
                val mc = MediaController(ctx)
                mc.setAnchorView(this)
                setMediaController(mc)
                seekTo(1)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(12.dp))
    )
}

@Composable
fun AudioPlayer(path: String) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    DisposableEffect(path) {
        onDispose {
            player?.release()
            player = null
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            val p = player
            if (p != null && p.duration > 0) progress = p.currentPosition.toFloat() / p.duration.toFloat()
            delay(200)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardColor)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            val p = player
            if (playing && p != null) {
                p.pause()
                playing = false
            } else if (p == null) {
                try {
                    val np = MediaPlayer()
                    np.setDataSource(path)
                    np.setOnCompletionListener {
                        playing = false
                        progress = 0f
                        it.seekTo(0)
                    }
                    np.prepare()
                    np.start()
                    player = np
                    playing = true
                } catch (e: Exception) {
                    playing = false
                }
            } else {
                p.start()
                playing = true
            }
        }) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = "پخش",
                tint = GreenColor
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("ویس", style = MaterialTheme.typography.bodyMedium, color = MutedColor)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                color = GreenColor,
                trackColor = OutlineColor
            )
        }
    }
}

@Composable
fun SpacerH(h: Int) {
    Box(modifier = Modifier.height(h.dp))
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = GreenColor,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
    )
}

@Composable
fun Chip(text: String, color: Color = GreenColor) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 2.dp)
    )
}

@Composable
fun ClickableCard(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        content()
    }
}

@Composable
fun Arrange(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
