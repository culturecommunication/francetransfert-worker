package fr.gouv.culture.francetransfert.services.mail.notification;

import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RecipientKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.RedisUtils;
import fr.gouv.culture.francetransfert.model.Enclosure;
import fr.gouv.culture.francetransfert.model.Recipient;
import fr.gouv.culture.francetransfert.services.mail.notification.enums.NotificationTemplateEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MailEnclosureNoLongerAvailbleServices {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailEnclosureNoLongerAvailbleServices.class);

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
                    mailNotificationServices.prepareAndSend(recipient.getMail(), messages.get("subject.no.availble.enclosure.recipient"), enclosure, NotificationTemplateEnum.MAIL_ENCLOSURE_NO_AVAILBLE_RECIPIENTS.getValue());
                    LOGGER.info("================================>send email notification enclosure not availble to recipient: {}", recipient.getMail());
                }
            }
            // Send email to the sender of enclosure is no longer available for download to recipients who have not removed it in time
            if (!CollectionUtils.isEmpty(recipientsDoNotDownloadedEnclosure)) {
                enclosure.setRecipients(recipientsDoNotDownloadedEnclosure);
                mailNotificationServices.prepareAndSend(enclosure.getSender(), messages.get("subject.no.availble.enclosure.sender"), enclosure, NotificationTemplateEnum.MAIL_ENCLOSURE_NO_AVAILBLE_SENDER.getValue());
                LOGGER.info("================================>send email notification enclosure not availble to sender: {}", enclosure.getSender());
            }
        }
    }
}
