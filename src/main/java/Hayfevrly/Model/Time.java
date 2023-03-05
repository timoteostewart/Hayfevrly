package Hayfevrly.Model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class Time {


    public static boolean areSameDay(LocalDateTime ldt1, LocalDateTime ldt2) {
        return (ldt1.toLocalDate().isEqual(ldt2.toLocalDate()));
    }

    static String getFirstMinuteOfTodaySQL() {
        return LocalDate.now(ZoneId.of("America/Chicago")) + " 00:00:00";
    }

    public static LocalDateTime getNowInCentralTimeLdt() {
        return LocalDateTime.now(ZoneId.of("America/Chicago"));
    }

    public static String ldtToSqlDatetime(LocalDateTime ldt) {
        return ldt.toString().replace('T', ' ').substring(0, 19);
    }

    public static String ldtToDisplay(LocalDateTime ldt) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("EEEE, MMMM d, h:mm a");
        return ldt.format(dtf);
    }

    public static LocalDateTime sqlDatetimeToLdt(String sqlDt) {
        if (sqlDt == null || sqlDt.isBlank() || sqlDt.isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(sqlDt, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public static boolean successfulPause(int seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }


}
