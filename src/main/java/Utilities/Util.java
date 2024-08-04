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

    public static String timeFormat(long milli, String format) {
        SimpleDateFormat sdf = new SimpleDateFormat(format, java.util.Locale.KOREA);
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        return sdf.format(milli);
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

    public static String dirtyRelativeDay(long milli) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        cal.setTimeInMillis(milli);
        int year = cal.get(Calendar.YEAR);
        int day = cal.get(Calendar.DAY_OF_YEAR);
        int curYear = Calendar.getInstance().get(Calendar.YEAR);
        int curDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);

        if (year == curYear) {
            if (day == curDay) {
                return "오늘";
            } else if (day == curDay + 1) {
                return "새벽";
            }
        }
        return null;
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
