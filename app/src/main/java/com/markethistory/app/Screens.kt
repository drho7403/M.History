package com.markethistory.app

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID

class AppState(val db: Db) {
    var version by mutableIntStateOf(0)
    fun bump() {
        version++
    }
}

sealed class Screen {
    object Home : Screen()
    data class FolderS(val id: Long) : Screen()
    data class Editor(val folderId: Long, val expId: Long?) : Screen()
    data class ViewS(val expId: Long) : Screen()
    object Backup : Screen()
}

data class MItem(val id: Long?, val type: String, val file: String, val oldFile: String?)

@Composable
fun MarketApp(app: AppState) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
    BackHandler(enabled = stack.size > 1) { pop() }

    when (val s = stack.last()) {
        is Screen.Home -> HomeScreen(
            app,
            onFolder = { stack.add(Screen.FolderS(it)) },
            onExp = { stack.add(Screen.ViewS(it)) },
            onBackup = { stack.add(Screen.Backup) }
        )
        is Screen.FolderS -> FolderScreen(
            app, s.id,
            onBack = { pop() },
            onFolder = { stack.add(Screen.FolderS(it)) },
            onNewExp = { stack.add(Screen.Editor(s.id, null)) },
            onExp = { stack.add(Screen.ViewS(it)) }
        )
        is Screen.Editor -> EditorScreen(app, s.folderId, s.expId, onDone = { pop() })
        is Screen.ViewS -> ViewScreen(
            app, s.expId,
            onBack = { pop() },
            onEdit = { folderId -> stack.add(Screen.Editor(folderId, s.expId)) }
        )
        is Screen.Backup -> BackupScreen(
            app,
            onBack = { pop() },
            onRestored = {
                stack.clear()
                stack.add(Screen.Home)
            }
        )
    }
}

// ---------------------------------------------------------------- rows

@Composable
fun FolderRow(
    f: Folder,
    subtitle: String,
    onOpen: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    ClickableCard(onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Folder, contentDescription = null, tint = GreenColor, modifier = Modifier.size(30.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(f.name, style = MaterialTheme.typography.bodyLarge, color = TextColor)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MutedColor)
                }
            }
            if (onRename != null) {
                IconButton(onClick = onRename) {
                    Icon(Icons.Filled.Edit, contentDescription = "تغییر نام", tint = MutedColor)
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = RedColor)
                }
            }
        }
    }
}

@Composable
fun ExpRow(e: Exp, where: String?, onClick: () -> Unit) {
    val d = parseDate(e.date)
    ClickableCard(onClick) {
        Column {
            Text(e.title, style = MaterialTheme.typography.bodyLarge, color = TextColor)
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(jalaliText(d), style = MaterialTheme.typography.bodySmall, color = MutedColor)
                if (e.symbol.isNotBlank()) Chip(e.symbol)
            }
            if (where != null) {
                Text(where, style = MaterialTheme.typography.bodySmall, color = MutedColor)
            }
        }
    }
}

@Composable
fun folderSubtitle(app: AppState, id: Long): String {
    return remember(app.version, id) {
        val subs = app.db.folders(id).size
        val exps = app.db.exps(id).size
        "$subs پوشه  •  $exps تجربه".fa()
    }
}

// ---------------------------------------------------------------- home

