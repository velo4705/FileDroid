package com.filedroid.local

import android.content.Context
import android.os.Environment
import java.io.File
import javax.inject.Inject

class LocalFileRepository @Inject constructor() {

    /** List contents of [directory], directories first then files, both sorted by name. */
    fun listDirectory(directory: File): Result<List<LocalFile>> = runCatching {
        require(directory.isDirectory) { "Not a directory: ${directory.absolutePath}" }
        val entries = directory.listFiles() ?: emptyArray()
        entries
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .map { LocalFile(it) }
    }

    fun getDefaultRoot(): File = Environment.getExternalStorageDirectory()

    /** Returns Termux home dir if it exists on this device. */
    fun getTermuxHome(): File? {
        val f = File("/data/data/com.termux/files/home")
        return if (f.exists() && f.canRead()) f else null
    }

    fun createDirectory(parent: File, name: String): Result<File> = runCatching {
        val dir = File(parent, name)
        require(dir.mkdirs()) { "Failed to create directory: ${dir.absolutePath}" }
        dir
    }

    fun rename(file: File, newName: String): Result<File> = runCatching {
        val dest = File(file.parent, newName)
        require(file.renameTo(dest)) { "Failed to rename ${file.name} to $newName" }
        dest
    }

    fun delete(file: File): Result<Unit> = runCatching {
        require(file.deleteRecursively()) { "Failed to delete ${file.absolutePath}" }
    }
}
