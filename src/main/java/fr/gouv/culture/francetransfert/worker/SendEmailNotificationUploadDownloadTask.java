package fr.gouv.culture.francetransfert.worker;

import fr.gouv.culture.francetransfert.core.model.NewRecipient;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fr.gouv.culture.francetransfert.core.enums.RedisQueueEnum;
import fr.gouv.culture.francetransfert.core.enums.TypeStat;
import fr.gouv.culture.francetransfert.core.services.RedisManager;
import fr.gouv.culture.francetransfert.model.Enclosure;
import fr.gouv.culture.francetransfert.services.mail.notification.MailAvailbleEnclosureServices;

@Component
public class SendEmailNotificationUploadDownloadTask implements Runnable {

	private static final Logger LOGGER = LoggerFactory.getLogger(SendEmailNotificationUploadDownloadTask.class);

	private MailAvailbleEnclosureServices mailAvailbleEnclosureServices;

	private RedisManager redisManager;

	private String enclosureId;

	private String newRecipient;

	private NewRecipient newRec;

	public SendEmailNotificationUploadDownloadTask(String enclosureId, RedisManager redisManager,
			MailAvailbleEnclosureServices mailAvailbleEnclosureServices) {
		this.enclosureId = enclosureId;
		this.mailAvailbleEnclosureServices = mailAvailbleEnclosureServices;
		this.redisManager = redisManager;
	}
	public SendEmailNotificationUploadDownloadTask(String enclosureId, NewRecipient newRec, RedisManager redisManager,
												   MailAvailbleEnclosureServices mailAvailbleEnclosureServices) {
		this.enclosureId = enclosureId;
		this.mailAvailbleEnclosureServices = mailAvailbleEnclosureServices;
		this.redisManager = redisManager;
		this.newRec = newRec;
	}

	public SendEmailNotificationUploadDownloadTask() {

	}

	@Override
	public void run() {
		try {
			LOGGER.info(" [Worker] Start send email notification availble enclosure to download for enclosure N° {}",
					enclosureId);
			mailAvailbleEnclosureServices.sendMailsAvailableEnclosure(Enclosure.build(enclosureId, redisManager), newRec,Locale.FRENCH);
			String statMessage = TypeStat.UPLOAD + ";" + enclosureId;
			redisManager.publishFT(RedisQueueEnum.STAT_QUEUE.getValue(), statMessage);
		} catch (Exception e) {
			LOGGER.error("[Worker] email notification error : " + e.getMessage(), e);
		}
	}
}
