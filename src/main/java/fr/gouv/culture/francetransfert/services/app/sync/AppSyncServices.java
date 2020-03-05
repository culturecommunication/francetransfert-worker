package fr.gouv.culture.francetransfert.services.app.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.AppSyncKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RedisKeysEnum;
import fr.gouv.culture.francetransfert.security.WorkerException;

@Service
public class AppSyncServices {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppSyncServices.class);
    
    @Autowired
    RedisManager redisManager;

	public void appSyncCleanup() {
		try {
//			RedisManager redisManager = RedisManager.getInstance();
			redisManager.deleteKey(AppSyncKeysEnum.APP_SYNC_CLEANUP.getKey());
		} catch (Exception e) {
			throw new WorkerException(e.getMessage());
		}
	}
	
	public void appSyncRelaunch() {
		try {
//			RedisManager redisManager = RedisManager.getInstance();
			redisManager.deleteKey(AppSyncKeysEnum.APP_SYNC_RELAUNCH.getKey());
		} catch (Exception e) {
			throw new WorkerException(e.getMessage());
		}
	}

	public boolean shouldRelaunch() {
//		RedisManager redisManager;
		boolean shouldRelaunch = false;
		try {
//			redisManager = RedisManager.getInstance();
			Long incrementedAppSyncCounter = redisManager.incr(AppSyncKeysEnum.APP_SYNC_RELAUNCH.getKey());
			if(incrementedAppSyncCounter == 1) {
				shouldRelaunch = true;
			}
		} catch (Exception e) {
			throw new WorkerException(e.getMessage());
		}
		return shouldRelaunch;
	}

	public boolean shouldCleanup() {
//		RedisManager redisManager;
		boolean shouldCleanup = false;
		try {
//			redisManager = RedisManager.getInstance();
			Long incrementedAppSyncCounter = redisManager.incr(AppSyncKeysEnum.APP_SYNC_CLEANUP.getKey());
			if(incrementedAppSyncCounter == 1) {
				shouldCleanup = true;
			}
		} catch (Exception e) {
			throw new WorkerException(e.getMessage());
		}
		return shouldCleanup;
	}

}
