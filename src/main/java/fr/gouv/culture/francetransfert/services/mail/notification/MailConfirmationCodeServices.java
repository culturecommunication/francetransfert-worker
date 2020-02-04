package fr.gouv.culture.francetransfert.services.mail.notification;

import fr.gouv.culture.francetransfert.model.ConfirmationCode;
import fr.gouv.culture.francetransfert.security.WorkerException;
import fr.gouv.culture.francetransfert.services.mail.notification.enums.NotificationTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class MailConfirmationCodeServices {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailConfirmationCodeServices.class);

    @Autowired
    private MailNotificationServices mailNotificationServices;

    @Autowired
    private Messages messages;

    public void sendConfirmationCode(String mailCode) throws Exception {
        String senderMail = extractSenderMail(mailCode);
        String code = extractConfirmationCode(mailCode);
        ConfirmationCode confirmationCode = ConfirmationCode.builder().code(code).mail(senderMail).build();
        LOGGER.info("================================> send email confirmation code to sender:  {}", senderMail);
        mailNotificationServices.prepareAndSend(senderMail, messages.get("subject.confirmation.code"), confirmationCode, NotificationTemplate.MAIL_CONFIRMATION_CODE.getValue());
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
            LOGGER.error("=======================> error extract mail and code");
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
