package Utilities;

import java.util.Calendar;
import java.util.TimeZone;

public class TimeUtil {
    public static TimeZone KST = TimeZone.getTimeZone("Asia/Seoul");

    public static Calendar getKstCalendar() {
        return Calendar.getInstance(KST);
    }
}
