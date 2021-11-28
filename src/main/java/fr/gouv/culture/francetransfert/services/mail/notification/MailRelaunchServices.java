package fr.gouv.culture.francetransfert.services.mail.notification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.EnclosureKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RecipientKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RedisKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.exception.MetaloadException;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.DateUtils;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.RedisUtils;
import fr.gouv.culture.francetransfert.model.Enclosure;
import fr.gouv.culture.francetransfert.model.Recipient;
import fr.gouv.culture.francetransfert.security.WorkerException;
import fr.gouv.culture.francetransfert.services.mail.notification.enums.NotificationTemplateEnum;

@Service
public class MailRelaunchServices {

	private static final Logger LOGGER = LoggerFactory.getLogger(MailRelaunchServices.class);

	@Value("${relaunch.mail.days}")
	private int relaunchDays;

	@Value("${subject.relaunch.recipient}")
	private String subjectRelaunchRecipient;

	@Autowired
	MailNotificationServices mailNotificationServices;

	@Autowired
	RedisManager redisManager;

	public void sendMailsRelaunch() throws WorkerException {
		redisManager.smembersString(RedisKeysEnum.FT_ENCLOSURE_DATES.getKey("")).forEach(date -> {
			redisManager.smembersString(RedisKeysEnum.FT_ENCLOSURE_DATE.getKey(date)).forEach(enclosureId -> {
				try {
					LocalDateTime exipireEnclosureDate = DateUtils.convertStringToLocalDateTime(
							redisManager.getHgetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId),
									EnclosureKeysEnum.EXPIRED_TIMESTAMP.getKey()));
					if (LocalDate.now().equals(exipireEnclosureDate.toLocalDate().minusDays(relaunchDays))) {
						Enclosure enclosure = Enclosure.build(enclosureId, redisManager);
						LOGGER.info(" send relaunch mail for enclosure N° {}", enclosureId);
						sendToRecipientsAndSenderRelaunch(enclosure,
								NotificationTemplateEnum.MAIL_RELAUNCH_RECIPIENT.getValue());
					}
				} catch (Exception e) {
					throw new WorkerException("Enclosure build error");
				}
			});
		});
	}

	// Send mails Relaunch to recipients
	private void sendToRecipientsAndSenderRelaunch(Enclosure enclosure, String templateName)
			throws WorkerException, MetaloadException {
		List<Recipient> recipients = enclosure.getRecipients();
		if (!CollectionUtils.isEmpty(recipients)) {
			for (Recipient recipient : recipients) {
				Map<String, String> recipientMap = RedisUtils.getRecipientEnclosure(redisManager, recipient.getId());
				boolean isFileDownloaded = (!CollectionUtils.isEmpty(recipientMap)
						&& 0 == Integer.parseInt(recipientMap.get(RecipientKeysEnum.NB_DL.getKey())));
				if (isFileDownloaded) {
					enclosure.setUrlDownload(mailNotificationServices.generateUrlForDownload(enclosure.getGuid(),
							recipient.getMail(), recipient.getId()));
					LOGGER.info(" send relaunch mail to {} ", recipient.getMail());
					mailNotificationServices.prepareAndSend(recipient.getMail(),
							subjectRelaunchRecipient + enclosure.getSender(), enclosure, templateName);
				}
			}
		}
	}
}
