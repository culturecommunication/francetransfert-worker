package fr.gouv.culture.francetransfert.services.cleanup;

import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.EnclosureKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RedisKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.DateUtils;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.RedisUtils;
import fr.gouv.culture.francetransfert.francetransfert_storage_api.StorageManager;
import fr.gouv.culture.francetransfert.model.Enclosure;
import fr.gouv.culture.francetransfert.security.WorkerException;
import fr.gouv.culture.francetransfert.services.mail.notification.MailEnclosureNoLongerAvailbleServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class CleanUpServices {

    private static final Logger LOGGER = LoggerFactory.getLogger(CleanUpServices.class);

    @Value("${bucket.prefix}")
    private String bucketPrefix;

    @Autowired
    MailEnclosureNoLongerAvailbleServices mailEnclosureNoLongerAvailbleServices;


    @Autowired
    StorageManager storageManager;
    
    @Autowired
    RedisManager redisManager;
    
    /**
     * clean all expired data in OSU and REDIS
     * @throws Exception 
     */
    public void cleanUp() throws Exception {
//        RedisManager redisManager = RedisManager.getInstance();
        redisManager.smembersString(RedisKeysEnum.FT_ENCLOSURE_DATES.getKey("")).forEach(date -> {
            redisManager.smembersString(RedisKeysEnum.FT_ENCLOSURE_DATE.getKey(date)).forEach( enclosureId -> {
                try {
                    LocalDate enclosureExipireDateRedis = DateUtils.convertStringToLocalDateTime(redisManager.getHgetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId), EnclosureKeysEnum.EXPIRED_TIMESTAMP.getKey())).toLocalDate();
                    if (enclosureExipireDateRedis.plusDays(1).equals(LocalDate.now())) { // expire date + 1
                        mailEnclosureNoLongerAvailbleServices.sendEnclosureNotAvailble(Enclosure.build(enclosureId, redisManager));
                        LOGGER.info("================================> clean up for enclosure N° {}", enclosureId );
                        // clean enclosure in OSU : delete enclosure
                        String bucketName = RedisUtils.getBucketName(redisManager, enclosureId, bucketPrefix);
                        LOGGER.info("================================> clean up OSU");
                        cleanUpOSU(bucketName, enclosureId);
                        // clean enclosure Core in REDIS : delete files, root-files, root-dirs, recipients, sender and enclosure
                        LOGGER.info("================================> clean up REDIS");
                        cleanUpEnclosureCoreInRedis(redisManager, enclosureId);
                        // clean enclosure date : delete list enclosureId and date expired
                        cleanUpEnclosureDatesInRedis(redisManager, date);
                    }
                } catch (Exception e) {
                    throw new WorkerException("");
                }
            });
        });
    }

    /**
     * clean all data expired in OSU
     * @param enclosureId
     * @throws Exception 
     */
    private void cleanUpOSU(String bucketName, String enclosureId) throws Exception {
//        StorageManager storageManager = StorageManager.getInstance();
        storageManager.deleteFilesWithPrefix(bucketName, storageManager.getZippedEnclosureName(enclosureId) + ".zip");
    }

    /**
     * clean expired data in REDIS: Enclosure core
     * @param redisManager
     * @param enclosureId
     * @throws WorkerException
     */
    private void cleanUpEnclosureCoreInRedis(RedisManager redisManager, String enclosureId) throws WorkerException {
        //delete list and HASH root-files
        deleteRootFiles(redisManager, enclosureId);
        LOGGER.debug("clean root-files {}", RedisKeysEnum.FT_ROOT_FILES.getKey(enclosureId));
        //delete list and HASH root-dirs
        deleteRootDirs(redisManager, enclosureId);
        LOGGER.debug("clean root-dirs {}", RedisKeysEnum.FT_ROOT_DIRS.getKey(enclosureId));
        //delete list and HASH recipients
        deleteListAndHashRecipients(redisManager, enclosureId);
        LOGGER.debug("clean recipients {}", RedisKeysEnum.FT_RECIPIENTS.getKey(enclosureId));
        //delete hash sender
        redisManager.deleteKey(RedisKeysEnum.FT_SENDER.getKey(enclosureId));
        LOGGER.debug("clean sender HASH {}", RedisKeysEnum.FT_SENDER.getKey(enclosureId));
        //delete hash enclosure
        redisManager.deleteKey(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId));
        LOGGER.debug("clean enclosure HASH {}", RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId));
    }

    /**
     * clean temp data in REDIS for Enclosure
     * @param redisManager
     * @param enclosureId
     * @throws WorkerException
     */
    public void cleanUpEnclosureTempDataInRedis(RedisManager redisManager, String enclosureId) throws WorkerException {
        //delete part-etags
        deleteListPartEtags(redisManager, enclosureId);
        //delete list and HASH files
        deleteFiles(redisManager, enclosureId);
    }
    
    
    /**
     * clean expired data in REDIS: Enclosure dates
     * @param redisManager
     * @param date
     * @throws WorkerException
     */
    private void cleanUpEnclosureDatesInRedis(RedisManager redisManager, String date) throws WorkerException {
        //delete list enclosureId  of expired date
        redisManager.deleteKey(RedisKeysEnum.FT_ENCLOSURE_DATE.getKey(date));
        LOGGER.debug("clean list enclosure per date {}", RedisKeysEnum.FT_ENCLOSURE_DATE.getKey(date));
        //delete date expired from the list of dates
        redisManager.sremString(RedisKeysEnum.FT_ENCLOSURE_DATES.getKey(""), date);
        LOGGER.debug("finish clean up list dates {} delete date : {} ", RedisKeysEnum.FT_ENCLOSURE_DATE.getKey(date), date);
    }

    /**
     *
     * @param redisManager
     * @param enclosureId
     */
    private void deleteFiles(RedisManager redisManager, String enclosureId) {
        String keyFiles = RedisKeysEnum.FT_FILES_IDS.getKey(enclosureId);
        //list files
        List<String> listFileIds = redisManager.lrange(keyFiles, 0, -1);
        //delete Hash files info
        LOGGER.debug("=========== clean up files: {}", RedisKeysEnum.FT_FILES_IDS.getKey(enclosureId));
        for (String fileId :listFileIds) {
//            redisManager.hmgetAllString(RedisKeysEnum.FT_FILE.getKey(fileId))
            redisManager.deleteKey(RedisKeysEnum.FT_FILE.getKey(fileId));
            LOGGER.debug("clean up file: {}", RedisKeysEnum.FT_FILE.getKey(fileId));
        }
        // delete list of files
        redisManager.deleteKey(keyFiles);
    }

    private void deleteRootFiles(RedisManager redisManager, String enclosureId) {
        String keyRootFiles = RedisKeysEnum.FT_ROOT_FILES.getKey(enclosureId);
        //list root-files
        List<String> listRootFileIds = redisManager.lrange(keyRootFiles, 0, -1);
        //delete Hash root-files info
        LOGGER.debug("=========== clean up root-files: {}", RedisKeysEnum.FT_ROOT_FILES.getKey(enclosureId));
        for (String rootFileId :listRootFileIds) {
//            redisManager.hmgetAllString(RedisKeysEnum.FT_ROOT_FILE.getKey(RedisUtils.generateHashsha1(enclosureId + ":" + rootFileId)))
            redisManager.deleteKey(RedisKeysEnum.FT_ROOT_FILE.getKey(RedisUtils.generateHashsha1(enclosureId + ":" + rootFileId)));
            LOGGER.debug("clean up root-file: {}", RedisKeysEnum.FT_ROOT_FILE.getKey(rootFileId));
        }
        // delete list of root-files
        redisManager.deleteKey(keyRootFiles);
    }

    private void deleteRootDirs(RedisManager redisManager, String enclosureId) {
        String keyrootDirs = RedisKeysEnum.FT_ROOT_DIRS.getKey(enclosureId);
        //list root-dirs
        List<String> listRootDirIds = redisManager.lrange(keyrootDirs, 0, -1);
        //delete Hash root-dirs info
        LOGGER.debug("=========== clean up root-dirs: {}", RedisKeysEnum.FT_ROOT_DIRS.getKey(enclosureId));
        for (String rootDirId :listRootDirIds) {
//            redisManager.hmgetAllString(RedisKeysEnum.FT_ROOT_DIR.getKey(RedisUtils.generateHashsha1(enclosureId + ":" + rootDirId)))
            redisManager.deleteKey(RedisKeysEnum.FT_ROOT_DIR.getKey(RedisUtils.generateHashsha1(enclosureId + ":" + rootDirId)));
            LOGGER.debug("clean up root-dir: {}", RedisKeysEnum.FT_ROOT_DIR.getKey(rootDirId));
        }
        // delete list of root-dirs
        redisManager.deleteKey(keyrootDirs);
    }

    private void deleteListPartEtags(RedisManager redisManager, String enclosureId) {
        //list files
        List<String> listFileIds = redisManager.lrange(RedisKeysEnum.FT_FILES_IDS.getKey(enclosureId), 0, -1);
        //delete list part-etags
        for (String fileId :listFileIds) {
            redisManager.deleteKey(RedisKeysEnum.FT_PART_ETAGS.getKey(fileId));
            LOGGER.debug("clean part-etags {}", RedisKeysEnum.FT_PART_ETAGS.getKey(fileId));
        }
    }

    /**
     *
     * @param redisManager
     * @param enclosureId
     * @throws Exception
     */
    private void deleteListAndHashRecipients(RedisManager redisManager, String enclosureId) throws WorkerException {
        try {
            //Map recipients  exemple : "charles.domenech@drac-idf.culture.gouv.fr":        "93e86440-fc67-4d71-9f74-fe17325e946a",
            Map<String, String> mapRecipients = RedisUtils.getRecipientsEnclosure(redisManager, enclosureId);
            for (String recipientId: mapRecipients.values()) {
                //delete Hash recipient info
                redisManager.deleteKey(RedisKeysEnum.FT_RECIPIENT.getKey(recipientId));
            }
            //delete Hash recipients info
            redisManager.deleteKey(RedisKeysEnum.FT_RECIPIENTS.getKey(enclosureId));
        } catch (Exception e) {
            throw new WorkerException("");
        }
    }
}
