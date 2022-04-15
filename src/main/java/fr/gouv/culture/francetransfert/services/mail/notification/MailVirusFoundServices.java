package fr.gouv.culture.francetransfert.services.mail.notification;

import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import fr.gouv.culture.francetransfert.core.enums.EnclosureKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.RedisKeysEnum;
import fr.gouv.culture.francetransfert.core.services.RedisManager;
import fr.gouv.culture.francetransfert.model.Enclosure;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MailVirusFoundServices {

	private static final Logger LOGGER = LoggerFactory.getLogger(MailVirusFoundServices.class);

	@Autowired
	MailNotificationServices mailNotificationServices;

	@Autowired
	RedisManager redisManager;
	
	// Send mail to sender
	public void sendToSender(Enclosure enclosure, String templateName, String subject, Locale currentLanguage) {
		LOGGER.info("send email notification virus to sender: {}", enclosure.getSender());
		

		/*added by abir */
		Map<String, String> enclosureMapp = redisManager.hmgetAllString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosure.getGuid()));					
		Locale  language = LocaleUtils.toLocale(enclosureMapp.get(EnclosureKeysEnum.LANGUAGE.getKey())) ;
		//--------//
		
		mailNotificationServices.prepareAndSend(enclosure.getSender(), subject, enclosure, templateName, language);
	}
}
