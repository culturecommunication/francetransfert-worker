package fr.gouv.culture.francetransfert.services.mail.notification;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import fr.gouv.culture.francetransfert.core.enums.EnclosureKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.RedisKeysEnum;
import fr.gouv.culture.francetransfert.core.services.RedisManager;
import fr.gouv.culture.francetransfert.core.utils.Base64CryptoService;
import fr.gouv.culture.francetransfert.security.WorkerException;
import fr.gouv.culture.francetransfert.services.mail.notification.enums.NotificationTemplateEnum;

@Component
public class MailNotificationServices {

	private static final Logger LOGGER = LoggerFactory.getLogger(MailNotificationServices.class);

//    properties mail France transfert SMTP
	@Value("${spring.mail.ftmail}")
	private String franceTransfertMail;

	@Value("${url.download.api}")
	private String urlDownloadApi;
	@Value("${url.admin.page}")
	private String urlAdminPage;

	@Autowired
	private JavaMailSender emailSender;

	@Autowired
	private MailContentBuilder htmlBuilder;

	@Autowired
	Base64CryptoService base64CryptoService;

	@Autowired
	private RedisManager redisManager;

	public void prepareAndSend(String to, String subject, Object object, String templateName) {
		try {
			LOGGER.debug("start send emails for enclosure ");
			templateName = templateName != null && !templateName.isEmpty() ? templateName
					: NotificationTemplateEnum.MAIL_TEMPLATE.getValue();
			JavaMailSenderImpl sender = new JavaMailSenderImpl();
			MimeMessage message = sender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setFrom(franceTransfertMail);
			helper.setTo(to);
			if( StringUtils.isNotBlank(subject))
			{
				helper.setSubject(subject);
			}
			String htmlContent = htmlBuilder.build(object, templateName);
			helper.setText(htmlContent, true);
			emailSender.send(message);
		} catch (MessagingException | IOException e) {
			throw new WorkerException("Enclosure build error");
		}
	}

	public void prepareAndSend(String to, String subject, String body, String templateName) {
		try {
			LOGGER.debug("start send emails for enclosure ");
			templateName = templateName != null && !templateName.isEmpty() ? templateName
					: NotificationTemplateEnum.MAIL_TEMPLATE.getValue();
			JavaMailSenderImpl sender = new JavaMailSenderImpl();
			MimeMessage message = sender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setFrom(franceTransfertMail);
			helper.setTo(to);
			helper.setSubject(subject);
			String htmlContent = htmlBuilder.build(body, templateName);
			helper.setText(htmlContent, true);
			emailSender.send(message);
		} catch (MessagingException e) {
			throw new WorkerException("Enclosure build error");
		}
	}

	public String generateUrlForDownload(String enclosureId, String recipientMail, String recipientId) {
		try {
			return urlDownloadApi + "?enclosure=" + enclosureId + "&recipient="
					+ base64CryptoService.base64Encoder(recipientMail) + "&token=" + recipientId;
		} catch (UnsupportedEncodingException e) {
			throw new WorkerException("Download url error");
		}
	}

	public String generateUrlPublicForDownload(String enclosureId) {
		return urlDownloadApi + "download-info-public?enclosure=" + enclosureId;
	}

	public boolean getPublicLink(String enclosureId) {
		Map<String, String> enclosureMap = redisManager.hmgetAllString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId));
		return Boolean.parseBoolean(enclosureMap.get(EnclosureKeysEnum.PUBLIC_LINK.getKey()));
	}

	public String generateUrlAdmin(String enclosureId) {
		Map<String, String> tokenMap = redisManager.hmgetAllString(RedisKeysEnum.FT_ADMIN_TOKEN.getKey(enclosureId));
		return urlAdminPage + "?token=" + tokenMap.get(EnclosureKeysEnum.TOKEN.getKey()) + "&enclosure=" + enclosureId;
	}

}
