/*
  * Copyright (c) Ministère de la Culture (2022) 
  * 
  * SPDX-License-Identifier: Apache-2.0 
  * License-Filename: LICENSE.txt 
  */

package fr.gouv.culture.francetransfert.services.app.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import fr.gouv.culture.francetransfert.core.enums.AppSyncKeysEnum;
import fr.gouv.culture.francetransfert.core.services.RedisManager;
import fr.gouv.culture.francetransfert.security.WorkerException;

@Service
public class AppSyncServices {

	private static final Logger LOGGER = LoggerFactory.getLogger(AppSyncServices.class);

	@Autowired
	RedisManager redisManager;

	public void appSyncCleanup() {
		try {
			redisManager.deleteKey(AppSyncKeysEnum.APP_SYNC_CLEANUP.getKey());
		} catch (Exception e) {
			throw new WorkerException(e.getMessage());
		}
	}

	public void appSyncRelaunch() {
		try {
			redisManager.deleteKey(AppSyncKeysEnum.APP_SYNC_RELAUNCH.getKey());
		} catch (Exception e) {
			throw new WorkerException(e.getMessage());
		}
	}

	public void appSyncIgnimissionDomain() {
		try {
			redisManager.deleteKey(AppSyncKeysEnum.APP_SYNC_IGNIMISSION_DOMAIN.getKey());
		} catch (Exception e) {
			throw new WorkerException(e.getMessage());
		}
	}

	public void appSyncCheckMailCheck() {
		try {
			redisManager.deleteKey(AppSyncKeysEnum.APP_SYNC_CHECK_MAIL_CHECK.getKey());
		} catch (Exception e) {
			throw new WorkerException(e.getMessage());
		}
	}

	public void appSyncCheckMailSend() {
		try {
			redisManager.deleteKey(AppSyncKeysEnum.APP_SYNC_CHECK_MAIL_SEND.getKey());
		} catch (Exception e) {
			throw new WorkerException(e.getMessage());
		}
	}

	public boolean shouldRelaunch() {
		boolean shouldRelaunch = false;
		try {
			Long incrementedAppSyncCounter = redisManager.incr(AppSyncKeysEnum.APP_SYNC_RELAUNCH.getKey());
			if (incrementedAppSyncCounter == 1) {
				shouldRelaunch = true;
			}
		} catch (Exception e) {
			throw new WorkerException(e.getMessage());
		}
		return shouldRelaunch;
	}

	public boolean shouldCleanup() {
		boolean shouldCleanup = false;
		try {
			Long incrementedAppSyncCounter = redisManager.incr(AppSyncKeysEnum.APP_SYNC_CLEANUP.getKey());
			if (incrementedAppSyncCounter == 1) {
				shouldCleanup = true;
			}
		} catch (Exception e) {
			throw new WorkerException(e.getMessage());
		}
		return shouldCleanup;
	}

	public boolean shouldUpdateIgnimissionDomain() {
		boolean shouldUpdateDomain = false;
		try {
			Long incrementedAppSyncCounter = redisManager.incr(AppSyncKeysEnum.APP_SYNC_IGNIMISSION_DOMAIN.getKey());
			LOGGER.info(" worker : start Application ignimission incrementedAppSyncCounter {} ",
					incrementedAppSyncCounter);
			if (incrementedAppSyncCounter == 1) {
				shouldUpdateDomain = true;
			}
		} catch (Exception e) {
			throw new WorkerException(e.getMessage());
		}
		return shouldUpdateDomain;
	}

	public boolean shouldCheckMailCheck() {
		boolean shouldCheckMail = false;
		try {
			Long incrementedAppSyncCounter = redisManager.incr(AppSyncKeysEnum.APP_SYNC_CHECK_MAIL_CHECK.getKey());
			LOGGER.info(" worker : start CheckMailCheck incrementedAppSyncCounter {} ", incrementedAppSyncCounter);
			if (incrementedAppSyncCounter == 1) {
				shouldCheckMail = true;
			}
		} catch (Exception e) {
			throw new WorkerException(e.getMessage());
		}
		return shouldCheckMail;
	}

	public boolean shouldSendCheckMail() {
		boolean shouldCheckMail = false;
		try {
			Long incrementedAppSyncCounter = redisManager.incr(AppSyncKeysEnum.APP_SYNC_CHECK_MAIL_SEND.getKey());
			LOGGER.info(" worker : start CheckMailSend incrementedAppSyncCounter {} ", incrementedAppSyncCounter);
			if (incrementedAppSyncCounter == 1) {
				shouldCheckMail = true;
			}
		} catch (Exception e) {
			throw new WorkerException(e.getMessage());
		}
		return shouldCheckMail;
	}

}
