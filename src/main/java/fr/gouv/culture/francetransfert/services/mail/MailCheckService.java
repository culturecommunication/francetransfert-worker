package fr.gouv.culture.francetransfert.services.mail;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.mail.Flags;
import javax.mail.Flags.Flag;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.search.FlagTerm;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import fr.gouv.culture.francetransfert.core.enums.AppSyncKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.CheckMailKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.RedisKeysEnum;
import fr.gouv.culture.francetransfert.core.services.RedisManager;
import fr.gouv.culture.francetransfert.core.utils.RedisUtils;
import fr.gouv.culture.francetransfert.security.WorkerException;
import fr.gouv.culture.francetransfert.services.mail.notification.MailNotificationServices;

@Service
public class MailCheckService {

	@Autowired
	private MailNotificationServices mailNotificationServices;
	@Autowired
	private RedisManager redisManager;

	@Value("${healthcheck.smtp.host:imap.gmail.com}")
	private String smtpTargetHost;

	@Value("${healthcheck.smtp.port:993}")
	private String smtpTargetPort;

	@Value("${healthcheck.smtp.type:imaps}")
	private String mailStoreType;

	@Value("${healthcheck.smtp.user}")
	private String smtpTargetUsername;

	@Value("${healthcheck.smtp.password}")
	private String smtpTargetPassword;

	@Value("${environnement}")
	private String environnement;

	public void mailCheck() {

		Map<String, String> hashRedis = redisManager.hmgetAllString(RedisKeysEnum.CHECK_MAIL.getFirstKeyPart());
		String uuid = hashRedis.getOrDefault(CheckMailKeysEnum.UUID.getKey(), "");

		try {
			if (StringUtils.isNoneBlank(uuid)) {

				Long sendAt = Long.parseLong(hashRedis.getOrDefault(CheckMailKeysEnum.SEND_AT.getKey(), null));

				Map<String, String> mailPending = new HashMap<String, String>();
				mailPending.put(CheckMailKeysEnum.UUID.getKey(), uuid);
				mailPending.put(CheckMailKeysEnum.PENDING.getKey(),
						Long.valueOf(new Date().getTime() - sendAt).toString());
				redisManager.insertHASH(RedisKeysEnum.CHECK_MAIL.getFirstKeyPart(), mailPending);

				// create properties
				String subject = "CheckMail - " + environnement + " - " + uuid;
				Properties properties = new Properties();

				properties.put("mail.imap.host", smtpTargetHost);
				properties.put("mail.imap.port", smtpTargetPort);
				properties.put("mail.imap.starttls.enable", "true");
				properties.put("mail.imap.ssl.trust", smtpTargetHost);

				Session emailSession = Session.getDefaultInstance(properties);

				// create the imap store object and connect to the imap server
				Store store = emailSession.getStore(mailStoreType);

				store.connect(smtpTargetHost, smtpTargetUsername, smtpTargetPassword);

				// create the inbox object and open it
				Folder inbox = store.getFolder("Inbox");
				inbox.open(Folder.READ_WRITE);

				// retrieve the messages from the folder in an array and print it
				Message[] messages = inbox.search(new FlagTerm(new Flags(Flag.SEEN), false));

				for (int i = 0, n = messages.length; i < n; i++) {
					Message message = messages[i];
					if (message.getSubject().equals(subject)) {
						Long delay = Long.valueOf(message.getReceivedDate().getTime() - sendAt);
						message.setFlag(Flag.SEEN, true);
						message.setFlag(Flag.DELETED, true);
						try {
							redisManager.deleteKey(AppSyncKeysEnum.APP_SYNC_CHECK_MAIL_SEND.getKey());
							redisManager.deleteKey(AppSyncKeysEnum.APP_SYNC_CHECK_MAIL_CHECK.getKey());
							Map<String, String> mailInfo = new HashMap<String, String>();
							mailInfo.put(CheckMailKeysEnum.UUID.getKey(), "");
							mailInfo.put(CheckMailKeysEnum.DELAY.getKey(), delay.toString());
							mailInfo.put(CheckMailKeysEnum.PENDING.getKey(), "0");
							redisManager.insertHASH(RedisKeysEnum.CHECK_MAIL.getFirstKeyPart(), mailInfo);
							uuid = null;
							delay = null;
						} catch (Exception e) {
							redisManager.deleteKey(AppSyncKeysEnum.APP_SYNC_CHECK_MAIL_CHECK.getKey());
							throw new WorkerException(e.getMessage());
						}
					}

				}

				inbox.close(false);
				store.close();
			}

		} catch (Exception e) {
			redisManager.deleteKey(AppSyncKeysEnum.APP_SYNC_CHECK_MAIL_CHECK.getKey());
			e.printStackTrace();
		}
		redisManager.deleteKey(AppSyncKeysEnum.APP_SYNC_CHECK_MAIL_CHECK.getKey());

	}

	public void sendMail() {

		Date sendAt = new Date();
		String uuid = RedisUtils.generateGUID();
		String subject = "CheckMail - " + environnement + " - " + uuid;
		mailNotificationServices.send(smtpTargetUsername, subject, uuid);
		try {
			Map<String, String> mailInfo = new HashMap<String, String>();
			mailInfo.put(CheckMailKeysEnum.UUID.getKey(), uuid);
			// mailInfo.put(CheckMailKeysEnum.DELAY.getKey(), null);
			mailInfo.put(CheckMailKeysEnum.SEND_AT.getKey(), Long.valueOf(sendAt.getTime()).toString());
			redisManager.insertHASH(RedisKeysEnum.CHECK_MAIL.getFirstKeyPart(), mailInfo);
			redisManager.deleteKey(AppSyncKeysEnum.APP_SYNC_CHECK_MAIL_CHECK.getKey());
		} catch (Exception e) {
			redisManager.deleteKey(AppSyncKeysEnum.APP_SYNC_CHECK_MAIL_SEND.getKey());
		}
		redisManager.deleteKey(AppSyncKeysEnum.APP_SYNC_CHECK_MAIL_CHECK.getKey());

	}

}
