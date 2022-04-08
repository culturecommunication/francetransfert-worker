package fr.gouv.culture.francetransfert.services.mail.notification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import fr.gouv.culture.francetransfert.core.enums.RedisQueueEnum;
import fr.gouv.culture.francetransfert.core.services.RedisManager;
import fr.gouv.culture.francetransfert.model.Enclosure;
import fr.gouv.culture.francetransfert.services.mail.notification.enums.NotificationTemplateEnum;
import fr.gouv.culture.francetransfert.utils.WorkerUtils;

@Service
public class MailDownloadServices {

	private static final Logger LOGGER = LoggerFactory.getLogger(MailDownloadServices.class);

	@Value("${subject.download.progress}")
	private String subjectDownloadProgress;

	@Autowired
	RedisManager redisManager;

	@Autowired
	private MailNotificationServices mailNotificationServices;

	public void sendDownloadEnclosure(Enclosure enclosure, List<String> recipientId) {
		ArrayList<String> recipList = new ArrayList<String>();
		String sendObject = new String(subjectDownloadProgress);
		if (StringUtils.isNotBlank(enclosure.getSubject())) {
			sendObject = sendObject.concat(" : ").concat(enclosure.getSubject());
		}
		recipList.addAll(enclosure.getRecipients().stream().filter(c -> recipientId.contains(c.getId()))
				.map(x -> x.getMail()).collect(Collectors.toList()));
		enclosure.setRecipientDownloadInProgress(recipList);

		LOGGER.info("Send email notification download in progress to sender:  {}", enclosure.getSender());
		mailNotificationServices.prepareAndSend(enclosure.getSender(), sendObject, enclosure,
				NotificationTemplateEnum.MAIL_DOWNLOAD_SENDER_TEMPLATE.getValue(),Locale.FRENCH);
	}

	public void sendMailsDownload() {
		LOGGER.info("STEP SEND MAIL DOWNLOAD");
		List<String> downloadList = redisManager.lrange(RedisQueueEnum.DOWNLOAD_QUEUE.getValue(), 0, -1);
		redisManager.deleteKey(RedisQueueEnum.DOWNLOAD_QUEUE.getValue());
		Map<String, Set<String>> encloRecipMap = new HashMap<String, Set<String>>();
		downloadList.stream().distinct().forEach(downloadKey -> {
			try {
				String enclosureId = WorkerUtils.extractEnclosureIdFromDownloadQueueValue(downloadKey);
				String recipientId = WorkerUtils.extractRecipientIdFromDownloadQueueValue(downloadKey);
				Set<String> tmpSet = encloRecipMap.getOrDefault(enclosureId, new HashSet<String>());
				tmpSet.add(recipientId);
				encloRecipMap.put(enclosureId, tmpSet);
			} catch (Exception e) {
				LOGGER.error("Error building download Mail : " + e.getMessage(), e);
			}
		});
		encloRecipMap.entrySet().forEach(x -> {
			try {
				sendDownloadEnclosure(Enclosure.build(x.getKey(), redisManager), new ArrayList<String>(x.getValue()));
			} catch (Exception e) {
				LOGGER.error("Error sending download Mail : " + e.getMessage(), e);
			}
		});
	}
}
