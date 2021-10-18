package fr.gouv.culture.francetransfert.services.mail.notification;

import fr.gouv.culture.francetransfert.model.Enclosure;
import fr.gouv.culture.francetransfert.services.mail.notification.enums.NotificationTemplateEnum;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MailVirusFoundServices {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailVirusFoundServices.class);

    @Autowired
    MailNotificationServices mailNotificationServices;


    // Send mail to sender
    public void sendToSender(Enclosure enclosure, String templateName, String subject){
        LOGGER.info("send email notification virus to sender: {}", enclosure.getSender());
        mailNotificationServices.prepareAndSend(
                enclosure.getSender(),
                subject,
                enclosure,
                templateName
        );
    }
}
