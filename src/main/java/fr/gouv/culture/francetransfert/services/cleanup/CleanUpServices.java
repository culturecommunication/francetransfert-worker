package fr.gouv.culture.francetransfert.services.cleanup;

import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.RedisManager;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.enums.EnclosureKeysEnum;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.enums.RedisKeysEnum;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.utils.DateUtils;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.utils.RedisUtils;
import com.opengroup.mc.francetransfert.api.francetransfert_storage_api.StorageManager;
import fr.gouv.culture.francetransfert.model.Enclosure;
import fr.gouv.culture.francetransfert.security.WorkerException;
import fr.gouv.culture.francetransfert.services.mail.notification.MailEnclosureNoLongerAvailbleServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class CleanUpServices {

    @Autowired
    MailEnclosureNoLongerAvailbleServices mailEnclosureNoLongerAvailbleServices;


    /**
     * clean all expired data in OSU and REDIS
     * @throws Exception 
     */
    public void cleanUp() throws Exception {
        RedisManager redisManager = RedisManager.getInstance();
        redisManager.smembersString(RedisKeysEnum.FT_ENCLOSURE_DATES.getKey("")).forEach(date -> {
            redisManager.smembersString(RedisKeysEnum.FT_ENCLOSURE_DATE.getKey(date)).forEach( enclosureId -> {
                try {
                    mailEnclosureNoLongerAvailbleServices.sendEnclosureNotAvailble(Enclosure.build(enclosureId));
                    LocalDate enclosureExipireDateRedis = DateUtils.convertStringToLocalDateTime(redisManager.getHgetString(enclosureId, EnclosureKeysEnum.EXPIRED_TIMESTAMP.getKey())).toLocalDate();
                    if (enclosureExipireDateRedis.plusDays(1).equals(LocalDate.now())) {// expire date + 1
                        // clean enclosure in OSU : delete enclosure
                        String bucketName = RedisUtils.getBucketName(redisManager, enclosureId);
                        cleanUpOSU(bucketName, enclosureId);
                        // clean enclosure Core in REDIS : delete files, root-files, root-dirs, recipients, sender and enclosure
                        cleanUpEnclosureCoreInRedis(redisManager, enclosureId);
                    }
                } catch (Exception e) {
                    throw new WorkerException("");
                }
            });
            // clean enclosure date : delete list enclosureId and date expired
            cleanUpEnclosureDatesInRedis(redisManager, date);
        });
    }

    /**
     * clean all data expired in OSU
     * @param enclosureId
     * @throws Exception 
     */
    private void cleanUpOSU(String bucketName, String enclosureId) throws Exception {
        StorageManager storageManager = new StorageManager();
        storageManager.deleteFilesWithPrefix(bucketName, enclosureId + ".zip");
    }

    /**
     * clean expired data in REDIS: Enclosure core
     * @param redisManager
     * @param enclosureId
     * @throws WorkerException
     */
    private void cleanUpEnclosureCoreInRedis(RedisManager redisManager, String enclosureId) throws WorkerException {
        //delete part-etags
        redisManager.deleteKey(RedisKeysEnum.FT_PART_ETAGS.getKey(enclosureId));
        log.debug("clean part-etags {}", RedisKeysEnum.FT_PART_ETAGS.getKey(enclosureId));
        //delete list and HASH files
        deleteListAndHashFiles(redisManager, RedisKeysEnum.FT_FILES_IDS, RedisKeysEnum.FT_FILE, enclosureId);
        log.debug("clean files {}", RedisKeysEnum.FT_FILES_IDS.getKey(enclosureId));
        //delete list and HASH root-files
        deleteListAndHashFiles(redisManager, RedisKeysEnum.FT_ROOT_FILES, RedisKeysEnum.FT_ROOT_FILE, enclosureId);
        log.debug("clean root-files {}", RedisKeysEnum.FT_ROOT_FILES.getKey(enclosureId));
        //delete list and HASH root-dirs
        deleteListAndHashFiles(redisManager, RedisKeysEnum.FT_ROOT_DIRS, RedisKeysEnum.FT_ROOT_DIR, enclosureId);
        log.debug("clean root-dirs {}", RedisKeysEnum.FT_ROOT_DIRS.getKey(enclosureId));
        //delete list and HASH recipients
        deleteListAndHashRecipients(redisManager, enclosureId);
        log.debug("clean recipients {}", RedisKeysEnum.FT_RECIPIENTS.getKey(enclosureId));
        //delete hash sender
        redisManager.deleteKey(RedisKeysEnum.FT_SENDER.getKey(enclosureId));
        log.debug("clean sender HASH {}", RedisKeysEnum.FT_SENDER.getKey(enclosureId));
        //delete hash enclosure
        redisManager.deleteKey(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId));
        log.debug("clean enclosure HASH {}", RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId));
    }

    /**
     * clean temp data in REDIS for Enclosure
     * @param redisManager
     * @param enclosureId
     * @throws WorkerException
     */
    public void cleanUpEnclosureTempDataInRedis(RedisManager redisManager, String enclosureId) throws WorkerException {
        //delete part-etags
        redisManager.deleteKey(RedisKeysEnum.FT_PART_ETAGS.getKey(enclosureId));
        log.debug("clean part-etags {}", RedisKeysEnum.FT_PART_ETAGS.getKey(enclosureId));
        //delete list and HASH root-files
        deleteListAndHashFiles(redisManager, RedisKeysEnum.FT_ROOT_FILES, RedisKeysEnum.FT_ROOT_FILE, enclosureId);
        log.debug("clean root-files {}", RedisKeysEnum.FT_ROOT_FILES.getKey(enclosureId));
        //delete list and HASH root-dirs
        deleteListAndHashFiles(redisManager, RedisKeysEnum.FT_ROOT_DIRS, RedisKeysEnum.FT_ROOT_DIR, enclosureId);
        log.debug("clean root-dirs {}", RedisKeysEnum.FT_ROOT_DIRS.getKey(enclosureId));
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
        log.debug("clean list enclosure per date {}", RedisKeysEnum.FT_ENCLOSURE_DATE.getKey(date));
        //delete date expired from the list of dates
        redisManager.deleteHField(RedisKeysEnum.FT_ENCLOSURE_DATES.getKey(""), date);
        log.debug("finish clean up list dates {} delete date : {} ", RedisKeysEnum.FT_ENCLOSURE_DATE.getKey(date), date);
    }

    /**
     *
     * @param redisManager
     * @param redisKeysEnumList
     * @param redisKeysEnumHash
     * @param enclosureId
     */
    private void deleteListAndHashFiles(RedisManager redisManager, RedisKeysEnum redisKeysEnumList, RedisKeysEnum redisKeysEnumHash, String enclosureId) {
        String keyFiles = redisKeysEnumList.getKey(enclosureId);
        //list files
        List<String> listFileIds = redisManager.lrange(keyFiles, 0, -1);
        //delete Hash files info
        for (String fileId :listFileIds) {
            String keyFile = redisKeysEnumHash.getKey(RedisUtils.generateHashsha1(enclosureId + ":" + fileId));
            redisManager.deleteKey(keyFile);
        }
        // delete list of files
        redisManager.deleteKey(keyFiles);
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
