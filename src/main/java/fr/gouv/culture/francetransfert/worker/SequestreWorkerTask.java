package fr.gouv.culture.francetransfert.worker;

import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.RedisUtils;
import fr.gouv.culture.francetransfert.francetransfert_storage_api.StorageManager;
import fr.gouv.culture.francetransfert.services.cleanup.CleanUpServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SequestreWorkerTask implements Runnable{
    private static final Logger LOGGER = LoggerFactory.getLogger(TempDataCleanupTask.class);

    private CleanUpServices cleanUpServices;

    private String enclosureId;

    private  String nameBucketDest;


    public SequestreWorkerTask(String enclosureId,String nameBucketDest, CleanUpServices cleanUpServices) {
        this.enclosureId = enclosureId;
        this.cleanUpServices = cleanUpServices;
        this.nameBucketDest = nameBucketDest;
    }

    public SequestreWorkerTask() {

    }

    @Override
    public void run() {
        try {
            LOGGER.info("ThreadName: " + Thread.currentThread().getName() + " | ThreadId: "
                    + Thread.currentThread().getId());
            LOGGER.info(" start coping data process for enclosure N: {}", enclosureId);
            cleanUpServices.createSequestre(nameBucketDest);
            cleanUpServices.writeOnSequestre(enclosureId);

        } catch (Exception e) {
            LOGGER.error("[Worker] error while creating sequestre : " + e.getMessage(), e);
        }
        LOGGER.info("ThreadName: " + Thread.currentThread().getName() + " | ThreadId: " + Thread.currentThread().getId()
                + " IS DEAD");
    }
}
