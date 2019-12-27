package fr.gouv.culture.francetransfert.worker;

import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.model.Enclosure;
import fr.gouv.culture.francetransfert.services.mail.notification.MailNotificationServices;
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
    MailNotificationServices mailNotificationServices;

    @Scheduled(cron = "0 * * * * ?")
    public void sendEmailNotificationUploadDownload() throws Exception {
        RedisManager manager = RedisManager.getInstance();
        List<String> returnedBLPOPList = manager.subscribeFT("email-notification-queue");
        String enclosureId = returnedBLPOPList.get(1);
        log.debug("start send emails for enclosure N: {}", enclosureId);
        mailNotificationServices.sendMails(Enclosure.build(enclosureId));
    }

    @Scheduled(cron = "0 * * * * ?")
    public void zipWorker() throws IOException {
        RedisManager manager = RedisManager.getInstance();
        List<String> returnedBLPOPList = manager.subscribeFT("zip-worker-queue");
    }

    @Scheduled(cron = "0 * * * * ?")
    public void cleanUp() {
    }

    @Scheduled(cron = "0 * * * * ?")
    public void stat() {
    }

}

