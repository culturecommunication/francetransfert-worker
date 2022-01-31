package fr.gouv.culture.francetransfert.services.mail.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import fr.gouv.culture.francetransfert.model.Enclosure;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MailVirusFoundServices {

	private static final Logger LOGGER = LoggerFactory.getLogger(MailVirusFoundServices.class);

	@Autowired
	MailNotificationServices mailNotificationServices;

	// Send mail to sender
	public void sendToSender(Enclosure enclosure, String templateName, String subject) {
		LOGGER.info("send email notification virus to sender: {}", enclosure.getSender());
		mailNotificationServices.prepareAndSend(enclosure.getSender(), subject, enclosure, templateName);
	}
}
