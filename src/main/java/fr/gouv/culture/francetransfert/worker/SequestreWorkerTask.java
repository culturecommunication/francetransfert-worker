package fr.gouv.culture.francetransfert.worker;

import fr.gouv.culture.francetransfert.services.sequestre.SequestreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SequestreWorkerTask implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TempDataCleanupTask.class);

    private SequestreService sequestreService;

    private String enclosureId;

    private String nameBucketDest;


    public SequestreWorkerTask(String enclosureId, String nameBucketDest, SequestreService sequestreService) {
        this.enclosureId = enclosureId;
        this.sequestreService = sequestreService;
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
            sequestreService.createSequestre(nameBucketDest);
            sequestreService.writeOnSequestre(enclosureId);
            sequestreService.removeEnclosureSequestre(enclosureId);

        } catch (Exception e) {
            LOGGER.error("[Worker] error while creating sequestre : " + e.getMessage(), e);
        }
        LOGGER.info("ThreadName: " + Thread.currentThread().getName() + " | ThreadId: " + Thread.currentThread().getId()
                + " IS DEAD");
    }
}
