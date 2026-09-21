package com.sinelynx.grindingrobot.core.util.log;


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtils {

    /**
     * yyyy-MM-dd字符串
     */
    private static final String DEFAULT_FORMAT_DATE = "yyyy-MM-dd";
    /**
     * yyyy-MM-dd HH:mm:ss字符串
     */
    private static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * yyyy-MM-dd格式
     */
    private static final ThreadLocal<SimpleDateFormat> defaultDateFormat = new ThreadLocal<SimpleDateFormat>() {

        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat(DEFAULT_FORMAT_DATE);
        }

    };

    public static Date parseDate(String dateStr, String format) {
        if (dateStr == null || format == null) {
            return null;
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            return sdf.parse(dateStr);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * yyyy-MM-dd HH:mm:ss格式
     */
    private static final ThreadLocal<SimpleDateFormat> defaultDateTimeFormat = new ThreadLocal<SimpleDateFormat>() {

        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat(DEFAULT_DATE_TIME_FORMAT);
        }

    };

    /**
     * 将date转成yyyy-MM-dd字符串<br>
     *
     * @param date Date对象
     * @return yyyy-MM-dd
     */
    public static String getDateFormat(Date date) {
        return (date == null ? "" : defaultDateFormat.get().format(date));
    }

    /**
     * 将"yyyy-MM-dd" 格式的字符串转成Date
     *
     * @param strDate
     * @return Date
     */
    public static Date getDateByDateFormat(String strDate) {
        try {
            return defaultDateFormat.get().parse(strDate);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 当前日期是否是n天之前的
     *
     * @param date1 和当天比较的日期
     * @param n     第几天之前
     * @return
     */
    public static boolean isNForDay(Date date1, int n) {
        if(date1 == null) {
            return false;
        }
        int days = (int) ((System.currentTimeMillis() - date1.getTime()) / (1000 * 3600 * 24));
        if (days > n) {
            return true;
        }
        return false;
    }

}
