package fr.gouv.culture.francetransfert.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RedisQueueEnum;
import fr.gouv.culture.francetransfert.services.stat.StatServices;

@Component
public class StatTask implements Runnable {

	private static final Logger LOGGER = LoggerFactory.getLogger(StatTask.class);
	
    private StatServices statServices;

    private RedisManager redisManager;
	
	private String enclosureId;

	public StatTask(String enclosureId, RedisManager redisManager, StatServices statServices) {
		this.enclosureId = enclosureId;
		this.redisManager = redisManager;
		this.statServices = statServices;
	}
	
	public StatTask() {
		
	}

	@Override
	public void run() {
		LOGGER.info("================================> worker : start zip  process for enclosur N°  {}", enclosureId);
    	try {
    		System.out.println("ThreadName: " + Thread.currentThread().getName() + " | ThreadId: " + Thread.currentThread().getId());
    		LOGGER.info("================================> start save data in mongoDb", enclosureId);
            statServices.saveData(enclosureId);
            redisManager.publishFT(RedisQueueEnum.TEMP_DATA_CLEANUP_QUEUE.getValue(), enclosureId);
		} catch (Exception e) {
			LOGGER.error(e.getMessage());
		}
    }
}
