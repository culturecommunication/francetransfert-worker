package fr.gouv.culture.francetransfert.worker;

import com.google.gson.Gson;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RedisQueueEnum;
import fr.gouv.culture.francetransfert.model.Enclosure;
import fr.gouv.culture.francetransfert.model.Rate;
import fr.gouv.culture.francetransfert.services.app.sync.AppSyncServices;
import fr.gouv.culture.francetransfert.services.cleanup.CleanUpServices;
import fr.gouv.culture.francetransfert.services.mail.notification.MailAvailbleEnclosureServices;
import fr.gouv.culture.francetransfert.services.mail.notification.MailConfirmationCodeServices;
import fr.gouv.culture.francetransfert.services.mail.notification.MailDownloadServices;
import fr.gouv.culture.francetransfert.services.mail.notification.MailRelaunchServices;
import fr.gouv.culture.francetransfert.services.satisfaction.SatisfactionService;
import fr.gouv.culture.francetransfert.services.stat.StatServices;
import fr.gouv.culture.francetransfert.services.zipworker.ZipWorkerServices;
import fr.gouv.culture.francetransfert.utils.WorkerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;


@Component
public class ScheduledTasks {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledTasks.class);

    @Autowired
    private MailAvailbleEnclosureServices mailAvailbleEnclosureServices;

    @Autowired
    private MailRelaunchServices mailRelaunchServices;
    
    @Autowired
    private AppSyncServices appSyncServices;

    @Autowired
    private MailDownloadServices mailDownloadServices;

    @Autowired
    private MailConfirmationCodeServices mailConfirmationCodeServices;

    @Autowired
    private CleanUpServices cleanUpServices;
    
    @Autowired
    private ZipWorkerServices zipWorkerServices;

    @Autowired
    private StatServices statServices;

    @Autowired
    private SatisfactionService satisfactionService;
    
    @Autowired
    private RedisManager redisManager;


    @Scheduled(cron = "${scheduled.relaunch.mail}")
    public void relaunchMail() throws Exception{
    	LOGGER.info("================================> worker : start relaunch for download Check");
    	if(appSyncServices.shouldRelaunch()) {
    		LOGGER.info("================================> worker : start relaunch for download Checked and Started");
    		mailRelaunchServices.sendMailsRelaunch();
    	}
    }

    @Scheduled(cron = "${scheduled.clean.up}")
    public void cleanUp() throws Exception {
    	LOGGER.info("================================> worker : start clean-up expired enclosure Check");
    	if(appSyncServices.shouldCleanup()) {
    		LOGGER.info("================================> worker : start clean-up expired enclosure Checked and Started");
    		cleanUpServices.cleanUp();
    	}
    }
    
    @Scheduled(cron = "${scheduled.app.sync.cleanup}")
    public void appSyncCleanup() throws Exception {
        LOGGER.info("================================> worker : start Application synchronization cleanup");
        appSyncServices.appSyncCleanup();
    }
    
    @Scheduled(cron = "${scheduled.app.sync.relaunch}")
    public void appSyncRelaunch() throws Exception {
        LOGGER.info("================================> worker : start Application synchronization relaunch");
        appSyncServices.appSyncRelaunch();
    }

    @Scheduled(cron = "0 * * * * ?")
    public void sendEmailNotificationUploadDownload() throws Exception {
//        RedisManager redisManager = RedisManager.getInstance();
        List<String> returnedBLPOPList = redisManager.subscribeFT(RedisQueueEnum.MAIL_QUEUE.getValue());
        if (!CollectionUtils.isEmpty(returnedBLPOPList)) {
            String enclosureId = returnedBLPOPList.get(1);
            LOGGER.info("================================> worker : start send email notification availble enclosure to download for enclosure N° {}", enclosureId);
            mailAvailbleEnclosureServices.sendMailsAvailableEnclosure(Enclosure.build(enclosureId, redisManager));
            redisManager.publishFT(RedisQueueEnum.STAT_QUEUE.getValue(), enclosureId);
        }
    }

    @Scheduled(cron = "0 * * * * ?")
    public void sendEmailDownloadInProgress() throws Exception {
//        RedisManager redisManager = RedisManager.getInstance();
        List<String> returnedBLPOPList = redisManager.subscribeFT(RedisQueueEnum.DOWNLOAD_QUEUE.getValue());
        if (!CollectionUtils.isEmpty(returnedBLPOPList)) {
            String downloadQueueValue = returnedBLPOPList.get(1);
            String enclosureId = WorkerUtils.extractEnclosureIdFromDownloadQueueValue(downloadQueueValue);
            LOGGER.info("================================> worker : start send email notification download in progress for enclosur N°  {}", enclosureId);
            String recipientId = WorkerUtils.extractRecipientIdFromDownloadQueueValue(downloadQueueValue);
            mailDownloadServices.sendDownloadEnclosure(Enclosure.build(enclosureId, redisManager), recipientId);
        }
    }

    @Scheduled(cron = "0 * * * * ?")
    public void zipWorker() throws Exception {
//        RedisManager redisManager = RedisManager.getInstance();
        List<String> returnedBLPOPList = redisManager.subscribeFT(RedisQueueEnum.ZIP_QUEUE.getValue());
        if(!CollectionUtils.isEmpty(returnedBLPOPList)) {
        	String enclosureId = returnedBLPOPList.get(1);
            LOGGER.info("================================> worker : start zip  process for enclosur N°  {}", enclosureId);
        	zipWorkerServices.startZip(enclosureId);
        }
    }
    
    @Scheduled(cron = "0 * * * * ?")
    public void tempDataCleanUp() throws Exception {
//        RedisManager redisManager = RedisManager.getInstance();
        List<String> returnedBLPOPList = redisManager.subscribeFT(RedisQueueEnum.TEMP_DATA_CLEANUP_QUEUE.getValue());
        if (!CollectionUtils.isEmpty(returnedBLPOPList)) {
            String enclosureId = returnedBLPOPList.get(1);

            LOGGER.info("================================> start temp data cleanup process for enclosure N: {}" , enclosureId);
            cleanUpServices.cleanUpEnclosureTempDataInRedis(redisManager, enclosureId);
        }
    }
    @Scheduled(cron = "0 * * * * ?")
    public void sendEmailConfirmationCode() throws Exception {
//        RedisManager manager = RedisManager.getInstance();
        List<String> returnedBLPOPList = redisManager.subscribeFT(RedisQueueEnum.CONFIRMATION_CODE_MAIL_QUEUE.getValue());
        if (!CollectionUtils.isEmpty(returnedBLPOPList)) {
            String mailCode = returnedBLPOPList.get(1);
            LOGGER.info("================================> start send confirmation code", mailCode);
            mailConfirmationCodeServices.sendConfirmationCode(mailCode);
        }
    }

    @Scheduled(cron = "0 * * * * ?")
    public void satisfactionWorker() throws Exception{
//        RedisManager redisManager = RedisManager.getInstance();
        List<String> returnedBLPOPList = redisManager.subscribeFT(RedisQueueEnum.SATISFACTION_QUEUE.getValue());
        if (!CollectionUtils.isEmpty(returnedBLPOPList)) {
            Rate rate = new Gson().fromJson(returnedBLPOPList.get(1), Rate.class);
            LOGGER.info("================================> convert json in string to object rate");
            LOGGER.info("================================> start save satisfaction data in mongoDb");
            satisfactionService.saveData(rate);
        }
    }

    @Scheduled(cron = "0 * * * * ?")
    public void stat() throws Exception {
//        RedisManager redisManager = RedisManager.getInstance();
        List<String> returnedBLPOPList = redisManager.subscribeFT(RedisQueueEnum.STAT_QUEUE.getValue());
        if (!CollectionUtils.isEmpty(returnedBLPOPList)) {
            String enclosureId = returnedBLPOPList.get(1);
            LOGGER.info("================================> start save data in mongoDb", enclosureId);
            statServices.saveData(enclosureId);
            redisManager.publishFT(RedisQueueEnum.TEMP_DATA_CLEANUP_QUEUE.getValue(), enclosureId);
        }
    }

}

