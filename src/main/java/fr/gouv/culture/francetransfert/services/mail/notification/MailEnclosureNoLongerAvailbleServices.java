/*
  * Copyright (c) Ministère de la Culture (2022) 
  * 
  * SPDX-License-Identifier: Apache-2.0 
  * License-Filename: LICENSE.txt 
  */

package fr.gouv.culture.francetransfert.services.mail.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import fr.gouv.culture.francetransfert.core.enums.EnclosureKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.RecipientKeysEnum;
import fr.gouv.culture.francetransfert.core.exception.MetaloadException;
import fr.gouv.culture.francetransfert.core.services.RedisManager;
import fr.gouv.culture.francetransfert.core.utils.RedisUtils;
import fr.gouv.culture.francetransfert.model.Enclosure;
import fr.gouv.culture.francetransfert.model.Recipient;
import fr.gouv.culture.francetransfert.services.mail.notification.enums.NotificationTemplateEnum;

@Service
public class MailEnclosureNoLongerAvailbleServices {

	private static final Logger LOGGER = LoggerFactory.getLogger(MailEnclosureNoLongerAvailbleServices.class);

	@Value("${subject.no.availble.enclosure.recipient}")
	private String subjectNoAvailbleEnclosureRecipient;

	@Value("${subject.no.availble.enclosure.sender}")
	private String subjectNoAvailbleEnclosureSender;

	@Autowired
	private MailNotificationServices mailNotificationServices;

	@Autowired
	RedisManager redisManager;

	public void sendEnclosureNotAvailble(Enclosure enclosure) throws MetaloadException {

		List<Recipient> recipients = enclosure.getRecipients();
		String sendNoAvailbleEnclosureRecipient = new String(subjectNoAvailbleEnclosureRecipient);
		String sendNoAvailbleEnclosureSender = new String(subjectNoAvailbleEnclosureSender);

		Locale language = LocaleUtils.toLocale(
				RedisUtils.getEnclosureValue(redisManager, enclosure.getGuid(), EnclosureKeysEnum.LANGUAGE.getKey()));

		if (!CollectionUtils.isEmpty(recipients)) {
			if (StringUtils.isNotBlank(enclosure.getSubject())) {
				sendNoAvailbleEnclosureRecipient = sendNoAvailbleEnclosureRecipient.concat(" : ")
						.concat(enclosure.getSubject());
				sendNoAvailbleEnclosureSender = sendNoAvailbleEnclosureSender.concat(" : ")
						.concat(enclosure.getSubject());
			}
			List<Recipient> recipientsDoNotDownloadedEnclosure = new ArrayList<>();
			for (Recipient recipient : recipients) {
				Map<String, String> recipientMap = RedisUtils.getRecipientEnclosure(redisManager, recipient.getId());
				boolean isFileDownloaded = (!CollectionUtils.isEmpty(recipientMap)
						&& 0 == Integer.parseInt(recipientMap.get(RecipientKeysEnum.NB_DL.getKey())));
				if (isFileDownloaded) {
					recipientsDoNotDownloadedEnclosure.add(recipient);
					mailNotificationServices.prepareAndSend(recipient.getMail(), sendNoAvailbleEnclosureRecipient,
							enclosure, NotificationTemplateEnum.MAIL_ENCLOSURE_NO_AVAILBLE_RECIPIENTS.getValue(),
							language);
					LOGGER.info("send email notification enclosure not availble to recipient: {}", recipient.getMail());
				}
			}
			// Send email to the sender of enclosure is no longer available for download to
			// recipients who have not removed it in time
			if (!CollectionUtils.isEmpty(recipientsDoNotDownloadedEnclosure)) {
				enclosure.setNotDownloadRecipients(recipientsDoNotDownloadedEnclosure);
				mailNotificationServices.prepareAndSend(enclosure.getSender(), sendNoAvailbleEnclosureSender, enclosure,
						NotificationTemplateEnum.MAIL_ENCLOSURE_NO_AVAILBLE_SENDER.getValue(), language);
				LOGGER.info("send email notification enclosure not availble to sender: {}", enclosure.getSender());
			}
		}
	}
}
