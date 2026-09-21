package com.sinelynx.grindingrobot.core.util.log

import android.annotation.SuppressLint
import android.app.Application
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.regex.Pattern

/**
 * 日志工具类
 * 提供统一的日志记录接口，支持不同级别的日志输出
 *
 * @author Dreamj
 */
object LogUtils {

    /**
     * 是否启用调试模式
     */
    private var isDebugMode = false

    const val TAG: String = "---->"

    val PATH: String = Environment.getExternalStorageDirectory().path + "/Sinelynx/log/"
    val DEBUG_PATH: String = PATH + "debug/"
    val INFO_PATH : String = PATH + "info/";
    val WARN_PATH : String = PATH + "info/";
    val ERROR_PATH : String = PATH + "info/";

    val ALGORITHM_PATH : String = PATH + "algorithm/";
    val CRASH_PATH : String =  PATH + "crash/";

    const val LOG_FILE_SUFFIX: String = ".log"
    const val LOG_DEBUG: Int = 0x11
    const val LOG_INFO: Int = 0x12
    const val LOG_WARN: Int = 0x13
    const val LOG_ERROR: Int = 0x14
    const val LOG_CRASH: Int = 0x15

    const val LOG_ALGORITHM: Int = 0x16

    /**
     * 初始化日志框架，应在 Application 中调用
     * 用法示例：LogUtils.init(application, BuildConfig.DEBUG)
     *
     * @param application Application 对象
     * @param isDebug 是否为调试模式，用于决定是否植入调试树
     * @author Dreamj
     */
    fun init(application: Application, isDebug: Boolean = false) {
        isDebugMode = isDebug
        initFolder()
        checkLogFile()
    }

    fun initFolder() {
        FileUtils.checkFileCreate(DEBUG_PATH)
        FileUtils.checkFileCreate(INFO_PATH)
        FileUtils.checkFileCreate(WARN_PATH)
        FileUtils.checkFileCreate(ERROR_PATH)
        FileUtils.checkFileCreate(CRASH_PATH)
        FileUtils.checkFileCreate(ALGORITHM_PATH)
        d("initFolder end")
    }

    fun checkLogFile() {
        //判断SD卡剩余存储，处理日志相关文件
        val freeSpace = FileUtils.getFreeSpace().toFloat()
        val memorySize = FileUtils.getTotalExternalMemorySize().toFloat()
//        clearLog(10)
        d("Sdcard free size= $freeSpace")
        d("Sdcard total size= $memorySize")
    }

