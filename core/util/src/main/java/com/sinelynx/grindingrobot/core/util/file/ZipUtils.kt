package com.sinelynx.grindingrobot.core.util.file

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Zip 压缩工具类（将文件/文件夹压缩为 .zip）
 */
object ZipUtils {

    private const val BUFFER_SIZE = 8 * 1024

    /**
     * 将单个文件或文件夹压缩为 zip。
     *
     * @param source 需要压缩的文件/文件夹
     * @param destZip 输出 zip 文件
     * @param includeRootDir source 为文件夹时，是否在 zip 内包含根目录名
     * @param overwrite destZip 已存在时是否覆盖
     */
    fun zip(
        source: File,
        destZip: File,
        includeRootDir: Boolean = true,
        overwrite: Boolean = true
    ): Boolean {
        if (!source.exists()) return false
        return zipFiles(
            sources = listOf(source),
            destZip = destZip,
            includeRootDir = includeRootDir,
            overwrite = overwrite
        )
    }

    /**
     * 将多个文件/文件夹压缩为一个 zip。
     *
     * @param sources 需要压缩的文件/文件夹列表
     * @param destZip 输出 zip 文件
     * @param includeRootDir 当某个 source 为文件夹时，是否在 zip 内包含根目录名
     * @param overwrite destZip 已存在时是否覆盖
     */
    fun zipFiles(
        sources: List<File>,
        destZip: File,
        includeRootDir: Boolean = true,
        overwrite: Boolean = true
    ): Boolean = zipFilesWithProgress(
        sources = sources,
        destZip = destZip,
        includeRootDir = includeRootDir,
        overwrite = overwrite,
        onProgress = null
    )

    /**
     * 将多个文件/文件夹压缩为一个 zip（带进度回调）。
     *
     * @param sources 需要压缩的文件/文件夹列表
     * @param destZip 输出 zip 文件
     * @param includeRootDir 当某个 source 为文件夹时，是否在 zip 内包含根目录名
     * @param overwrite destZip 已存在时是否覆盖
     * @param onProgress 进度回调：bytesWritten / totalBytes
     */
    fun zipFilesWithProgress(
        sources: List<File>,
        destZip: File,
        includeRootDir: Boolean = true,
        overwrite: Boolean = true,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null
    ): Boolean {
        if (sources.isEmpty()) return false
        if (sources.any { !it.exists() }) return false

        try {
            if (destZip.exists()) {
                if (!overwrite) return false
                if (!destZip.delete()) return false
            }
            destZip.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) return false
            }

            val outputCanonical = runCatching { destZip.canonicalFile }.getOrNull()
            val totalBytes = computeTotalBytes(sources, outputCanonical)
            var bytesWritten = 0L
            onProgress?.invoke(0L, totalBytes)

