package workers;

import java.util.ArrayList;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.opengroup.mc.francetransfert.api.francetransfert_storage_api.StorageManager;

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
