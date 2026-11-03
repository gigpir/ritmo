package io.github.gigpir.ritmo

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.gigpir.ritmo.core.Workout
import io.github.gigpir.ritmo.core.WorkoutFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/** Workouts travel as .ritmo files, the same as on iPhone: to a chat, an email or Files. */
object Sharing {
    /** Opens the share sheet with the workouts in a .ritmo file named [fileName]. */
    fun share(context: Context, workouts: List<Workout>, fileName: String) {
        val folder = File(context.cacheDir, "shared").apply { mkdirs() }
        folder.listFiles()?.forEach { it.delete() } // only the latest file is kept
        val name = fileName.replace(Regex("""[/\\:*?"<>|]"""), "-").trim().ifEmpty { "Ritmo workouts" }
        val file = File(folder, "$name.ritmo").apply { writeText(WorkoutFile.encode(workouts)) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null))
    }

    /** Adds the workouts of a .ritmo file opened in Ritmo, and says what happened. */
    fun import(context: Context, uri: Uri): String {
        val incoming = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readAtMost(1_000_000)?.let { WorkoutFile.decode(it.decodeToString()) }
            }.orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
        if (incoming.isEmpty()) return "This file doesn't contain Ritmo workouts."
        val added = context.ritmo.store.add(incoming)
        return when (added.size) {
            0 -> if (incoming.size == 1) "${incoming[0].name} is already in your list." else "These workouts are already in your list."
            1 -> "Added ${added[0].name}."
            else -> "Added ${added.size} workouts."
        }
    }

    /** Opens a web page, such as the donation page. */
    fun open(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            // No browser: nothing to open it with.
        }
    }

    /** A workout file is a few kilobytes: anything much bigger isn't one. */
    private fun InputStream.readAtMost(limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) return out.toByteArray()
            if (out.size() + count > limit) return null
            out.write(buffer, 0, count)
        }
    }
}
