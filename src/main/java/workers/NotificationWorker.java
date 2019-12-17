package workers;

import java.util.ArrayList;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.opengroup.mc.francetransfert.api.francetransfert_storage_api.StorageManager;

@Component
public class NotificationWorker {
	

	@Scheduled(cron = "0 0 * * * ?")
	public void scheduleTaskWithCronExpression() {
		try {
			StorageManager manager = new StorageManager();
			String bucketForEmailNotificationName = manager.getBucketForEmailNotificationName();
			ArrayList<String> bucketContent = manager.listBucketContent(bucketForEmailNotificationName);
			for (String fileName : bucketContent) {
				checkRedisForSendMail(fileName);
			}
		} catch (Exception e) {
			//TODO LOG ERROR IN ERROR FILE
		}
	}

	private void checkRedisForSendMail(String fileName) {
		// TODO Get data for file from redis, check for each user if it was downloaded, if not send email
		ArrayList<String> emailList = getEmailsToBeNotifiedList(fileName);
		for (String emailAddress : emailList) {
			MailSender mailSender = new MailSender();
			mailSender.sendDeletionNotificationMail(emailAddress);
		}
	}

	private ArrayList<String> getEmailsToBeNotifiedList(String fileName) {
		ArrayList<String> emailList = new ArrayList<String>();
		
		//TODO get that from redis and for each user that has not downloaded 
		// the file yet we add them to the list of emails to notify.
		
		return emailList;
	}
} 