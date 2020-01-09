package fr.gouv.culture.francetransfert.utils;

import com.amazonaws.services.s3.model.PartETag;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.utils.RedisUtils;
import fr.gouv.culture.francetransfert.security.WorkerException;
import org.redisson.client.RedisTryAgainException;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

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

}
