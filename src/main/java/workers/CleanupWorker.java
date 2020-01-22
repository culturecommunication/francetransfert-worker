package workers;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import fr.gouv.culture.francetransfert.francetransfert_storage_api.StorageManager;

import java.util.ArrayList;

@Component
public class CleanupWorker {
	

	@Scheduled(cron = "0 0 * * * ?")
	public void scheduleTaskWithCronExpression() {
		try {
			StorageManager manager = new StorageManager();
			String bucketForDeletionName = manager.getBucketForDeletionName();
			ArrayList<String> bucketContent = manager.listBucketContent(bucketForDeletionName);
			for (String fileName : bucketContent) {
				manager.deleteObject(bucketForDeletionName, fileName);
			}
			manager.deleteBucket(bucketForDeletionName);;
		} catch (Exception e) {
			//TODO LOG ERROR IN ERROR FILE
		}
	}
} 