            ZipOutputStream(BufferedOutputStream(FileOutputStream(destZip))).use { zos ->
                val buffer = ByteArray(BUFFER_SIZE)
                val onBytesWritten: (Long) -> Unit = { delta ->
                    bytesWritten += delta
                    onProgress?.invoke(bytesWritten.coerceAtMost(totalBytes), totalBytes)
                }

                for (source in sources) {
                    if (outputCanonical != null) {
                        val sourceCanonical = runCatching { source.canonicalFile }.getOrNull()
                        if (sourceCanonical != null && sourceCanonical == outputCanonical) {
                            continue
                        }
                    }
                    if (source.isDirectory) {
                        val baseName = if (includeRootDir) source.name else ""
                        addDirectoryToZip(
                            zos = zos,
                            dir = source,
                            entryPrefix = baseName,
                            outputCanonical = outputCanonical,
                            buffer = buffer,
                            onBytesWritten = onBytesWritten
                        )
                    } else {
                        addFileToZip(
                            zos = zos,
                            file = source,
                            entryName = source.name,
                            outputCanonical = outputCanonical,
                            buffer = buffer,
                            onBytesWritten = onBytesWritten
                        )
                    }
                }
                zos.flush()
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun addDirectoryToZip(
        zos: ZipOutputStream,
        dir: File,
        entryPrefix: String,
        outputCanonical: File?,
        buffer: ByteArray,
        onBytesWritten: (delta: Long) -> Unit
    ) {
        val children = dir.listFiles()
        if (children == null || children.isEmpty()) {
            if (entryPrefix.isBlank()) {
                // 不包含根目录名且根目录为空：生成空 zip
                return
            }
            // 空目录条目
            val dirEntryName = normalizeEntryName(entryPrefix).ensureTrailingSlash()
            putDirectoryEntry(zos, dirEntryName, dir.lastModified())
            return
        }

        for (child in children) {
            if (outputCanonical != null) {
                val childCanonical = runCatching { child.canonicalFile }.getOrNull()
                if (childCanonical != null && childCanonical == outputCanonical) continue
            }
            val childEntryName = when {
                entryPrefix.isBlank() -> child.name
                else -> "$entryPrefix/${child.name}"
            }
            if (child.isDirectory) {
                addDirectoryToZip(
                    zos = zos,
                    dir = child,
                    entryPrefix = childEntryName,
                    outputCanonical = outputCanonical,
                    buffer = buffer,
                    onBytesWritten = onBytesWritten
                )
            } else {
                addFileToZip(
                    zos = zos,
                    file = child,
                    entryName = childEntryName,
                    outputCanonical = outputCanonical,
                    buffer = buffer,
                    onBytesWritten = onBytesWritten
                )
            }
        }
    }

    private fun addFileToZip(
        zos: ZipOutputStream,
        file: File,
        entryName: String,
        outputCanonical: File?,
        buffer: ByteArray,
        onBytesWritten: (delta: Long) -> Unit
    ) {
        if (!file.exists() || !file.isFile) return
        if (outputCanonical != null) {
            val fileCanonical = runCatching { file.canonicalFile }.getOrNull()
            if (fileCanonical != null && fileCanonical == outputCanonical) return
        }

        val normalized = normalizeEntryName(entryName)
        val entry = ZipEntry(normalized).apply { time = file.lastModified() }
        zos.putNextEntry(entry)
        BufferedInputStream(FileInputStream(file)).use { input ->
            var len: Int
            while (input.read(buffer).also { len = it } > 0) {
                zos.write(buffer, 0, len)
                onBytesWritten(len.toLong())
            }
        }
        zos.closeEntry()
    }

    private fun putDirectoryEntry(zos: ZipOutputStream, entryName: String, lastModified: Long) {
        val entry = ZipEntry(entryName).apply { time = lastModified }
        zos.putNextEntry(entry)
        zos.closeEntry()
    }

    private fun normalizeEntryName(name: String): String =
        name.replace('\\', '/').trimStart('/')

    private fun String.ensureTrailingSlash(): String =
        if (endsWith("/")) this else "$this/"

    private fun computeTotalBytes(sources: List<File>, outputCanonical: File?): Long {
        var total = 0L
        for (source in sources) {
            if (outputCanonical != null) {
                val sourceCanonical = runCatching { source.canonicalFile }.getOrNull()
                if (sourceCanonical != null && sourceCanonical == outputCanonical) continue
            }
            total += when {
                source.isDirectory -> directorySize(source, outputCanonical)
                source.isFile -> source.length()
                else -> 0L
            }
        }
        return total.coerceAtLeast(0L)
    }

    private fun directorySize(dir: File, outputCanonical: File?): Long {
        var total = 0L
        val children = dir.listFiles() ?: return 0L
        for (child in children) {
            if (outputCanonical != null) {
                val childCanonical = runCatching { child.canonicalFile }.getOrNull()
                if (childCanonical != null && childCanonical == outputCanonical) continue
            }
            total += when {
                child.isDirectory -> directorySize(child, outputCanonical)
                child.isFile -> child.length()
                else -> 0L
            }
        }
        return total
    }
}

