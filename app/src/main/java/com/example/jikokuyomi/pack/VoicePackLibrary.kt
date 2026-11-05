package com.example.jikokuyomi.pack

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.jikokuyomi.model.Slot
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

data class SavedVoicePack(
    val id: String,
    val name: String,
    val fileCount: Int,
    val dir: File
)

class VoicePackLibrary(private val context: Context) {
    private val rootDir = File(context.filesDir, "packs").apply { mkdirs() }

    companion object {
        const val RANDOM_ID = "__random__"
        val SUPPORTED_EXTENSIONS = setOf("wav", "mp3", "m4a", "aac", "flac", "ogg")

        fun slotKey(slot: Slot): String {
            return String.format(Locale.US, "t%02d%02d", slot.hour, slot.minute)
        }

        fun minuteKey(minute: Int): String {
            return String.format(Locale.US, "m%02d", minute)
        }
    }

    fun getAllPacks(): List<SavedVoicePack> {
        val list = mutableListOf<SavedVoicePack>()
        val folders = rootDir.listFiles { file -> file.isDirectory } ?: return emptyList()

        for (folder in folders) {
            val metadataFile = File(folder, "pack.json")
            var packName = folder.name
            if (metadataFile.exists()) {
                try {
                    val json = JSONObject(metadataFile.readText())
                    if (json.has("name")) {
                        packName = json.getString("name")
                    } else if (json.has("speaker")) {
                        packName = "VOICEVOX (Speaker ${json.get("speaker")})"
                    }
                } catch (_: Exception) {}
            }

            val audioFiles = folder.listFiles { file ->
                val ext = file.extension.lowercase(Locale.US)
                SUPPORTED_EXTENSIONS.contains(ext)
            } ?: emptyArray()

            list.add(
                SavedVoicePack(
                    id = folder.name,
                    name = packName,
                    fileCount = audioFiles.size,
                    dir = folder
                )
            )
        }
        return list.sortedBy { it.name }
    }

    fun getAudioFile(packId: String, key: String): File? {
        val folder = File(rootDir, packId)
        if (!folder.exists() || !folder.isDirectory) return null

        for (ext in SUPPORTED_EXTENSIONS) {
            val candidate = File(folder, "$key.$ext")
            if (candidate.exists() && candidate.length() > 0) {
                return candidate
            }
        }
        return null
    }

    fun getAudioFileForSlot(packId: String, slot: Slot, minuteOnly: Boolean): File? {
        val key = if (minuteOnly) minuteKey(slot.minute) else slotKey(slot)
        return getAudioFile(packId, key)
    }

    /**
     * 話者IDから再生する音声ファイルを決める。
     * RANDOM_ID のときは、対象パック（空なら全パック）のうち、その時刻のファイルがあるものから1つ選ぶ。
     */
    fun pickAudioFile(
        speakerId: String,
        slot: Slot,
        minuteOnly: Boolean,
        randomPool: Set<String>,
        remember: Boolean = false
    ): File? {
        if (speakerId != RANDOM_ID) return getAudioFileForSlot(speakerId, slot, minuteOnly)
        val allIds = getAllPacks().map { it.id }
        val poolIds = if (randomPool.isEmpty()) allIds else allIds.filter { it in randomPool }
        val candidates = poolIds.mapNotNull { id -> getAudioFileForSlot(id, slot, minuteOnly)?.let { id to it } }
        if (candidates.isEmpty()) return null

        // 前回と同じ話者は続けて選ばない（候補が1つだけのときは、そのまま使う）
        val prefs = context.getSharedPreferences("jikokuyomi_random", Context.MODE_PRIVATE)
        val last = prefs.getString("lastPackId", null)
        val others = candidates.filter { it.first != last }
        val chosen = (if (others.isNotEmpty()) others else candidates).random()
        if (remember) prefs.edit().putString("lastPackId", chosen.first).apply()
        return chosen.second
    }

    fun importPack(folderUri: Uri, fallbackName: String? = null): SavedVoicePack? {
        val rootDoc = DocumentFile.fromTreeUri(context, folderUri) ?: return null
        val packId = "pack_" + UUID.randomUUID().toString().take(8)
        val packFolder = File(rootDir, packId).apply { mkdirs() }
        val metadataFile = File(packFolder, "pack.json")

        val files = mutableListOf<DocumentFile>()
        collectFilesRecursively(rootDoc, files)

        var importedCount = 0
        var detectedName: String? = fallbackName

        for (doc in files) {
            val name = doc.name ?: continue
            val lower = name.lowercase(Locale.US)

            if (lower == "pack.json") {
                copyDocumentToFile(doc, metadataFile)
                try {
                    val json = JSONObject(metadataFile.readText())
                    if (json.has("name")) {
                        detectedName = json.getString("name")
                    }
                } catch (_: Exception) {}
                continue
            }

            val dotIndex = lower.lastIndexOf('.')
            if (dotIndex == -1) continue
            val base = lower.substring(0, dotIndex)
            val ext = lower.substring(dotIndex + 1)

            if (SUPPORTED_EXTENSIONS.contains(ext)) {
                val isSlot = base.matches(Regex("^t(0[0-9]|1[0-9]|2[0-3])(00|15|30|45)$"))
                val isMinuteOnly = base.matches(Regex("^m(15|30|45)$"))
                if (isSlot || isMinuteOnly) {
                    val destFile = File(packFolder, "$base.$ext")
                    if (copyDocumentToFile(doc, destFile)) {
                        importedCount++
                    }
                }
            }
        }

        if (importedCount == 0) {
            packFolder.deleteRecursively()
            return null
        }

        val finalName = detectedName ?: rootDoc.name ?: "ボイスパック ${packId.takeLast(4)}"
        if (!metadataFile.exists()) {
            val meta = JSONObject().apply {
                put("id", packId)
                put("name", finalName)
                put("fileCount", importedCount)
            }
            metadataFile.writeText(meta.toString())
        }

        return SavedVoicePack(
            id = packId,
            name = finalName,
            fileCount = importedCount,
            dir = packFolder
        )
    }

    fun deletePack(packId: String) {
        val folder = File(rootDir, packId)
        if (folder.exists()) {
            folder.deleteRecursively()
        }
    }

    private fun collectFilesRecursively(dir: DocumentFile, outList: MutableList<DocumentFile>) {
        val children = dir.listFiles()
        for (child in children) {
            if (child.isDirectory) {
                collectFilesRecursively(child, outList)
            } else if (child.isFile) {
                outList.add(child)
            }
        }
    }

    private fun copyDocumentToFile(source: DocumentFile, dest: File): Boolean {
        return try {
            context.contentResolver.openInputStream(source.uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
