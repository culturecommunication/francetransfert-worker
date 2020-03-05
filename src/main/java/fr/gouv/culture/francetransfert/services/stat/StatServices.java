package fr.gouv.culture.francetransfert.services.stat;

import fr.gouv.culture.francetransfert.enums.SizeIndexEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.EnclosureKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.DateUtils;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.RedisUtils;
import fr.gouv.culture.francetransfert.model.MongoLocalDateTime;
import fr.gouv.culture.francetransfert.model.SizeData;
import fr.gouv.culture.francetransfert.model.UsageData;
import fr.gouv.culture.francetransfert.repository.SizeDataRepository;
import fr.gouv.culture.francetransfert.repository.UsageDataRepository;
import fr.gouv.culture.francetransfert.security.WorkerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class StatServices {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatServices.class);

    @Autowired
    private UsageDataRepository usageDataRepository;

    @Autowired
    private SizeDataRepository sizeDataRepository;
    
    @Autowired
    RedisManager redisManager;

    public boolean saveData(String enclosureId) throws WorkerException {
        try {
            boolean isSaved = true;
//            RedisManager redisManager = RedisManager.getInstance();
            Map<String, String> enclosureRedis = RedisUtils.getEnclosure(redisManager, enclosureId);
            MongoLocalDateTime mongoLocalDateTime = MongoLocalDateTime.of(DateUtils.convertStringToLocalDateTime(enclosureRedis.get(EnclosureKeysEnum.TIMESTAMP.getKey())));
            LOGGER.info("=================== save collection usage data mongoDB");
            UsageData usageData = UsageData.builder()
                    .date(mongoLocalDateTime)
                    .isNewUser(RedisUtils.isNewSenderEnclosure(redisManager, enclosureId))
                    .build();
            usageData = usageDataRepository.save(usageData);
            isSaved = (usageData != null);
            LOGGER.info("=================== save collection size data mongoDB");
            long totalSizeEnclosure = RedisUtils.getTotalSizeEnclosure(redisManager, enclosureId);
            String sizeIndexEnclosure = SizeIndexEnum.getSizeIndex(totalSizeEnclosure);
            LOGGER.debug("=================== size index enclosure is : {}", sizeIndexEnclosure);
            SizeData sizeData = SizeData.builder()
                    .date(mongoLocalDateTime)
                    .enclosureSize(totalSizeEnclosure)
                    .enclosureSizeIndex(sizeIndexEnclosure)
                    .build();
            sizeData = sizeDataRepository.save(sizeData);
            isSaved = (sizeData != null);
            return isSaved;
        } catch (Exception e) {
            LOGGER.error("=================== error save data in mongoDB");
            throw new WorkerException("error save data in mongoDb");
        }
    }
}
