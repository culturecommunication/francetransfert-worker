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
import fr.gouv.culture.francetransfert.core.enums.RedisKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.StatutEnum;
import fr.gouv.culture.francetransfert.core.exception.MetaloadException;
import fr.gouv.culture.francetransfert.core.exception.StatException;
import fr.gouv.culture.francetransfert.core.model.NewRecipient;
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

	@Value("${subject.senderEn}")
	private String subjectSenderEn;

	@Value("${subject.sender.linkEn}")
	private String subjectSenderLinkEn;

	@Value("${subject.recipientEn}")
	private String subjectRecipientEn;

	@Value("${subject.sender.passwordEn}")
	private String subjectSenderPasswordEn;

	@Value("${subject.recipient.passwordEn}")
	private String subjectRecipientPasswordEn;
	@Autowired
	Base64CryptoService base64CryptoService;

	// Send Mails to snder and recipients
	public void sendMailsAvailableEnclosure(Enclosure enclosure, NewRecipient metaDataRecipient, Locale currentLanguage)
			throws MetaloadException, StatException {

		redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosure.getGuid()),
				EnclosureKeysEnum.STATUS_CODE.getKey(), StatutEnum.EDC.getCode(), -1);
		redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosure.getGuid()),
				EnclosureKeysEnum.STATUS_WORD.getKey(), StatutEnum.EDC.getWord(), -1);

		LOGGER.info("send email notification availble to sender: {} for enclosure {}", enclosure.getSender(),
				enclosure.getGuid());
		String passwordRedis = RedisUtils.getEnclosureValue(redisManager, enclosure.getGuid(),
				EnclosureKeysEnum.PASSWORD.getKey());
		boolean publicLink = mailNotificationServices.getPublicLink(enclosure.getGuid());
		String passwordUnHashed = base64CryptoService.aesDecrypt(passwordRedis);
		enclosure.setPassword(passwordUnHashed);
		passwordUnHashed = "";
		passwordRedis = "";
		enclosure.setPublicLink(publicLink);
		enclosure.setUrlAdmin(mailNotificationServices.generateUrlAdmin(enclosure.getGuid()));

		Locale language = LocaleUtils.toLocale(
				RedisUtils.getEnclosureValue(redisManager, enclosure.getGuid(), EnclosureKeysEnum.LANGUAGE.getKey()));

		String subjectSend = new String();
		String subjectSenderPassw = new String();
		String subjectRecipientLang = new String();
		String subjectSenderLinkLang = new String();

		if (language.equals(Locale.UK)) {
			subjectSend = new String(subjectSenderEn);
			subjectSenderPassw = new String(subjectSenderPasswordEn);
			subjectRecipientLang = new String(subjectRecipientEn);
			subjectSenderLinkLang = new String(subjectSenderLinkEn);
		} else {
			subjectSend = new String(subjectSender);
			subjectSenderPassw = new String(subjectSenderPassword);
			subjectRecipientLang = new String(subjectRecipient);
			subjectSenderLinkLang = new String(subjectSenderLink);
		}

		if (publicLink) {
			enclosure.setUrlDownload(mailNotificationServices.generateUrlPublicForDownload(enclosure.getGuid()));
			subjectSend = subjectSenderLinkLang;
		}
		if (StringUtils.isNotBlank(enclosure.getSubject())) {
			subjectSend = subjectSend.concat(" : ").concat(enclosure.getSubject());
			subjectSenderPassw = subjectSenderPassw.concat(" : ").concat(enclosure.getSubject());
		}
		if (metaDataRecipient == null) {
			mailNotificationServices.prepareAndSend(enclosure.getSender(), subjectSend, enclosure,
					NotificationTemplateEnum.MAIL_AVAILABLE_SENDER.getValue(), language);
			mailNotificationServices.prepareAndSend(enclosure.getSender(), subjectSenderPassw, enclosure,
					NotificationTemplateEnum.MAIL_PASSWORD_SENDER.getValue(), language);
		}
		if (!publicLink)
			sendToRecipients(enclosure, new String(subjectRecipientLang),
					NotificationTemplateEnum.MAIL_AVAILABLE_RECIPIENT.getValue(), metaDataRecipient, currentLanguage);

		// ---
		redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosure.getGuid()),
				EnclosureKeysEnum.STATUS_CODE.getKey(), StatutEnum.PAT.getCode(), -1);
		redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosure.getGuid()),
				EnclosureKeysEnum.STATUS_WORD.getKey(), StatutEnum.PAT.getWord(), -1);
	}

	// Send mails to recipients
	public void sendToRecipients(Enclosure enclosure, String subject, String templateName,
			NewRecipient metaDataRecipient, Locale currentLanguage) throws MetaloadException {

		// ---
		Map<String, String> enclosureMap = redisManager
				.hmgetAllString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosure.getGuid()));

		Locale language = LocaleUtils.toLocale(
				RedisUtils.getEnclosureValue(redisManager, enclosure.getGuid(), EnclosureKeysEnum.LANGUAGE.getKey()));

		subject = subject + " " + enclosure.getSender();
		String subjectPassword = new String(subjectRecipientPassword);

		if (language.equals(Locale.UK)) {
			subjectPassword = new String(subjectRecipientPasswordEn);
		}

		if (StringUtils.isNotBlank(enclosure.getSubject())) {
			subject = subject.concat(" : ").concat(enclosure.getSubject());
			subjectPassword = subjectPassword.concat(" : ").concat(enclosure.getSubject());

		}
		List<Recipient> recipients = enclosure.getRecipients();
		if (!CollectionUtils.isEmpty(recipients)) {
			if (metaDataRecipient != null) {
				if (StringUtils.isNotBlank(metaDataRecipient.getMail())) {
					Recipient newRec = new Recipient();
					newRec.setMail(metaDataRecipient.getMail());
					newRec.setId(metaDataRecipient.getId());
					List<Recipient> newRecipientList = new ArrayList<>();
					newRecipientList.add(newRec);
					recipients = newRecipientList;
				}
			}
			for (Recipient recipient : recipients) {
				if (!recipient.isSuppressionLogique()) {
					try {
						LOGGER.info("send email notification availble to recipient: {} for enclosure {}",
								recipient.getMail(), enclosure.getGuid());

						enclosure.setUrlDownload(mailNotificationServices.generateUrlForDownload(enclosure.getGuid(),
								recipient.getMail(), recipient.getId()));
						mailNotificationServices.prepareAndSend(recipient.getMail(), subject, enclosure, templateName,
								language);
						mailNotificationServices.prepareAndSend(recipient.getMail(), subjectPassword, enclosure,
								NotificationTemplateEnum.MAIL_PASSWORD_RECIPIENT.getValue(), language);

					} catch (Exception e) {
						redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosure.getGuid()),
								EnclosureKeysEnum.STATUS_CODE.getKey(), StatutEnum.EEC.getCode(), -1);
						redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosure.getGuid()),
								EnclosureKeysEnum.STATUS_WORD.getKey(), StatutEnum.EEC.getWord(), -1);
						LOGGER.error("Cannot send mail recipient mail {} for enclosure {}", recipient.getMail(),
								enclosure.getGuid());
					}
				}
			}

		}
	}

}
