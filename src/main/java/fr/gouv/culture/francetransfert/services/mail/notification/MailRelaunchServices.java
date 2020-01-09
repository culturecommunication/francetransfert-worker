package fr.gouv.culture.francetransfert.services.mail.notification;

import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.RedisManager;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.enums.EnclosureKeysEnum;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.enums.RecipientKeysEnum;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.enums.RedisKeysEnum;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.utils.DateUtils;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.utils.RedisUtils;
import fr.gouv.culture.francetransfert.model.Enclosure;
import fr.gouv.culture.francetransfert.security.WorkerException;
import fr.gouv.culture.francetransfert.services.mail.notification.enums.NotificationTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@Slf4j
public class MailRelaunchServices {

    @Value("${subject.relaunch.recipient}")
    private String subjectRelaunchRecipient;

    @Value("${subject.relaunch.sender}")
    private String subjectRelaunchSender;

    @Value("${relaunch.mail.days}")
    private int relaunchDays;

    @Autowired
    MailNotificationServices mailNotificationServices;

    public void sendMailsRelaunch() throws WorkerException {
        RedisManager redisManager = RedisManager.getInstance();
        redisManager.smembersString(RedisKeysEnum.FT_ENCLOSURE_DATES.getKey("")).forEach(date -> {
            redisManager.smembersString(RedisKeysEnum.FT_ENCLOSURE_DATE.getKey(date)).forEach(enclosureId -> {
                try {
                    LocalDateTime exipireEnclosureDate = DateUtils.convertStringToLocalDateTime(redisManager.getHgetString(enclosureId, EnclosureKeysEnum.EXPIRED_TIMESTAMP.getKey()));
                    if (LocalDate.now().equals(exipireEnclosureDate.toLocalDate().minusDays(relaunchDays))) {
                        Enclosure enclosure = Enclosure.build(enclosureId);
                        sendToRecipientsAndSenderRelaunch(redisManager, enclosure, NotificationTemplate.MAIL_RELAUNCH_RECIPIENT.getValue());
                    }
                } catch (Exception e) {
                    throw new WorkerException("Enclosure build error");
                }
            });
        });
    }

    // Send mails Relaunch to recipients
    private void sendToRecipientsAndSenderRelaunch(RedisManager redisManager, Enclosure enclosure, String templateName) throws Exception {
        String subject = enclosure.getSender() + " " + subjectRelaunchRecipient;
        Map<String, String> recipients = enclosure.getRecipients();
        if (!CollectionUtils.isEmpty(recipients)) {
            for (Map.Entry<String, String> recipient: recipients.entrySet()) {
                Map<String, String> recipientMap = RedisUtils.getRecipientEnclosure(redisManager, recipient.getValue());
                boolean isFileDownloaded = (0 == Integer.parseInt(recipientMap.get(RecipientKeysEnum.NB_DL.getKey())));
                if (isFileDownloaded) {
                    enclosure.setUrlDownload(mailNotificationServices.generateUrlForDownload(enclosure.getGuid(), recipient.getKey(), recipient.getValue()));
                    mailNotificationServices.prepareAndSend(recipient.getKey(), subject, enclosure, templateName);
                }
            }
        }
    }
}
