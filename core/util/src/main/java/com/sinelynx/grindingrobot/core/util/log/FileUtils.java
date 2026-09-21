package com.sinelynx.grindingrobot.core.util.log;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;

import java.io.File;

public class FileUtils {

    public static File getSaveFile(Context context) {
        File file = new File(context.getFilesDir(), "pic.jpg");
        return file;
    }

    /**
     * 删除文件
     */
    public static void deleteSingleFile(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    /**
     * 删除一个目录中的所有文件
     *
     * @param file 文件夹
     */
    public static void deleteFileList(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (File f : files) {
                deleteFileList(f);
            }
//            file.delete();//如要保留文件夹，只删除文件，请注释这行
        } else if (file.exists()) {
            file.delete();
        }
    }

    public static boolean checkFileCreate(String fileName)
    {
        File file = new File(fileName);
        if(!file.exists())
        {
            file.mkdirs();
            return false;
        }
        return true;
    }

    /**
     * 判断SD卡是否可用
     *
     * @return true : 可用<br>false : 不可用
     */
    private static boolean isSDCardEnable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /**
     * 获取手机外部总空间大小
     *
     * @return 总大小，字节为单位
     */
    public static long getTotalExternalMemorySize() {
        if (isSDCardEnable()) {
            //获取SDCard根目录
            File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSizeLong();
            long totalBlocks = stat.getBlockCountLong();
            return totalBlocks * blockSize / 1024L / 1024L;
        } else {
            return -1;
        }
    }

    /**
     * 获取SD卡剩余空间
     *
     * @return SD卡剩余空间
     */
    public static long getFreeSpace() {
        if (!isSDCardEnable())
        {
            return -1;
        }
        StatFs stat = new StatFs(getSDCardPath());
        long blockSize, availableBlocks;
        availableBlocks = stat.getAvailableBlocksLong();
        blockSize = stat.getBlockSizeLong();
        return availableBlocks * blockSize / 1024L / 1024L;
    }

    private static String getSDCardPath(){
        File sdDir = null;
        //判断sd卡是否存在
        boolean sdCardExist = Environment.getExternalStorageState()
                .equals(Environment.MEDIA_MOUNTED);
        if(sdCardExist)
        {
            //获取跟目录
            sdDir = Environment.getExternalStorageDirectory();
        }
        return sdDir == null ? "" : sdDir.toString();
    }

}
