package com.markethistory.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class Folder(val id: Long, val parentId: Long, val name: String)
data class Exp(
    val id: Long,
    val folderId: Long,
    val title: String,
    val date: String, // yyyy-MM-dd (Gregorian)
    val symbol: String,
    val body: String
)
data class MediaItem(val id: Long, val expId: Long, val type: String, val file: String)

class Db(private val ctx: Context) : SQLiteOpenHelper(ctx, "market_history.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE folders(id INTEGER PRIMARY KEY AUTOINCREMENT, parent_id INTEGER NOT NULL, name TEXT NOT NULL)")
        db.execSQL("CREATE TABLE exps(id INTEGER PRIMARY KEY AUTOINCREMENT, folder_id INTEGER NOT NULL, title TEXT NOT NULL, date TEXT NOT NULL, symbol TEXT NOT NULL, body TEXT NOT NULL)")
        db.execSQL("CREATE TABLE media(id INTEGER PRIMARY KEY AUTOINCREMENT, exp_id INTEGER NOT NULL, type TEXT NOT NULL, file TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    val mediaDir: File
        get() = File(ctx.filesDir, "media").also { it.mkdirs() }

    fun mediaFile(name: String): File = File(mediaDir, name)

    private fun <T> query(sql: String, args: Array<String> = emptyArray(), f: (Cursor) -> T): List<T> {
        val out = ArrayList<T>()
        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) out.add(f(c))
        }
        return out
    }

    private fun readFolder(c: Cursor) = Folder(c.getLong(0), c.getLong(1), c.getString(2))
    private fun readExp(c: Cursor) =
        Exp(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5))

    // ---------- folders ----------
    fun folders(parent: Long): List<Folder> =
        query("SELECT id,parent_id,name FROM folders WHERE parent_id=? ORDER BY id", arrayOf(parent.toString())) { readFolder(it) }

    fun allFolders(): List<Folder> =
        query("SELECT id,parent_id,name FROM folders ORDER BY id") { readFolder(it) }

    fun folder(id: Long): Folder? =
        query("SELECT id,parent_id,name FROM folders WHERE id=?", arrayOf(id.toString())) { readFolder(it) }.firstOrNull()

    fun addFolder(parent: Long, name: String): Long {
        val cv = ContentValues()
        cv.put("parent_id", parent)
        cv.put("name", name)
        return writableDatabase.insert("folders", null, cv)
    }

    fun renameFolder(id: Long, name: String) {
        val cv = ContentValues()
        cv.put("name", name)
        writableDatabase.update("folders", cv, "id=?", arrayOf(id.toString()))
    }

    fun deleteFolder(id: Long) {
        for (sub in folders(id)) deleteFolder(sub.id)
        for (e in exps(id)) deleteExp(e.id)
        writableDatabase.delete("folders", "id=?", arrayOf(id.toString()))
    }

    fun folderPath(id: Long): String {
        val names = ArrayList<String>()
        var cur = id
        var guard = 0
        while (cur != 0L && guard < 50) {
            val f = folder(cur) ?: break
            names.add(0, f.name)
            cur = f.parentId
            guard++
        }
        return names.joinToString(" ‹ ")
    }

    // ---------- experiences ----------
    fun exps(folderId: Long): List<Exp> =
        query("SELECT id,folder_id,title,date,symbol,body FROM exps WHERE folder_id=? ORDER BY date DESC, id DESC", arrayOf(folderId.toString())) { readExp(it) }

    fun allExps(): List<Exp> =
        query("SELECT id,folder_id,title,date,symbol,body FROM exps ORDER BY date DESC, id DESC") { readExp(it) }

    fun exp(id: Long): Exp? =
        query("SELECT id,folder_id,title,date,symbol,body FROM exps WHERE id=?", arrayOf(id.toString())) { readExp(it) }.firstOrNull()

    fun saveExp(id: Long?, folderId: Long, title: String, date: String, symbol: String, body: String): Long {
        val cv = ContentValues()
        cv.put("folder_id", folderId)
        cv.put("title", title)
        cv.put("date", date)
        cv.put("symbol", symbol)
        cv.put("body", body)
        return if (id == null) {
            writableDatabase.insert("exps", null, cv)
        } else {
            writableDatabase.update("exps", cv, "id=?", arrayOf(id.toString()))
            id
        }
    }

    fun deleteExp(id: Long) {
        for (m in media(id)) deleteMedia(m.id)
        writableDatabase.delete("exps", "id=?", arrayOf(id.toString()))
    }

    // ---------- media ----------
    fun media(expId: Long): List<MediaItem> =
        query("SELECT id,exp_id,type,file FROM media WHERE exp_id=? ORDER BY id", arrayOf(expId.toString())) {
            MediaItem(it.getLong(0), it.getLong(1), it.getString(2), it.getString(3))
        }

    fun addMedia(expId: Long, type: String, file: String) {
        val cv = ContentValues()
        cv.put("exp_id", expId)
        cv.put("type", type)
        cv.put("file", file)
        writableDatabase.insert("media", null, cv)
    }

    fun deleteMedia(id: Long) {
        val m = query("SELECT id,exp_id,type,file FROM media WHERE id=?", arrayOf(id.toString())) {
            MediaItem(it.getLong(0), it.getLong(1), it.getString(2), it.getString(3))
        }.firstOrNull()
        if (m != null) mediaFile(m.file).delete()
        writableDatabase.delete("media", "id=?", arrayOf(id.toString()))
    }

    fun replaceMedia(id: Long, newFile: String) {
        val old = query("SELECT file FROM media WHERE id=?", arrayOf(id.toString())) { it.getString(0) }.firstOrNull()
        val cv = ContentValues()
        cv.put("file", newFile)
        writableDatabase.update("media", cv, "id=?", arrayOf(id.toString()))
        if (old != null && old != newFile) mediaFile(old).delete()
    }

    fun importMedia(uri: Uri, type: String): String? {
        val ext = when (type) {
            "image" -> "jpg"
            "video" -> "mp4"
            else -> "m4a"
        }
        val name = UUID.randomUUID().toString() + "." + ext
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                mediaFile(name).outputStream().use { out -> input.copyTo(out) }
            } ?: return null
            name
        } catch (e: Exception) {
            null
        }
    }

    // ---------- search ----------
    fun search(q: String): Pair<List<Folder>, List<Exp>> {
        val nq = normalize(q)
        if (nq.isBlank()) return Pair(emptyList(), emptyList())
        val fs = allFolders().filter { normalize(it.name).contains(nq) }
        val es = allExps().filter {
            normalize(it.title).contains(nq) ||
                normalize(it.body).contains(nq) ||
                normalize(it.symbol).contains(nq)
        }
        return Pair(fs, es)
    }

    // ---------- backup ----------
    fun exportBackup(out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            val root = JSONObject()
            val fa = JSONArray()
            for (f in allFolders()) {
                fa.put(JSONObject().put("id", f.id).put("parent", f.parentId).put("name", f.name))
            }
            val ea = JSONArray()
            val files = ArrayList<String>()
            for (e in allExps()) {
                val ma = JSONArray()
                for (m in media(e.id)) {
                    ma.put(JSONObject().put("type", m.type).put("file", m.file))
                    files.add(m.file)
                }
                ea.put(
                    JSONObject().put("id", e.id).put("folder", e.folderId).put("title", e.title)
                        .put("date", e.date).put("symbol", e.symbol).put("body", e.body).put("media", ma)
                )
            }
            root.put("version", 1).put("folders", fa).put("exps", ea)
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(root.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for (name in files) {
                val f = mediaFile(name)
                if (!f.exists()) continue
                zip.putNextEntry(ZipEntry("media/$name"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun importBackup(input: InputStream): Boolean {
        val staging = File(ctx.cacheDir, "restore")
        staging.deleteRecursively()
        staging.mkdirs()
        var json: String? = null
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val base = File(entry.name).name
                    if (entry.name == "data.json") {
                        json = zip.readBytes().toString(Charsets.UTF_8)
                    } else if (entry.name.startsWith("media/") && base.isNotEmpty()) {
                        File(staging, base).outputStream().use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                }
            }
        } catch (e: Exception) {
            staging.deleteRecursively()
            return false
        }
        val text = json
        if (text == null) {
            staging.deleteRecursively()
            return false
        }
        return try {
            val root = JSONObject(text)
            val db = writableDatabase
            db.beginTransaction()
            try {
                db.delete("media", null, null)
                db.delete("exps", null, null)
                db.delete("folders", null, null)
                mediaDir.listFiles()?.forEach { it.delete() }
                val fa = root.getJSONArray("folders")
                for (i in 0 until fa.length()) {
                    val o = fa.getJSONObject(i)
                    val cv = ContentValues()
                    cv.put("id", o.getLong("id"))
                    cv.put("parent_id", o.getLong("parent"))
                    cv.put("name", o.getString("name"))
                    db.insert("folders", null, cv)
                }
                val ea = root.getJSONArray("exps")
                for (i in 0 until ea.length()) {
                    val o = ea.getJSONObject(i)
                    val cv = ContentValues()
                    cv.put("id", o.getLong("id"))
                    cv.put("folder_id", o.getLong("folder"))
                    cv.put("title", o.getString("title"))
                    cv.put("date", o.getString("date"))
                    cv.put("symbol", o.getString("symbol"))
                    cv.put("body", o.getString("body"))
                    db.insert("exps", null, cv)
                    val ma = o.getJSONArray("media")
                    for (j in 0 until ma.length()) {
                        val m = ma.getJSONObject(j)
                        val mv = ContentValues()
                        mv.put("exp_id", o.getLong("id"))
                        mv.put("type", m.getString("type"))
                        mv.put("file", m.getString("file"))
                        db.insert("media", null, mv)
                    }
                }
                staging.listFiles()?.forEach { f -> f.copyTo(File(mediaDir, f.name), overwrite = true) }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            staging.deleteRecursively()
            true
        } catch (e: Exception) {
            staging.deleteRecursively()
            false
        }
    }
}

fun normalize(s: String): String {
    val sb = StringBuilder()
    for (ch in s.lowercase()) {
        sb.append(
            when (ch) {
                'ي' -> 'ی'
                'ك' -> 'ک'
                in '۰'..'۹' -> '0' + (ch - '۰')
                in '٠'..'٩' -> '0' + (ch - '٠')
                else -> ch
            }
        )
    }
    return sb.toString().trim()
}