@Composable
fun HomeScreen(app: AppState, onFolder: (Long) -> Unit, onExp: (Long) -> Unit, onBackup: () -> Unit) {
    val db = app.db
    var query by remember { mutableStateOf("") }
    var showNew by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<Folder?>(null) }
    var delete by remember { mutableStateOf<Folder?>(null) }
    val folders = remember(app.version) { db.folders(0L) }
    val results = remember(query, app.version) { db.search(query) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar("مارکت هیستوری", null) {
            IconButton(onClick = onBackup) {
                Icon(Icons.Filled.Backup, contentDescription = "بک‌آپ", tint = GreenColor)
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("جستجو در پوشه‌ها و متن تجربه‌ها") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "پاک کردن")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (query.isBlank()) {
                item {
                    Button(onClick = { showNew = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("  پوشه جدید", style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (folders.isEmpty()) {
                    item {
                        Text(
                            "هنوز پوشه‌ای نساخته‌ای. با «پوشه جدید» شروع کن؛ مثلاً «اخبار».",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedColor,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
                items(folders, key = { it.id }) { f ->
                    FolderRow(
                        f, folderSubtitle(app, f.id),
                        onOpen = { onFolder(f.id) },
                        onRename = { rename = f },
                        onDelete = { delete = f }
                    )
                }
            } else {
                val (fs, es) = results
                if (fs.isEmpty() && es.isEmpty()) {
                    item {
                        Text("نتیجه‌ای پیدا نشد", style = MaterialTheme.typography.bodyMedium, color = MutedColor)
                    }
                }
                if (fs.isNotEmpty()) item { SectionLabel("پوشه‌ها") }
                items(fs, key = { "f" + it.id }) { f ->
                    FolderRow(f, db.folderPath(f.id), onOpen = { onFolder(f.id) }, onRename = null, onDelete = null)
                }
                if (es.isNotEmpty()) item { SectionLabel("تجربه‌ها") }
                items(es, key = { "e" + it.id }) { e ->
                    ExpRow(e, db.folderPath(e.folderId)) { onExp(e.id) }
                }
            }
        }
    }

    if (showNew) {
        NameDialog("پوشه جدید", "", onDismiss = { showNew = false }) { name ->
            db.addFolder(0L, name)
            app.bump()
            showNew = false
        }
    }
    val r = rename
    if (r != null) {
        NameDialog("تغییر نام پوشه", r.name, onDismiss = { rename = null }) { name ->
            db.renameFolder(r.id, name)
            app.bump()
            rename = null
        }
    }
    val d = delete
    if (d != null) {
        ConfirmDialog(
            "پوشه «${d.name}» با همه زیرپوشه‌ها و تجربه‌های داخلش حذف شود؟",
            "حذف",
            onConfirm = {
                db.deleteFolder(d.id)
                app.bump()
                delete = null
            },
            onDismiss = { delete = null }
        )
    }
}

// ---------------------------------------------------------------- folder

@Composable
fun FolderScreen(
    app: AppState,
    id: Long,
    onBack: () -> Unit,
    onFolder: (Long) -> Unit,
    onNewExp: () -> Unit,
    onExp: (Long) -> Unit
) {
    val db = app.db
    val folder = remember(app.version, id) { db.folder(id) }
    val subs = remember(app.version, id) { db.folders(id) }
    val exps = remember(app.version, id) { db.exps(id) }
    val path = remember(app.version, id) { db.folderPath(id) }
    var showNew by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<Folder?>(null) }
    var delete by remember { mutableStateOf<Folder?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(folder?.name ?: "", onBack)
        Text(
            path,
            style = MaterialTheme.typography.bodySmall,
            color = MutedColor,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = { showNew = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Folder, contentDescription = null)
                Text("  پوشه جدید", style = MaterialTheme.typography.labelLarge)
            }
            Button(onClick = onNewExp, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  ثبت تجربه", style = MaterialTheme.typography.labelLarge)
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (subs.isEmpty() && exps.isEmpty()) {
                item {
                    Text(
                        "این پوشه خالیه. پوشه جدید بساز یا اولین تجربه‌ات رو ثبت کن.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedColor,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            if (subs.isNotEmpty()) item { SectionLabel("پوشه‌ها") }
            items(subs, key = { "f" + it.id }) { f ->
                FolderRow(
                    f, folderSubtitle(app, f.id),
                    onOpen = { onFolder(f.id) },
                    onRename = { rename = f },
                    onDelete = { delete = f }
                )
            }
            if (exps.isNotEmpty()) item { SectionLabel("تجربه‌ها") }
            items(exps, key = { "e" + it.id }) { e ->
                ExpRow(e, null) { onExp(e.id) }
            }
        }
    }

    if (showNew) {
        NameDialog("پوشه جدید", "", onDismiss = { showNew = false }) { name ->
            db.addFolder(id, name)
            app.bump()
            showNew = false
        }
    }
    val r = rename
    if (r != null) {
        NameDialog("تغییر نام پوشه", r.name, onDismiss = { rename = null }) { name ->
            db.renameFolder(r.id, name)
            app.bump()
            rename = null
        }
    }
    val d = delete
    if (d != null) {
        ConfirmDialog(
            "پوشه «${d.name}» با همه زیرپوشه‌ها و تجربه‌های داخلش حذف شود؟",
            "حذف",
            onConfirm = {
                db.deleteFolder(d.id)
                app.bump()
                delete = null
            },
            onDismiss = { delete = null }
        )
    }
}

// ---------------------------------------------------------------- view experience

@Composable
fun ViewScreen(app: AppState, expId: Long, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val db = app.db
    val exp = remember(app.version, expId) { db.exp(expId) }
    val media = remember(app.version, expId) { db.media(expId) }
    var confirmDelete by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf<String?>(null) }
    var replacing by remember { mutableStateOf<MediaItem?>(null) }
    var srcFor by remember { mutableStateOf<MediaItem?>(null) }
    var showRecord by remember { mutableStateOf(false) }
    var delItem by remember { mutableStateOf<MediaItem?>(null) }

    val picker = rememberMediaPicker(db) { _, file ->
        val t = replacing
        replacing = null
        if (t != null) {
            db.replaceMedia(t.id, file)
            app.bump()
        } else {
            db.mediaFile(file).delete()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar("تجربه", onBack) {
            if (exp != null) {
                IconButton(onClick = { onEdit(exp.folderId) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "ویرایش", tint = TextColor)
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = RedColor)
                }
            }
        }
        if (exp != null) {
            val d = parseDate(exp.date)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(exp.title, style = MaterialTheme.typography.titleMedium, color = TextColor)
                SpacerH(8)
                Text(
                    db.folderPath(exp.folderId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedColor
                )
                SpacerH(10)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Chip(weekdayText(d) + " " + jalaliText(d))
                    Chip(gregText(d), MutedColor)
                }
                if (exp.symbol.isNotBlank()) {
                    SpacerH(8)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("نماد: ", style = MaterialTheme.typography.bodyMedium, color = MutedColor)
                        Chip(exp.symbol)
                    }
                }
                if (exp.body.isNotBlank()) {
                    SpacerH(14)
                    Text(
                        exp.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardColor)
                            .padding(14.dp)
                    )
                }
                if (media.isNotEmpty()) SectionLabel("فایل‌ها")
                for (m in media) {
                    key(m.id, m.file) {
                        val path = db.mediaFile(m.file).absolutePath
                        Column(modifier = Modifier.padding(bottom = 12.dp)) {
                            when (m.type) {
                                "image" -> FileImage(
                                    path,
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { zoom = path }
                                )
                                "video" -> VideoPlayer(path)
                                else -> AudioPlayer(path)
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                TextButton(onClick = {
                                    replacing = m
                                    srcFor = m
                                }) {
                                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = GreenColor)
                                    Text("  جایگزین", style = MaterialTheme.typography.labelLarge, color = GreenColor)
                                }
                                TextButton(onClick = { delItem = m }) {
                                    Icon(Icons.Filled.Delete, contentDescription = null, tint = RedColor)
                                    Text("  حذف", style = MaterialTheme.typography.labelLarge, color = RedColor)
                                }
                            }
                        }
                    }
                }
                SpacerH(24)
            }
        }
    }

    val z = zoom
    if (z != null) ZoomableImageDialog(z) { zoom = null }
    val s = srcFor
    if (s != null) {
        SourceDialog(
            "جایگزین کردن با",
            sourceOptions(s.type, picker) { showRecord = true },
            onClose = { srcFor = null },
            onCancel = {
                srcFor = null
                replacing = null
            }
        )
    }
    if (showRecord) {
        RecordDialog(
            db,
            onSaved = { file ->
                showRecord = false
                val t = replacing
                replacing = null
                if (t != null) {
                    db.replaceMedia(t.id, file)
                    app.bump()
                } else {
                    db.mediaFile(file).delete()
                }
            },
            onDismiss = {
                showRecord = false
                replacing = null
            }
        )
    }
    val di = delItem
    if (di != null) {
        ConfirmDialog(
            "این فایل حذف شود؟",
            "حذف",
            onConfirm = {
                db.deleteMedia(di.id)
                app.bump()
                delItem = null
            },
            onDismiss = { delItem = null }
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            "این تجربه با همه فایل‌هایش حذف شود؟",
            "حذف",
            onConfirm = {
                db.deleteExp(expId)
                app.bump()
                confirmDelete = false
                onBack()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

// ---------------------------------------------------------------- editor

@Composable
fun EditorScreen(app: AppState, folderId: Long, expId: Long?, onDone: () -> Unit) {
    val db = app.db
    val ctx = LocalContext.current
    val existing = remember { if (expId != null) db.exp(expId) else null }

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var symbol by remember { mutableStateOf(existing?.symbol ?: "") }
    var body by remember { mutableStateOf(existing?.body ?: "") }
    var date by remember { mutableStateOf(if (existing != null) parseDate(existing.date) else LocalDate.now()) }
    val media = remember {
        mutableStateListOf<MItem>().also { list ->
            if (expId != null) db.media(expId).forEach { list.add(MItem(it.id, it.type, it.file, null)) }
        }
    }
    val removed = remember { mutableStateListOf<Long>() }
    var showDate by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var showCam by remember { mutableStateOf(false) }
    var showRecord by remember { mutableStateOf(false) }
    var replacing by remember { mutableStateOf<MItem?>(null) }
    var srcFor by remember { mutableStateOf<MItem?>(null) }
    var delItem by remember { mutableStateOf<MItem?>(null) }

    fun dirty(): Boolean {
        val t0 = existing?.title ?: ""
        val s0 = existing?.symbol ?: ""
        val b0 = existing?.body ?: ""
        return title != t0 || symbol != s0 || body != b0 ||
            removed.isNotEmpty() || media.any { it.id == null || it.oldFile != null } ||
            (existing != null && date != parseDate(existing.date))
    }

    // swap the file of an item; saved experiences keep the old file until Save
    fun replaceItem(old: MItem, file: String) {
        val idx = media.indexOf(old)
        if (idx < 0) {
            db.mediaFile(file).delete()
            return
        }
        val fresh = if (old.id == null) {
            db.mediaFile(old.file).delete()
            MItem(null, old.type, file, null)
        } else if (old.oldFile == null) {
            MItem(old.id, old.type, file, old.file)
        } else {
            db.mediaFile(old.file).delete()
            MItem(old.id, old.type, file, old.oldFile)
        }
        media[idx] = fresh
    }

    fun onNewMedia(type: String, file: String) {
        val t = replacing
        replacing = null
        if (t == null) media.add(MItem(null, type, file, null)) else replaceItem(t, file)
    }

    val picker = rememberMediaPicker(db) { type, file -> onNewMedia(type, file) }

    fun cancel() {
        media.filter { it.id == null || it.oldFile != null }.forEach { db.mediaFile(it.file).delete() }
        onDone()
    }

    fun save() {
        if (title.isBlank()) {
            Toast.makeText(ctx, "عنوان تجربه را بنویس", Toast.LENGTH_SHORT).show()
            return
        }
        val id = db.saveExp(expId, folderId, title.trim(), date.toString(), symbol.trim(), body.trim())
        removed.forEach { db.deleteMedia(it) }
        for (m in media) {
            val mid = m.id
            if (mid == null) {
                db.addMedia(id, m.type, m.file)
            } else if (m.oldFile != null) {
                db.replaceMedia(mid, m.file)
            }
        }
        app.bump()
        onDone()
    }

    BackHandler(enabled = true) {
        if (dirty()) confirmDiscard = true else cancel()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(if (expId == null) "ثبت تجربه" else "ویرایش تجربه", onBack = {
            if (dirty()) confirmDiscard = true else cancel()
        }) {
            IconButton(onClick = { save() }) {
                Icon(Icons.Filled.Check, contentDescription = "ذخیره", tint = GreenColor)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("عنوان تجربه") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = symbol,
                onValueChange = { symbol = it },
                label = { Text("نماد (مثلاً XAUUSD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            ClickableCard(onClick = { showDate = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.DateRange, contentDescription = null, tint = GreenColor)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            weekdayText(date) + "  " + jalaliText(date),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextColor
                        )
                        Text(gregText(date), style = MaterialTheme.typography.bodySmall, color = MutedColor)
                    }
                    Text("تغییر", style = MaterialTheme.typography.labelMedium, color = GreenColor)
                }
            }
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("متن تجربه") },
                minLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            SectionLabel("فایل‌ها")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        replacing = null
                        showCam = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Text("  دوربین", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = {
                        replacing = null
                        showRecord = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null, tint = RedColor)
                    Text("  ضبط ویس", style = MaterialTheme.typography.labelLarge)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        replacing = null
                        picker.gallery("image")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Text(" عکس", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = {
                        replacing = null
                        picker.gallery("video")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Videocam, contentDescription = null)
                    Text(" فیلم", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = {
                        replacing = null
                        picker.gallery("audio")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.AudioFile, contentDescription = null)
                    Text(" صوت", style = MaterialTheme.typography.labelLarge)
                }
            }

            for (m in media.toList()) {
                key(m.file) {
                    val path = db.mediaFile(m.file).absolutePath
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            when (m.type) {
                                "image" -> FileImage(
                                    path,
                                    Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    ContentScale.Crop
                                )
                                "video" -> Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CardColor)
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Videocam, contentDescription = null, tint = GreenColor)
                                    Text("  فیلم", style = MaterialTheme.typography.bodyLarge, color = TextColor)
                                }
                                else -> AudioPlayer(path)
                            }
                        }
                        Column {
                            IconButton(onClick = {
                                replacing = m
                                srcFor = m
                            }) {
                                Icon(Icons.Filled.SwapHoriz, contentDescription = "جایگزین", tint = GreenColor)
                            }
                            IconButton(onClick = { delItem = m }) {
                                Icon(Icons.Filled.Delete, contentDescription = "حذف فایل", tint = RedColor)
                            }
                        }
                    }
                }
            }
            SpacerH(24)
        }
    }

    if (showDate) {
        DateDialog(date, onDismiss = { showDate = false }) {
            date = it
            showDate = false
        }
    }
    if (showCam) {
        SourceDialog(
            "دوربین",
            listOf<Pair<String, () -> Unit>>(
                "گرفتن عکس" to { picker.camera("image") },
                "فیلم‌برداری" to { picker.camera("video") }
            ),
            onClose = { showCam = false },
            onCancel = { showCam = false }
        )
    }
    val s = srcFor
    if (s != null) {
        SourceDialog(
            "جایگزین کردن با",
            sourceOptions(s.type, picker) { showRecord = true },
            onClose = { srcFor = null },
            onCancel = {
                srcFor = null
                replacing = null
            }
        )
    }
    if (showRecord) {
        RecordDialog(
            db,
            onSaved = { file ->
                showRecord = false
                onNewMedia("audio", file)
            },
            onDismiss = {
                showRecord = false
                replacing = null
            }
        )
    }
    val di = delItem
    if (di != null) {
        ConfirmDialog(
            "این فایل حذف شود؟",
            "حذف",
            onConfirm = {
                val mid = di.id
                if (mid != null) {
                    removed.add(mid)
                    if (di.oldFile != null) db.mediaFile(di.file).delete()
                } else {
                    db.mediaFile(di.file).delete()
                }
                media.remove(di)
                delItem = null
            },
            onDismiss = { delItem = null }
        )
    }
    if (confirmDiscard) {
        ConfirmDialog(
            "تغییرات ذخیره نشده از بین می‌رود. خارج می‌شوی؟",
            "خروج",
            onConfirm = {
                confirmDiscard = false
                cancel()
            },
            onDismiss = { confirmDiscard = false }
        )
    }
}

// ---------------------------------------------------------------- date dialog

fun shiftMonth(date: LocalDate, jalali: Boolean, d: Int): LocalDate {
    if (!jalali) return date.plusMonths(d.toLong())
    val (y, m, day) = toJalali(date)
    val total = y * 12 + (m - 1) + d
    val ny = Math.floorDiv(total, 12)
    val nm = Math.floorMod(total, 12) + 1
    return fromJalali(ny, nm, minOf(day, jalaliMonthLength(ny, nm)))
}

fun shiftYear(date: LocalDate, jalali: Boolean, d: Int): LocalDate {
    if (!jalali) return date.plusYears(d.toLong())
    val (y, m, day) = toJalali(date)
    val ny = y + d
    return fromJalali(ny, m, minOf(day, jalaliMonthLength(ny, m)))
}

@Composable
fun Stepper(label: String, value: String, onUp: () -> Unit, onDown: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MutedColor)
        IconButton(onClick = onUp) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = GreenColor)
        }
        Text(value, style = MaterialTheme.typography.titleSmall, color = TextColor)
        IconButton(onClick = onDown) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = GreenColor)
        }
    }
}

