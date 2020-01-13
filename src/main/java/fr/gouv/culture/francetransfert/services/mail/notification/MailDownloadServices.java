package fr.gouv.culture.francetransfert.services.mail.notification;

import fr.gouv.culture.francetransfert.model.Enclosure;
import fr.gouv.culture.francetransfert.model.Recipient;
import fr.gouv.culture.francetransfert.services.mail.notification.enums.NotificationTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class MailDownloadServices {

    @Value("${subject.download.progress}")
    private String subjectDownload;

    @Autowired
    MailNotificationServices mailNotificationServices;

    public void sendDownloadEnclosure(Enclosure enclosure, String recipientId) throws Exception {
        Optional<Recipient> optionalRecipient = enclosure.getRecipients().stream().filter(
                entryRecipient -> entryRecipient.getId().equals(recipientId)
        ).findFirst();
        if (optionalRecipient.isPresent()) {
            Recipient entry = optionalRecipient.get();
            enclosure.setRecipientDownloadInProgress(entry.getMail());
            mailNotificationServices.prepareAndSend(enclosure.getSender(), subjectDownload, enclosure, NotificationTemplate.MAIL_AVAILABLE_SENDER.getValue());
        }
    }
}
