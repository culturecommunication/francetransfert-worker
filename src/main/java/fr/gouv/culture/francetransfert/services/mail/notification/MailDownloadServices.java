package fr.gouv.culture.francetransfert.services.mail.notification;

import fr.gouv.culture.francetransfert.model.Enclosure;
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
        Optional<Map.Entry<String, String>> optionEntry = enclosure.getRecipients().entrySet().stream().filter(
                entryRecipient -> entryRecipient.getValue().equals(recipientId)
        ).findFirst();
        if (optionEntry.isPresent()) {
            Map.Entry<String, String> entry = optionEntry.get();
            enclosure.setRecipientDownloadInProgress(entry.getKey());
            mailNotificationServices.prepareAndSend(enclosure.getSender(), subjectDownload, enclosure, NotificationTemplate.MAIL_AVAILABLE_SENDER.getValue());
        }
    }
}
