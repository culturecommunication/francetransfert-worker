package fr.gouv.culture.francetransfert.worker;

import com.google.gson.Gson;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RedisQueueEnum;
import fr.gouv.culture.francetransfert.model.Enclosure;
import fr.gouv.culture.francetransfert.model.Rate;
import fr.gouv.culture.francetransfert.services.cleanup.CleanUpServices;
import fr.gouv.culture.francetransfert.services.mail.notification.MailAvailbleEnclosureServices;
import fr.gouv.culture.francetransfert.services.mail.notification.MailConfirmationCodeServices;
import fr.gouv.culture.francetransfert.services.mail.notification.MailDownloadServices;
import fr.gouv.culture.francetransfert.services.mail.notification.MailRelaunchServices;
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
    private MailDownloadServices mailDownloadServices;

    @Autowired
    private MailConfirmationCodeServices mailConfirmationCodeServices;



    @Autowired
    private CleanUpServices cleanUpServices;
    
    @Autowired
    private ZipWorkerServices zipWorkerServices;


    @Scheduled(cron = "${scheduled.relaunch.mail}")
    public void relaunchMail() throws Exception{
        LOGGER.info("================================> worker : start relaunch for download");
        mailRelaunchServices.sendMailsRelaunch();
    }

    @Scheduled(cron = "${scheduled.clean.up}")
    public void cleanUp() throws Exception {
        LOGGER.info("================================> worker : start clean-up expired enclosure");
        cleanUpServices.cleanUp();
    }

    @Scheduled(cron = "0 * * * * ?")
    public void sendEmailNotificationUploadDownload() throws Exception {
        RedisManager manager = RedisManager.getInstance();
        List<String> returnedBLPOPList = manager.subscribeFT(RedisQueueEnum.MAIL_QUEUE.getValue());
        if (!CollectionUtils.isEmpty(returnedBLPOPList)) {
            String enclosureId = returnedBLPOPList.get(1);
            LOGGER.info("================================> worker : start send email notification availble enclosure to download for enclosure N° {}", enclosureId);
            mailAvailbleEnclosureServices.sendMailsAvailableEnclosure(Enclosure.build(enclosureId));
            manager.publishFT(RedisQueueEnum.TEMP_DATA_CLEANUP_QUEUE.getValue(), enclosureId);
        }
    }

    @Scheduled(cron = "0 * * * * ?")
    public void sendEmailDownloadInProgress() throws Exception {
        RedisManager manager = RedisManager.getInstance();
        List<String> returnedBLPOPList = manager.subscribeFT(RedisQueueEnum.DOWNLOAD_QUEUE.getValue());
        if (!CollectionUtils.isEmpty(returnedBLPOPList)) {
            String downloadQueueValue = returnedBLPOPList.get(1);
            String enclosureId = WorkerUtils.extractEnclosureIdFromDownloadQueueValue(downloadQueueValue);
            LOGGER.info("================================> worker : start send email notification download in progress for enclosur N°  {}", enclosureId);
            String recipientId = WorkerUtils.extractRecipientIdFromDownloadQueueValue(downloadQueueValue);
            mailDownloadServices.sendDownloadEnclosure(Enclosure.build(enclosureId), recipientId);
        }
    }

    @Scheduled(cron = "0 * * * * ?")
    public void zipWorker() throws Exception {
        RedisManager manager = RedisManager.getInstance();
        List<String> returnedBLPOPList = manager.subscribeFT(RedisQueueEnum.ZIP_QUEUE.getValue());
        if(!CollectionUtils.isEmpty(returnedBLPOPList)) {
        	String enclosureId = returnedBLPOPList.get(1);
            LOGGER.info("================================> worker : start zip  process for enclosur N°  {}", enclosureId);
        	zipWorkerServices.startZip(enclosureId);
        	manager.publishFT(RedisQueueEnum.MAIL_QUEUE.getValue(), enclosureId);
        }
    }
    
    @Scheduled(cron = "0 * * * * ?")
    public void tempDataCleanUp() throws Exception {
        RedisManager manager = RedisManager.getInstance();
        List<String> returnedBLPOPList = manager.subscribeFT(RedisQueueEnum.TEMP_DATA_CLEANUP_QUEUE.getValue());
        if (!CollectionUtils.isEmpty(returnedBLPOPList)) {
            String enclosureId = returnedBLPOPList.get(1);
            LOGGER.info("================================> start temp data cleanup process for enclosure N: {}" , enclosureId);
            cleanUpServices.cleanUpEnclosureTempDataInRedis(manager, enclosureId);
        }
    }
    @Scheduled(cron = "0 * * * * ?")
    public void sendEmailConfirmationCode() throws Exception {
        RedisManager manager = RedisManager.getInstance();
        List<String> returnedBLPOPList = manager.subscribeFT(RedisQueueEnum.CONFIRMATION_CODE_MAIL_QUEUE.getValue());
        if (!CollectionUtils.isEmpty(returnedBLPOPList)) {
            String mailCode = returnedBLPOPList.get(1);
            LOGGER.info("================================> start send confirmation code", mailCode);
            mailConfirmationCodeServices.sendConfirmationCode(mailCode);
        }
    }

    @Scheduled(cron = "0 * * * * ?")
    public void satisfactionWorker() throws Exception{
        RedisManager manager = RedisManager.getInstance();
        List<String> returnedBLPOPList = manager.subscribeFT(RedisQueueEnum.SATISFACTION_QUEUE.getValue());
        if (!CollectionUtils.isEmpty(returnedBLPOPList)) {
            Rate rate = new Gson().fromJson(returnedBLPOPList.get(1), Rate.class);
            LOGGER.info("================================> convert json in string to object rate");
        }
        //TODO: insert satisfaction in admin module
    }

    @Scheduled(cron = "0 * * * * ?")
    public void stat() {
    }

}

