package fr.gouv.culture.francetransfert.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.model.Enclosure;
import fr.gouv.culture.francetransfert.services.mail.notification.MailDownloadServices;

@Component
public class SendEmailDownloadInProgressTask implements Runnable { 

	private static final Logger LOGGER = LoggerFactory.getLogger(SendEmailDownloadInProgressTask.class);
	
    private MailDownloadServices mailDownloadServices;

    private RedisManager redisManager;
	
	private String enclosureId;
	private String recipientId;

	public SendEmailDownloadInProgressTask(String enclosureId, String recipientId, RedisManager redisManager, MailDownloadServices mailDownloadServices) {
		this.enclosureId = enclosureId;
		this.recipientId = recipientId;
		this.redisManager = redisManager;
		this.mailDownloadServices = mailDownloadServices;
	}
	
	public SendEmailDownloadInProgressTask() {
		
	}
	
	@Override
	public void run() {
    	try {
    		mailDownloadServices.sendDownloadEnclosure(Enclosure.build(enclosureId, redisManager), recipientId);
		} catch (Exception e) {
			LOGGER.error(e.getMessage());
		}
    }
}
