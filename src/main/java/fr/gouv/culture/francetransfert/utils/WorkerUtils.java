package fr.gouv.culture.francetransfert.utils;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;

import fr.gouv.culture.francetransfert.security.WorkerException;

public class WorkerUtils {
	
	private WorkerUtils() {
		// private Constructor
	}

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

    public static String base64Encoder(String string) throws UnsupportedEncodingException {
        return Base64.getEncoder().encodeToString(string.getBytes("utf-8"));
    }

    public static String extractEnclosureIdFromDownloadQueueValue(String downloadQueueValue) {
        return extractPartOfString(0, downloadQueueValue);
    }

    public static String extractRecipientIdFromDownloadQueueValue(String downloadQueueValue) {
        return extractPartOfString(1, downloadQueueValue);
    }

    public static String extractPartOfString(int part, String string) {
        Pattern pattern = Pattern.compile(":");
        String[] items = pattern.split(string, 2);
        if (2 == items.length) {
            return items[part];
        } else {
            throw new WorkerException("error of extraction value");
        }
    }

    //convert bit to octets", "Ko", "Mo", "Go", "To
    public static String getFormattedFileSize(long size) {
        String[] suffixes = new String[] { "octets", "Ko", "Mo", "Go", "To" };

        double tmpSize = size;
        int i = 0;

        while (tmpSize >= 1024) {
            tmpSize /= 1024.0;
            i++;
        }

        // arrondi à 10^-2
        tmpSize *= 100;
        tmpSize = (int) (tmpSize + 0.5);
        tmpSize /= 100;

        return tmpSize + " " + suffixes[i];
    }

}
