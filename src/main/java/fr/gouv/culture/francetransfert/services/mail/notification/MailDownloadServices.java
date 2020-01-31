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

import java.util.Optional;

@Service
@Slf4j
public class MailDownloadServices {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailDownloadServices.class);

    @Autowired
    private MailNotificationServices mailNotificationServices;

    @Autowired
    private Messages messages;

    public void sendDownloadEnclosure(Enclosure enclosure, String recipientId) throws Exception {
        Optional<Recipient> optionalRecipient = enclosure.getRecipients().stream().filter(
                entryRecipient -> entryRecipient.getId().equals(recipientId)
        ).findFirst();
        if (optionalRecipient.isPresent()) {
            Recipient entry = optionalRecipient.get();
            enclosure.setRecipientDownloadInProgress(entry.getMail());
            LOGGER.info("================================> send email notification download in progress to sender:  {}", enclosure.getSender());
            mailNotificationServices.prepareAndSend(enclosure.getSender(), messages.get("subject.download.progress"), enclosure, NotificationTemplate.MAIL_AVAILABLE_SENDER.getValue());
        }
    }
}
