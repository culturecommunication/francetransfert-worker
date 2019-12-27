package fr.gouv.culture.francetransfert.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class WorkerUtils {

    public static String infinity_Date = "2000-12-31";

    public static LocalDateTime convertStringToLocalDateTime(String date) {
        if (null == date) {
            date = infinity_Date;
        }
        //convert String to LocalDateTime
        return LocalDateTime.parse(date);
    }

    public static String convertStringToLocalDateTime(LocalDateTime localDateTime) {
        String result = "";
        if (localDateTime != null) {
            result = localDateTime.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRENCH));
        }
        return result;
    }

    public static String getExipreDate(String dateCreationEnclosure) {
        return  convertStringToLocalDateTime(convertStringToLocalDateTime(dateCreationEnclosure).plusDays(30));
    }
}