    /**
     * 日志清理
     * @param day 要清理的day天之前的日志
     */
    fun clearLog(day: Int) {
        if (day > 3) {
            CoroutineScope(Dispatchers.IO).launch {
                deleteLogFile(DEBUG_PATH, day)
                deleteLogFile(INFO_PATH, day)
                deleteLogFile(WARN_PATH, day)
                deleteLogFile(ERROR_PATH, day)
                deleteLogFile(CRASH_PATH, day)
            }
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                FileUtils.deleteFileList(File(PATH))
            }
        }
    }

    fun d(msg: String?) {
       logContent(TAG, msg,LOG_DEBUG)
    }

    fun d(tag: String?, msg: String?) {
       logContent(tag, msg,LOG_DEBUG)
    }

    fun a(tag: String?, msg: String?) {
        logContent(tag, msg,LOG_ALGORITHM)
    }

    fun i(msg: String?) {
       logContent(TAG, msg,LOG_INFO)
    }

    fun i(tag: String?, msg: String?) {
       logContent(tag, msg,LOG_INFO)
    }

    fun w(msg: String?) {
       logContent(TAG, msg,LOG_WARN)
    }

    fun w(tag: String?, msg: String?) {
       logContent(tag, msg,LOG_WARN)
    }

    fun e(msg: String?) {
       logContent(TAG, msg,LOG_ERROR)
    }

    fun e(tag: String?, msg: String?) {
       logContent(tag, msg,LOG_ERROR)
    }

    fun logContent(tag: String?, msg: String?, type: Int) {
        //固定日志的打印入口，此处取第四个即可定位到实际调用处
        val trace = Thread.currentThread().getStackTrace()
        val content = mLineTime.get()?.format(Date()) +
                "   F[" +
                getSimpleClassName(trace[4]!!.getClassName()) +
                "]   L[" +
                trace[4]!!.getLineNumber() +
                "][" +
                trace[4]!!.getMethodName() +
                "]      " +
                msg
        val logFile: File
        when (type) {
            LOG_DEBUG -> {
                logFile =
                    File(DEBUG_PATH + "debug_" + DateUtils.getDateFormat(Date()) +LOG_FILE_SUFFIX)
                Log.d(tag, content)
            }

            LOG_INFO -> {
                logFile =
                    File(INFO_PATH + "info_" + DateUtils.getDateFormat(Date()) +LOG_FILE_SUFFIX)
                Log.i(tag, content)
            }

            LOG_WARN -> {
                logFile =
                    File(WARN_PATH + "warn_" + DateUtils.getDateFormat(Date()) +LOG_FILE_SUFFIX)
                Log.w(tag, content)
            }

            LOG_ERROR -> {
                logFile =
                    File(ERROR_PATH + "error_" + DateUtils.getDateFormat(Date()) +LOG_FILE_SUFFIX)
                Log.e(tag, content)
            }

            LOG_CRASH -> {
                logFile =
                    File(CRASH_PATH + "crash" + DateUtils.getDateFormat(Date()) +LOG_FILE_SUFFIX)
                Log.e(tag, content)
            }

            LOG_ALGORITHM -> {
                logFile =
                    File(ALGORITHM_PATH + "algorithm" + DateUtils.getDateFormat(Date()) +LOG_FILE_SUFFIX)
                Log.d(tag, content)
            }
            else -> return
        }
        // 使用Kotlin协程写入日志文件
        try {
            // 这里需要通过Kotlin协程来实现，但由于当前是Java文件，需要调用Kotlin协程包装类
            // 使用Kotlin协程在IO线程中写入日志文件
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (type == LOG_DEBUG) {
                        return@launch
                    }
                    writeLog(content + "\r\n", logFile)
                } catch (e: Exception) {
                    e("Failed to write log file in coroutine: " + e.message)
                }
            }
        } catch (e: Exception) {
            // 当协程执行失败时，直接在当前线程写入日志作为备选方案
            e( "Failed to execute log task in coroutine: " + e.message)
        }
    }

    val mLineTime: ThreadLocal<DateFormat?> = object : ThreadLocal<DateFormat?>() {
        @SuppressLint("SimpleDateFormat")
        override fun initialValue(): DateFormat {
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
        }
    }

    fun getSimpleClassName(name: String): String {
        val lastIndex = name.lastIndexOf(".")
        return name.substring(lastIndex + 1)
    }
    fun writeLog(content: String?, logFile: File) {
        var fileWriter: FileWriter? = null

        try {
            fileWriter = FileWriter(logFile, true)
            fileWriter.append(content)
            fileWriter.flush()
            fileWriter.close()
        } catch (e: IOException) {
            if (fileWriter != null) {
                try {
                    fileWriter.flush()
                    fileWriter.close()
                } catch (e1: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * 删除目录下n天前的log日志
     * @param path 删除的路径
     * @param n    删除n的天数
     */
    fun deleteLogFile(path: String, n: Int) {
        val f = File(path)
        val files = f.listFiles()
        for (file in files!!) {
            val pattern = Pattern
                .compile("[0-9]{4}[-][0-9]{1,2}[-][0-9]{1,2}")
            val matcher = pattern.matcher(file.getPath())
            var dateStr: String? = null
            if (matcher.find()) {
                dateStr = matcher.group(0)
            }
            if (null != dateStr && dateStr != "") {
                if (DateUtils
                        .isNForDay(DateUtils.getDateByDateFormat(dateStr), n)
                ) {
                    FileUtils.deleteSingleFile(file)
                }
            }
        }
    }
}