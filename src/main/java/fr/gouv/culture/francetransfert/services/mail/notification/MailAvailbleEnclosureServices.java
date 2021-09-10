package fr.gouv.culture.francetransfert.services.mail.notification;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.EnclosureKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.RedisUtils;
import fr.gouv.culture.francetransfert.model.Enclosure;
import fr.gouv.culture.francetransfert.model.Recipient;
import fr.gouv.culture.francetransfert.services.mail.notification.enums.NotificationTemplateEnum;
import fr.gouv.culture.francetransfert.utils.Base64CryptoService;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MailAvailbleEnclosureServices {

	private static final Logger LOGGER = LoggerFactory.getLogger(MailAvailbleEnclosureServices.class);

	@Autowired
	private MailNotificationServices mailNotificationServices;

	@Autowired
	private RedisManager redisManager;

	@Value("${subject.sender}")
	private String subjectSender;

	@Value("${subject.recipient}")
	private String subjectRecipient;

	@Value("${subject.sender.password}")
	private String subjectSenderPassword;

	@Value("${subject.recipient.password}")
	private String subjectRecipientPassword;

	@Autowired
	Base64CryptoService base64CryptoService;

	// Send Mails to snder and recipients
	public void sendMailsAvailableEnclosure(Enclosure enclosure) throws Exception {
		LOGGER.info("================================>send email notification availble to sender: {}",
				enclosure.getSender());
		String passwordRedis = RedisUtils.getEnclosureValue(redisManager, enclosure.getGuid(),
				EnclosureKeysEnum.PASSWORD.getKey());
		boolean publicLink = mailNotificationServices.getPublicLink(enclosure.getGuid());
		String passwordUnHashed = base64CryptoService.aesDecrypt(passwordRedis);
		enclosure.setPassword(passwordUnHashed);
		enclosure.setPublicLink(publicLink);
		enclosure.setUrlAdmin(mailNotificationServices.generateUrlAdmin(enclosure.getGuid()));
		enclosure.setUrlDownload(mailNotificationServices.generateUrlPublicForDownload(enclosure.getGuid()));
		mailNotificationServices.prepareAndSend(enclosure.getSender(), subjectSender, enclosure,
				NotificationTemplateEnum.MAIL_AVAILABLE_SENDER.getValue());
		mailNotificationServices.prepareAndSend(enclosure.getSender(), subjectSenderPassword, enclosure,
				NotificationTemplateEnum.MAIL_PASSWORD_SENDER.getValue());
		if (!publicLink)
			sendToRecipients(enclosure, subjectRecipient, NotificationTemplateEnum.MAIL_AVAILABLE_RECIPIENT.getValue());
	}

	// Send mails to recipients
	public void sendToRecipients(Enclosure enclosure, String subject, String templateName) throws Exception {
		subject = enclosure.getSender() + " " + subject;
		String subjectPassword = subjectRecipientPassword + " " + enclosure.getSender();
		List<Recipient> recipients = enclosure.getRecipients();
		if (!CollectionUtils.isEmpty(recipients)) {
			for (Recipient recipient : recipients) {
				LOGGER.info("================================>send email notification availble to recipient: {}",
						recipient.getMail());
				enclosure.setUrlDownload(mailNotificationServices.generateUrlForDownload(enclosure.getGuid(),
						recipient.getMail(), recipient.getId()));
				mailNotificationServices.prepareAndSend(recipient.getMail(), subject, enclosure, templateName);
				mailNotificationServices.prepareAndSend(recipient.getMail(), subjectPassword, enclosure,
						NotificationTemplateEnum.MAIL_PASSWORD_RECIPIENT.getValue());
			}

		}
	}

}
