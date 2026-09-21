package com.sinelynx.grindingrobot.core.util.file

import java.io.File
import java.security.MessageDigest

/**
 * 文件哈希工具，用于计算文件 MD5 等（OTA 包校验）
 */
object FileHashUtils {

    /**
     * 计算文件的 MD5 值（小写十六进制字符串）
     *
     * @param file 目标文件
     * @return MD5 字符串，失败返回 null
     */
    fun computeFileMD5(file: File): String? {
        if (!file.exists() || !file.isFile()) return null
        return try {
            val md = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } > 0) {
                    md.update(buffer, 0, read)
                }
            }
            md.digest().joinToString("") { String.format("%02x", it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
