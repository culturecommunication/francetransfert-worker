
package fr.gouv.culture.francetransfert.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.services.cleanup.CleanUpServices;

@Component
public class TempDataCleanupTask implements Runnable {

	private static final Logger LOGGER = LoggerFactory.getLogger(TempDataCleanupTask.class);
	
	
	private CleanUpServices cleanUpServices; 
	
    private RedisManager redisManager;
	
	private String enclosureId;

	public TempDataCleanupTask(String enclosureId, RedisManager redisManager, CleanUpServices cleanUpServices) {
		this.enclosureId = enclosureId;
		this.redisManager = redisManager;
		this.cleanUpServices = cleanUpServices;
	}
	
	public TempDataCleanupTask() {
		
	}
	
	@Override
	public void run() {
    	try {
    		LOGGER.info("ThreadName: " + Thread.currentThread().getName() + " | ThreadId: " + Thread.currentThread().getId());
    		LOGGER.info(" start temp data cleanup process for enclosure N: {}" , enclosureId);
            cleanUpServices.cleanUpEnclosureTempDataInRedis(redisManager, enclosureId);
		} catch (Exception e) {
			LOGGER.error("[Worker] temp data cleanup error : "  + e.getMessage(), e);
		}
    	LOGGER.info("ThreadName: " + Thread.currentThread().getName() + " | ThreadId: " + Thread.currentThread().getId() + " IS DEAD");
    }
}
