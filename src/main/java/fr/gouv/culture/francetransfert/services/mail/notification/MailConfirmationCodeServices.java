package fr.gouv.culture.francetransfert.services.mail.notification;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import fr.gouv.culture.francetransfert.model.ConfirmationCode;
import fr.gouv.culture.francetransfert.security.WorkerException;
import fr.gouv.culture.francetransfert.services.mail.notification.enums.NotificationTemplateEnum;

@Service
public class MailConfirmationCodeServices {

	private static final Logger LOGGER = LoggerFactory.getLogger(MailConfirmationCodeServices.class);

	@Autowired
	private MailNotificationServices mailNotificationServices;

	@Value("${subject.confirmation.code}")
	private String subjectConfirmationCode;

	@Value("${expire.confirmation.code}")
	private int secondsToExpireConfirmationCode;

	@Value("${expire.token.sender}")
	private int expireTokenSender;

	public void sendConfirmationCode(String mailCode) {
		LOGGER.info("STEP SEND MAIL");
		String senderMail = extractSenderMail(mailCode);
		String code = extractConfirmationCode(mailCode);
		String ttlCode = extractHeureExpirationCode(mailCode);
		int codeMin = secondsToExpireConfirmationCode / 60;
		int sessionMin = expireTokenSender / 60;
		ConfirmationCode confirmationCode = ConfirmationCode.builder().code(code).mail(senderMail)
				.dateExpiration(ttlCode).codeTime(codeMin).sessionTime(sessionMin).build();
		LOGGER.info("Send email confirmation code to sender:  {}", senderMail);
		mailNotificationServices.prepareAndSend(senderMail, subjectConfirmationCode, confirmationCode,
				NotificationTemplateEnum.MAIL_CONFIRMATION_CODE.getValue());
	}

	/**
	 *
	 * @param mailCode
	 * @param part     : part = 0 -> sender email , part = 1 -> confirmation code ,
	 *                 part = 2 -> ttl
	 * @return
	 */
	private String extractSenderMailAndConfirmationCode(String mailCode, int part) {
		String result = "";
		Pattern pattern = Pattern.compile(":");
		String[] items = pattern.split(mailCode, 3);
		if (3 == items.length) {
			result = items[part];
		} else {
			LOGGER.error("Error extract mail and code");
			throw new WorkerException("error extract mail and code");
		}
		return result;
	}

	private String extractConfirmationCode(String mailCode) {
		return extractSenderMailAndConfirmationCode(mailCode, 1);
	}

	private String extractSenderMail(String mailCode) {
		return extractSenderMailAndConfirmationCode(mailCode, 0);
	}

	private String extractHeureExpirationCode(String mailCode) {
		String result = "";
		String code = extractSenderMailAndConfirmationCode(mailCode, 2);
		if (StringUtils.isNotBlank(code)) {
			result = code.substring(11, 19);
		}
		return result;
	}
}
