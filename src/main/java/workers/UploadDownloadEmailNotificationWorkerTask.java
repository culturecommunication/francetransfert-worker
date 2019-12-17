package workers;

import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.RedisManager;

public class UploadDownloadEmailNotificationWorkerTask implements Runnable {

	
	String prefix;
	String statsChannel = "stats-worker-queue";
	private MailSender sender;
	
	public UploadDownloadEmailNotificationWorkerTask(String prefix) {
		this.sender = new MailSender();
		this.prefix = prefix;
	}

	@Override
	public void run() {
		String uploadSuccessfulRecipient = getUploadSuccessfulRecipient(prefix);
		String downloadLinkRecipient = getDownloadLinkRecipient(prefix);
		sendUploadSuccessfulEmail(uploadSuccessfulRecipient);
		sendDownloadLinkEmail(downloadLinkRecipient);
		notifyStatsWorker();
	}
	
	private String getDownloadLinkRecipient(String prefix) {
		return null;
	}

	private String getUploadSuccessfulRecipient(String prefix) {
		return null;
	}

	private void sendUploadSuccessfulEmail(String uploadSuccessfulRecipient) {
		sender.sendUploadSuccessNotificationMail(uploadSuccessfulRecipient);
	}

	private void sendDownloadLinkEmail(String downloadLinkRecipient) {
		sender.sendDownloadLinkMail(downloadLinkRecipient);
	}

	private void notifyStatsWorker() {
		RedisManager.getInstance().publish(statsChannel, prefix);
	}
}
