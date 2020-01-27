package fr.gouv.culture.francetransfert.services.mail.notification;

import fr.gouv.culture.francetransfert.model.ConfirmationCode;
import fr.gouv.culture.francetransfert.model.Recipient;
import fr.gouv.culture.francetransfert.security.WorkerException;
import fr.gouv.culture.francetransfert.services.mail.notification.enums.NotificationTemplate;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
public class MailConfirmationCodeServices {

    private static final String subjct = "Code de confirmation";

    @Autowired
    MailNotificationServices mailNotificationServices;

    public void sendConfirmationCode(String mailCode) throws Exception {
        String senderMail = extractSenderMail(mailCode);
        String code = extractConfirmationCode(mailCode);
        ConfirmationCode confirmationCode = ConfirmationCode.builder().code(code).mail(senderMail).build();
        mailNotificationServices.prepareAndSend(senderMail, subjct, confirmationCode, NotificationTemplate.MAIL_CONFIRMATION_CODE.getValue());
    }

    /**
     *
     * @param mailCode
     * @param part : part = 1 -> sender email , part = 2 -> confirmation code
     * @return
     */
    private String extractSenderMailAndConfirmationCode(String mailCode, int part) {
        String result = "";
        Pattern pattern = Pattern.compile(":");
        String[] items = pattern.split(mailCode, 2);
        if (2 == items.length) {
            result =items[part];

        } else {
            log.error("=======================> error extract mail and code");
            throw new WorkerException("error extract mail and code");
        }
        return result;
    }

    private String extractConfirmationCode(String mailCode) {
        return extractSenderMailAndConfirmationCode(mailCode, 1);
    }

    private String extractSenderMail(String mailCode) {
        return extractSenderMailAndConfirmationCode(mailCode, 0);
    }
}