@Composable
fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Color.Black else TextColor,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) GreenColor else CardColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 6.dp)
    )
}

@Composable
fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onOk: (LocalDate) -> Unit) {
    var date by remember { mutableStateOf(initial) }
    var jalali by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        title = { Text("انتخاب تاریخ", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip("شمسی", jalali) { jalali = true }
                    ModeChip("میلادی", !jalali) { jalali = false }
                }
                SpacerH(12)
                val j = toJalali(date)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stepper(
                        "روز",
                        (if (jalali) j.third else date.dayOfMonth).toString().fa(),
                        onUp = { date = date.plusDays(1) },
                        onDown = { date = date.minusDays(1) }
                    )
                    Stepper(
                        "ماه",
                        if (jalali) JALALI_MONTHS[j.second - 1] else GREG_MONTHS[date.monthValue - 1],
                        onUp = { date = shiftMonth(date, jalali, 1) },
                        onDown = { date = shiftMonth(date, jalali, -1) }
                    )
                    Stepper(
                        "سال",
                        (if (jalali) j.first else date.year).toString().fa(),
                        onUp = { date = shiftYear(date, jalali, 1) },
                        onDown = { date = shiftYear(date, jalali, -1) }
                    )
                }
                SpacerH(10)
                Text(
                    weekdayText(date) + "  " + jalaliText(date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextColor
                )
                Text(gregText(date), style = MaterialTheme.typography.bodySmall, color = MutedColor)
            }
        },
        confirmButton = {
            TextButton(onClick = { onOk(date) }) {
                Text("تأیید", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

// ---------------------------------------------------------------- backup

@Composable
fun BackupScreen(app: AppState, onBack: () -> Unit, onRestored: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val ok = withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openOutputStream(uri)?.use { app.db.exportBackup(it) }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            busy = false
            status = if (ok) "بک‌آپ ذخیره شد ✓" else "ذخیره بک‌آپ ناموفق بود"
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pending = uri
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar("بک‌آپ", onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "از همه پوشه‌ها، تجربه‌ها و فایل‌ها (عکس، فیلم، ویس) یک فایل بک‌آپ می‌گیری. این فایل رو به گوشی دیگه منتقل کن، برنامه رو نصب کن و از همین صفحه «بازیابی» رو بزن.",
                style = MaterialTheme.typography.bodyLarge,
                color = MutedColor
            )
            Button(
                onClick = { exportLauncher.launch("market_history_backup_${LocalDate.now()}.zip") },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Backup, contentDescription = null)
                Text("  گرفتن بک‌آپ", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Restore, contentDescription = null)
                Text("  بازیابی از بک‌آپ", style = MaterialTheme.typography.labelLarge)
            }
            if (busy) Text("لطفاً صبر کن...", style = MaterialTheme.typography.bodyMedium, color = MutedColor)
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyLarge, color = GreenColor)
        }
    }

    val p = pending
    if (p != null) {
        ConfirmDialog(
            "با بازیابی، همه اطلاعات فعلی برنامه با اطلاعات فایل بک‌آپ جایگزین می‌شود. ادامه می‌دهی؟",
            "بازیابی",
            onConfirm = {
                pending = null
                scope.launch {
                    busy = true
                    val ok = withContext(Dispatchers.IO) {
                        try {
                            ctx.contentResolver.openInputStream(p)?.use { app.db.importBackup(it) } ?: false
                        } catch (e: Exception) {
                            false
                        }
                    }
                    busy = false
                    if (ok) {
                        app.bump()
                        Toast.makeText(ctx, "بازیابی انجام شد", Toast.LENGTH_SHORT).show()
                        onRestored()
                    } else {
                        status = "فایل بک‌آپ معتبر نیست"
                    }
                }
            },
            onDismiss = { pending = null }
        )
    }
}
