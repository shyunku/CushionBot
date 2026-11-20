package Utilities;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.ricecode.similarity.JaroWinklerStrategy;
import net.ricecode.similarity.SimilarityStrategy;
import net.ricecode.similarity.StringSimilarityService;
import net.ricecode.similarity.StringSimilarityServiceImpl;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.TimeZone;

public class Util {
    public static int parseInt(String str) throws NumberFormatException {
        return Integer.parseInt(str.trim());
    }

    public static long parseLong(String str) throws NumberFormatException {
        return Long.parseLong(str.trim());
    }

    public static double parseDouble(String str) throws NumberFormatException {
        return Double.parseDouble(str.trim());
    }

    public static double getStringDistance(String s1, String s2) {
        SimilarityStrategy strategy = new JaroWinklerStrategy();
        StringSimilarityService service = new StringSimilarityServiceImpl(strategy);
        return service.score(s1, s2);
    }

    public static String concatStrArrWithoutBlank(Collection<String> strs) {
        StringBuilder builder = new StringBuilder();
        for (String str : strs) {
            builder.append(str);
        }

        return builder.toString();
    }

    public static String generateRandomUUID(int length) {
        StringBuilder sb = new StringBuilder();
        String tokens = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        for (int i = 0; i < length; i++) {
            int index = (int) (Math.random() * tokens.length());
            sb.append(tokens.charAt(index));
        }
        return sb.toString();
    }

    public static String generateRandomNumber(int length) {
        StringBuilder sb = new StringBuilder();
        String tokens = "123456789";
        for (int i = 0; i < length; i++) {
            int index = (int) (Math.random() * tokens.length());
            sb.append(tokens.charAt(index));
        }
        return sb.toString();
    }

    public static String timeFormat(long milli, String format) {
        SimpleDateFormat sdf = new SimpleDateFormat(format, java.util.Locale.KOREA);
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        return sdf.format(milli);
    }

    public static String getRemainTime(long milli) {
        long current = System.currentTimeMillis();
        long diff = milli - current;
        if (diff < 0) return "종료";

        diff = Math.abs(diff);
        long second = (diff / 1000) % 60;
        long minute = (diff / (1000 * 60)) % 60;
        long hour = (diff / (1000 * 60 * 60)) % 24;
        long day = (diff / (1000 * 60 * 60 * 24));

        if (day > 0) return String.format("%d일 %02d시간 %02d분 %02d초", day, hour, minute, second);
        if (hour > 0) return String.format("%02d시간 %02d분 %02d초", hour, minute, second);
        if (minute > 0) return String.format("%02d분 %02d초", minute, second);
        return String.format("%02d초", second);
    }

    public static String getRemainTimeMinute(long milli) {
        long current = System.currentTimeMillis();
        long diff = milli - current;
        if (diff < 0) return "종료";

        diff = Math.abs(diff);
        long minute = (diff / (1000 * 60)) % 60;
        long hour = (diff / (1000 * 60 * 60)) % 24;
        long day = (diff / (1000 * 60 * 60 * 24));

        if (day > 0) return String.format("%d일 %02d시간 %02d분", day, hour, minute);
        if (hour > 0) return String.format("%d시간 %02d분", hour, minute);
        if (minute > 0) return String.format("%d분", minute);
        return "1분 미만";
    }

    public static String getRelativeTime(long milli) {
        long current = System.currentTimeMillis();
        long diff = milli - current;
        boolean isPast = diff < 0;
        diff = Math.abs(diff);
//        long second = (diff / 1000) % 60;
        long minute = (diff / (1000 * 60)) % 60;
        long hour = (diff / (1000 * 60 * 60)) % 24;

        if (hour == 0) return String.format("약 %s%d분", isPast ? "-" : "", minute);
        return String.format("약 %s%d시간 %d분", isPast ? "-" : "", hour, minute);
    }

    public static String getDurationString(long milli) {
        long second = (milli / 1000) % 60;
        long minute = (milli / (1000 * 60)) % 60;
        long hour = (milli / (1000 * 60 * 60)) % 24;
        long day = (milli / (1000 * 60 * 60 * 24));

        if (day > 0) return String.format("%d일 %02d시간 %02d분 %02d초", day, hour, minute, second);
        if (hour > 0) return String.format("%d시간 %02d분 %02d초", hour, minute, second);
        if (minute > 0) return String.format("%d분 %02d초", minute, second);
        return String.format("%d초", second);
    }

    public static String getDurationStringMinute(long milli) {
        long minute = (milli / (1000 * 60)) % 60;
        long hour = (milli / (1000 * 60 * 60)) % 24;
        long day = (milli / (1000 * 60 * 60 * 24));

        if (day > 0) return String.format("%d일 %02d시간 %02d분", day, hour, minute);
        if (hour > 0) return String.format("%d시간 %02d분", hour, minute);
        if (minute > 0) return String.format("%d분", minute);
        return "1분 미만";
    }

    /**
     * "오늘" / "새벽"(the next day) / "M월 d일" for a time, comparing calendar days in KST.
     */
    public static String dirtyRelativeDay(long milli) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"));
        cal.setTimeInMillis(milli);
        Calendar today = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"));
        Calendar tomorrow = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"));
        tomorrow.add(Calendar.DATE, 1);
        if (sameDay(cal, today)) return "오늘";
        if (sameDay(cal, tomorrow)) return "새벽";
        return String.format("%d월 %d일", cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    public static String unescapeHTML(String original) {
        return original.replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&")
                .replaceAll("&quot;", "\"")
                .replaceAll("&apos;", "'");
    }

    public static String ToJson(Object obj, boolean pretty) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        if (pretty) {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } else {
            return mapper.writeValueAsString(obj);
        }
    }
}
