package fr.gouv.culture.francetransfert.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fr.gouv.culture.francetransfert.services.mail.notification.MailConfirmationCodeServices;

@Component
public class SendEmailConfirmationCodeTask implements Runnable {

	private static final Logger LOGGER = LoggerFactory.getLogger(SendEmailConfirmationCodeTask.class);

	MailConfirmationCodeServices mailConfirmationCodeServices;

	private String mailCode;
	private String ttl;

	public SendEmailConfirmationCodeTask(String mailCode, String ttl,MailConfirmationCodeServices mailConfirmationCodeServices) {
		this.mailCode = mailCode;
		this.mailConfirmationCodeServices = mailConfirmationCodeServices;
		this.ttl = ttl;
	}

	public SendEmailConfirmationCodeTask() {

	}

	@Override
	public void run() {
		try {
			LOGGER.info("[Worker] Start send confirmation code : " + mailCode);
			mailConfirmationCodeServices.sendConfirmationCode(mailCode, ttl);
		} catch (Exception e) {
			LOGGER.error("[Worker] Send mail confirmation code error : " + e.getMessage(), e);
		}
	}
}
