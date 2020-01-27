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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
@Slf4j
public class ScheduledTasks {

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
        mailRelaunchServices.sendMailsRelaunch();
    }

    @Scheduled(cron = "${scheduled.clean.up}")
    public void cleanUp() throws Exception {
        cleanUpServices.cleanUp();
    }

    @Scheduled(cron = "0 * * * * ?")
    public void sendEmailNotificationUploadDownload() throws Exception {
        RedisManager manager = RedisManager.getInstance();
        List<String> returnedBLPOPList = manager.subscribeFT(RedisQueueEnum.MAIL_QUEUE.getValue());
        String enclosureId = returnedBLPOPList.get(1);
        log.debug("start send emails for enclosure N: {}", enclosureId);
        mailAvailbleEnclosureServices.sendMailsAvailableEnclosure(Enclosure.build(enclosureId));
        manager.publishFT(RedisQueueEnum.TEMP_DATA_CLEANUP_QUEUE.getValue(), enclosureId);
    }

    @Scheduled(cron = "0 * * * * ?")
    public void sendEmailDownloadInProgress() throws Exception {
        RedisManager manager = RedisManager.getInstance();
        List<String> returnedBLPOPList = manager.subscribeFT(RedisQueueEnum.DOWNLOAD_QUEUE.getValue());
        String downloadQueueValue = returnedBLPOPList.get(1);
        String enclosureId = WorkerUtils.extractEnclosureIdFromDownloadQueueValue(downloadQueueValue);
        String recipientId = WorkerUtils.extractRecipientIdFromDownloadQueueValue(downloadQueueValue);
        log.debug("start send email download in progress", enclosureId);
        mailDownloadServices.sendDownloadEnclosure(Enclosure.build(enclosureId), recipientId);
    }

    @Scheduled(cron = "0 * * * * ?")
    public void zipWorker() throws Exception {
        RedisManager manager = RedisManager.getInstance();
        List<String> returnedBLPOPList = manager.subscribeFT(RedisQueueEnum.ZIP_QUEUE.getValue());
        if(returnedBLPOPList != null) {
        	String enclosureId = returnedBLPOPList.get(1);
        	log.debug("start Zip process for enclosure N: {}", enclosureId);
        	zipWorkerServices.startZip(enclosureId);
        	manager.publishFT(RedisQueueEnum.MAIL_QUEUE.getValue(), enclosureId);
        }
    }
    
    @Scheduled(cron = "0 * * * * ?")
    public void tempDataCleanUp() throws Exception {
        RedisManager manager = RedisManager.getInstance();
        List<String> returnedBLPOPList = manager.subscribeFT(RedisQueueEnum.TEMP_DATA_CLEANUP_QUEUE.getValue());
        if (returnedBLPOPList != null) {
            String enclosureId = returnedBLPOPList.get(1);
            log.debug("start temp data cleanup process for enclosure N: {}", enclosureId);
            cleanUpServices.cleanUpEnclosureTempDataInRedis(manager, enclosureId);
        }
    }
    @Scheduled(cron = "0 * * * * ?")
    public void sendEmailConfirmationCode() throws Exception {
        RedisManager manager = RedisManager.getInstance();
        List<String> returnedBLPOPList = manager.subscribeFT(RedisQueueEnum.CONFIRMATION_CODE_MAIL_QUEUE.getValue());
        String mailCode = returnedBLPOPList.get(1);
        log.debug("start send confirmation code", mailCode);
        mailConfirmationCodeServices.sendConfirmationCode(mailCode);
    }

    @Scheduled(cron = "0 * * * * ?")
    public void satisfactionWorker() throws Exception{
        RedisManager manager = RedisManager.getInstance();
        List<String> returnedBLPOPList = manager.subscribeFT(RedisQueueEnum.SATISFACTION_QUEUE.getValue());
        if (!returnedBLPOPList.isEmpty()) {
            Rate rate = new Gson().fromJson(returnedBLPOPList.get(1), Rate.class);
            log.info("convert json in string to object rate");
        }
        //TODO: insert satisfaction in admin module
    }

    @Scheduled(cron = "0 * * * * ?")
    public void stat() {
    }

}

