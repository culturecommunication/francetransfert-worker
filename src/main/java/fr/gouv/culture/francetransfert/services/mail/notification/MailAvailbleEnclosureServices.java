package fr.gouv.culture.francetransfert.services.mail.notification;

import java.util.ArrayList;
import java.util.List;

import fr.gouv.culture.francetransfert.core.enums.RecipientKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.RedisQueueEnum;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import fr.gouv.culture.francetransfert.core.enums.EnclosureKeysEnum;
import fr.gouv.culture.francetransfert.core.exception.MetaloadException;
import fr.gouv.culture.francetransfert.core.exception.StatException;
import fr.gouv.culture.francetransfert.core.services.RedisManager;
import fr.gouv.culture.francetransfert.core.utils.Base64CryptoService;
import fr.gouv.culture.francetransfert.core.utils.RedisUtils;
import fr.gouv.culture.francetransfert.model.Enclosure;
import fr.gouv.culture.francetransfert.model.Recipient;
import fr.gouv.culture.francetransfert.services.mail.notification.enums.NotificationTemplateEnum;
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

	@Value("${subject.sender.link}")
	private String subjectSenderLink;

	@Value("${subject.recipient}")
	private String subjectRecipient;

	@Value("${subject.sender.password}")
	private String subjectSenderPassword;

	@Value("${subject.recipient.password}")
	private String subjectRecipientPassword;

	@Autowired
	Base64CryptoService base64CryptoService;

	// Send Mails to snder and recipients
	public void sendMailsAvailableEnclosure(Enclosure enclosure, String email) throws MetaloadException, StatException {
		LOGGER.info("send email notification availble to sender: {}", enclosure.getSender());
		String passwordRedis = RedisUtils.getEnclosureValue(redisManager, enclosure.getGuid(),
				EnclosureKeysEnum.PASSWORD.getKey());
		boolean publicLink = mailNotificationServices.getPublicLink(enclosure.getGuid());
		String passwordUnHashed = base64CryptoService.aesDecrypt(passwordRedis);
		enclosure.setPassword(passwordUnHashed);
		enclosure.setPublicLink(publicLink);
		enclosure.setUrlAdmin(mailNotificationServices.generateUrlAdmin(enclosure.getGuid()));
		String subjectSend = new String(subjectSender);
		String subjectSenderPassw = new String(subjectSenderPassword);
		if (publicLink) {
			enclosure.setUrlDownload(mailNotificationServices.generateUrlPublicForDownload(enclosure.getGuid()));
			subjectSend = subjectSenderLink;
		}
		if (StringUtils.isNotBlank(enclosure.getSubject())) {
			subjectSend = subjectSend.concat(" : ").concat(enclosure.getSubject());
			subjectSenderPassw = subjectSenderPassw.concat(" : ").concat(enclosure.getSubject());
		}

		if(!StringUtils.isNotBlank(email)){
		mailNotificationServices.prepareAndSend(enclosure.getSender(), subjectSend, enclosure,
				NotificationTemplateEnum.MAIL_AVAILABLE_SENDER.getValue());
		mailNotificationServices.prepareAndSend(enclosure.getSender(), subjectSenderPassw, enclosure,
				NotificationTemplateEnum.MAIL_PASSWORD_SENDER.getValue());
		}
		if (!publicLink)
			sendToRecipients(enclosure, new String(subjectRecipient),
					NotificationTemplateEnum.MAIL_AVAILABLE_RECIPIENT.getValue(), email);
	}

	// Send mails to recipients
	public void sendToRecipients(Enclosure enclosure, String subject, String templateName, String email) {
		subject = subject + " " + enclosure.getSender();
		String subjectPassword = new String(subjectRecipientPassword);

		if (StringUtils.isNotBlank(enclosure.getSubject())) {
			subject = subject.concat(" : ").concat(enclosure.getSubject());
			subjectPassword  = subjectPassword.concat(" : ").concat(enclosure.getSubject());

		}
		List<Recipient> recipients = enclosure.getRecipients();
		if (!CollectionUtils.isEmpty(recipients)) {

			if(StringUtils.isNotBlank(email)) {
				Recipient newRec = new Recipient();
				newRec.setMail(email);
				List<String> returnesBLOP = redisManager.subscribeFT(RedisQueueEnum.NEW_ID_RECIPIENT.getValue());
				if(!CollectionUtils.isEmpty(returnesBLOP)){
					newRec.setId(returnesBLOP.get(1));
				}
				List<Recipient> newRecipient = new ArrayList<>();
				newRecipient.add(newRec);
				recipients = newRecipient;
			}
			for (Recipient recipient : recipients ) {
				if(!recipient.isSuppressionLogique()){
				LOGGER.info("send email notification availble to recipient: {}", recipient.getMail());
				enclosure.setUrlDownload(mailNotificationServices.generateUrlForDownload(enclosure.getGuid(),
						recipient.getMail(), recipient.getId()));
				mailNotificationServices.prepareAndSend(recipient.getMail(), subject, enclosure, templateName);
				mailNotificationServices.prepareAndSend(recipient.getMail(), subjectPassword, enclosure,
						NotificationTemplateEnum.MAIL_PASSWORD_RECIPIENT.getValue());
				}
			}

		}
	}

}
