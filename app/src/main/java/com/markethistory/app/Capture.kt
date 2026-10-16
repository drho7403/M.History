package com.markethistory.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class Rec {
    var recorder: MediaRecorder? = null
    var file: String? = null
    var start: Long = 0L
}

class MediaPicker(
    val camera: (String) -> Unit,
    val gallery: (String) -> Unit
)

/** Phone camera (photo/video) and gallery/file pickers. onResult(type, fileName) is called with a file already stored in the app. */
@Composable
fun rememberMediaPicker(db: Db, onResult: (String, String) -> Unit): MediaPicker {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onResult)
    var pendingCam by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingType by remember { mutableStateOf("image") }

    val photo = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val p = pendingCam
        pendingCam = null
        if (p != null) {
            val f = db.mediaFile(p.second)
            if (ok && f.exists() && f.length() > 0) callback(p.first, p.second) else f.delete()
        }
    }
    val video = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val p = pendingCam
        pendingCam = null
        if (p != null) {
            val f = db.mediaFile(p.second)
            if (ok && f.exists() && f.length() > 0) callback(p.first, p.second) else f.delete()
        }
    }
    val gal = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val t = pendingType
            scope.launch {
                val f = withContext(Dispatchers.IO) { db.importMedia(uri, t) }
                if (f != null) callback(t, f)
            }
        }
    }

    return remember {
        MediaPicker(
            camera = { type ->
                val name = UUID.randomUUID().toString() + (if (type == "image") ".jpg" else ".mp4")
                val file = db.mediaFile(name)
                try {
                    file.createNewFile()
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
                    pendingCam = Pair(type, name)
                    if (type == "image") photo.launch(uri) else video.launch(uri)
                } catch (e: Exception) {
                    file.delete()
                    Toast.makeText(ctx, "باز کردن دوربین ممکن نشد", Toast.LENGTH_SHORT).show()
                }
            },
            gallery = { type ->
                pendingType = type
                gal.launch(
                    when (type) {
                        "image" -> "image/*"
                        "video" -> "video/*"
                        else -> "audio/*"
                    }
                )
            }
        )
    }
}

fun sourceOptions(type: String, picker: MediaPicker, onRecord: () -> Unit): List<Pair<String, () -> Unit>> =
    when (type) {
        "image" -> listOf<Pair<String, () -> Unit>>(
            "گرفتن عکس با دوربین" to { picker.camera("image") },
            "انتخاب از گالری" to { picker.gallery("image") }
        )
        "video" -> listOf<Pair<String, () -> Unit>>(
            "فیلم‌برداری با دوربین" to { picker.camera("video") },
            "انتخاب از گالری" to { picker.gallery("video") }
        )
        else -> listOf<Pair<String, () -> Unit>>(
            "ضبط ویس جدید" to onRecord,
            "انتخاب فایل صوتی" to { picker.gallery("audio") }
        )
    }

@Composable
fun SourceDialog(
    title: String,
    options: List<Pair<String, () -> Unit>>,
    onClose: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = SurfaceColor,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (o in options) {
                    OutlinedButton(
                        onClick = {
                            onClose()
                            o.second()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(o.first, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("انصراف", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@Suppress("DEPRECATION")
private fun newRecorder(ctx: Context): MediaRecorder =
    if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()

/** Starts recording as soon as it opens; "stop and save" returns the stored file name. */
@Composable
fun RecordDialog(db: Db, onSaved: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val rec = remember { Rec() }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }

    fun start() {
        try {
            val name = UUID.randomUUID().toString() + ".m4a"
            val r = newRecorder(ctx)
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96000)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(db.mediaFile(name).absolutePath)
            r.prepare()
            r.start()
            rec.recorder = r
            rec.file = name
            rec.start = System.currentTimeMillis()
            elapsed = 0L
            recording = true
        } catch (e: Exception) {
            Toast.makeText(ctx, "شروع ضبط ممکن نشد", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    fun finish(keep: Boolean): String? {
        val r = rec.recorder
        var ok = keep
        if (r != null) {
            try {
                r.stop()
            } catch (e: Exception) {
                ok = false
            }
            r.release()
        }
        rec.recorder = null
        recording = false
        val name = rec.file
        rec.file = null
        if (name == null) return null
        val f = db.mediaFile(name)
        if (ok && f.exists() && f.length() > 0) return name
        f.delete()
        return null
    }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            start()
        } else {
            Toast.makeText(ctx, "برای ضبط ویس اجازه میکروفون لازمه", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            start()
        } else {
            perm.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    LaunchedEffect(recording) {
        while (recording) {
            elapsed = System.currentTimeMillis() - rec.start
            delay(300)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            val r = rec.recorder
            if (r != null) {
                try {
                    r.stop()
                } catch (e: Exception) {
                }
                r.release()
                rec.recorder = null
                val n = rec.file
                if (n != null) db.mediaFile(n).delete()
            }
        }
    }

    val secs = (elapsed / 1000).toInt()
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        containerColor = SurfaceColor,
        title = { Text("ضبط ویس", style = MaterialTheme.typography.titleMedium) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (recording) RedColor else MutedColor)
                )
                Text(
                    if (recording) "  در حال ضبط  " + "%02d:%02d".format(secs / 60, secs % 60).fa() else "  در حال آماده‌سازی...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextColor
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = recording,
                onClick = {
                    val n = finish(true)
                    if (n != null) {
                        onSaved(n)
                    } else {
                        Toast.makeText(ctx, "ضبط خیلی کوتاه بود", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                }
            ) {
                Text("توقف و ذخیره", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                finish(false)
                onDismiss()
            }) {
                Text("انصراف", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}
