package fr.gouv.culture.francetransfert.services.mail.notification;

import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RecipientKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.RedisUtils;
import fr.gouv.culture.francetransfert.model.Enclosure;
import fr.gouv.culture.francetransfert.model.Recipient;
import fr.gouv.culture.francetransfert.services.mail.notification.enums.NotificationTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class MailEnclosureNoLongerAvailbleServices {

    @Autowired
    private MailNotificationServices mailNotificationServices;

    @Autowired
    private  Messages messages;

    public void sendEnclosureNotAvailble(Enclosure enclosure) throws Exception {

        RedisManager redisManager = RedisManager.getInstance();
        List<Recipient> recipients = enclosure.getRecipients();
        if (!CollectionUtils.isEmpty(recipients)) {
            List<Recipient> recipientsDoNotDownloadedEnclosure = new ArrayList<>();
            for (Recipient recipient: recipients) {
                Map<String, String> recipientMap = RedisUtils.getRecipientEnclosure(redisManager, recipient.getId());
                boolean isFileDownloaded = (!CollectionUtils.isEmpty(recipientMap) && 0 == Integer.parseInt(recipientMap.get(RecipientKeysEnum.NB_DL.getKey())));
                if (isFileDownloaded) {
                    recipientsDoNotDownloadedEnclosure.add(recipient);
                    mailNotificationServices.prepareAndSend(recipient.getMail(), messages.get("subject.no.availble.enclosure.recipient"), enclosure, NotificationTemplate.MAIL_ENCLOSURE_NO_AVAILBLE_RECIPIENTS.getValue());
                }
            }
            // Send email to the sender of enclosure is no longer available for download to recipients who have not removed it in time
            if (!CollectionUtils.isEmpty(recipientsDoNotDownloadedEnclosure)) {
                enclosure.setRecipients(recipientsDoNotDownloadedEnclosure);
                mailNotificationServices.prepareAndSend(enclosure.getSender(), messages.get("subject.no.availble.enclosure.sender"), enclosure, NotificationTemplate.MAIL_ENCLOSURE_NO_AVAILBLE_SENDER.getValue());
            }
        }
    }
}
