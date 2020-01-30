package fr.gouv.culture.francetransfert.services.mail.notification;

import fr.gouv.culture.francetransfert.model.Enclosure;
import fr.gouv.culture.francetransfert.model.Recipient;
import fr.gouv.culture.francetransfert.services.mail.notification.enums.NotificationTemplate;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Service
@Slf4j
public class MailAvailbleEnclosureServices {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailAvailbleEnclosureServices.class);

    @Value("${subject.sender}")
    private String subjectSender;

    @Value("${subject.recipient}")
    private String subjectRecipient;

    @Autowired
    MailNotificationServices mailNotificationServices;

    // Send Mails to snder and recipients
    public void sendMailsAvailableEnclosure(Enclosure enclosure) throws Exception{
        LOGGER.info("================================>send email notification availble to sender: {}", enclosure.getSender());
        mailNotificationServices.prepareAndSend(enclosure.getSender(), subjectSender, enclosure, NotificationTemplate.MAIL_AVAILABLE_SENDER.getValue());
        sendToRecipients(enclosure, subjectRecipient, NotificationTemplate.MAIL_AVAILABLE_RECIPIENT.getValue());
    }

    // Send mails to recipients
    public void sendToRecipients(Enclosure enclosure, String subject, String templateName) throws Exception {
        subject = enclosure.getSender() + " " + subject;
        List<Recipient> recipients = enclosure.getRecipients();
        if (!CollectionUtils.isEmpty(recipients)) {
            for (Recipient recipient: recipients) {
                LOGGER.info("================================>send email notification availble to recipient: {}", recipient.getMail());
                enclosure.setUrlDownload(mailNotificationServices.generateUrlForDownload(enclosure.getGuid(), recipient.getMail(), recipient.getId()));
                mailNotificationServices.prepareAndSend(recipient.getMail(), subject, enclosure, templateName);
            }

        }
    }
}
