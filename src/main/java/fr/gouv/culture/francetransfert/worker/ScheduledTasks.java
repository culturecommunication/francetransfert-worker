package fr.gouv.culture.francetransfert.worker;

import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.RedisManager;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.enums.RedisQueueEnum;
import fr.gouv.culture.francetransfert.model.Enclosure;
import fr.gouv.culture.francetransfert.services.mail.notification.MailAvailbleEnclosureServices;
import fr.gouv.culture.francetransfert.services.mail.notification.MailDownloadServices;
import fr.gouv.culture.francetransfert.services.mail.notification.MailRelaunchServices;
import fr.gouv.culture.francetransfert.utils.WorkerUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
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


    @Scheduled(cron = "0 * * * * ?")
    public void sendEmailNotificationUploadDownload() throws Exception {
        RedisManager manager = RedisManager.getInstance();
        List<String> returnedBLPOPList = manager.subscribeFT(RedisQueueEnum.MAIL_QUEUE.getValue());
        String enclosureId = returnedBLPOPList.get(1);
        log.debug("start send emails for enclosure N: {}", enclosureId);
        mailAvailbleEnclosureServices.sendMailsAvailableEnclosure(Enclosure.build(enclosureId));
    }

    @Scheduled(cron = "${scheduled.relaunch.mail}")
    public void relaunchMail() throws Exception{
        mailRelaunchServices.sendMailsRelaunch();
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
    public void zipWorker() throws IOException {
        RedisManager manager = RedisManager.getInstance();
        List<String> returnedBLPOPList = manager.subscribeFT(RedisQueueEnum.ZIP_QUEUE.getValue());
//        zipWorkerTask.zipWorker();
        String enclosureId = "";
        manager.rpush(RedisQueueEnum.MAIL_QUEUE.getValue(), enclosureId);
    }

    @Scheduled(cron = "0 * * * * ?")
    public void cleanUp() {
    }

    @Scheduled(cron = "0 * * * * ?")
    public void stat() {
    }

}

