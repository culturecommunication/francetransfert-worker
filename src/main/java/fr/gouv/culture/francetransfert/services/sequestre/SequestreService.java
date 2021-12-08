package fr.gouv.culture.francetransfert.services.sequestre;

import com.amazonaws.services.s3.AmazonS3;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RedisKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.exception.MetaloadException;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.RedisUtils;
import fr.gouv.culture.francetransfert.francetransfert_storage_api.Exception.StorageException;
import fr.gouv.culture.francetransfert.francetransfert_storage_api.StorageManager;
import fr.gouv.culture.francetransfert.services.cleanup.CleanUpServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SequestreService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CleanUpServices.class);

    private AmazonS3 conn;

    @Value("${bucket.prefix}")
    private String bucketPrefix;


    @Autowired
    StorageManager storageManager;

    @Autowired
    RedisManager redisManager;

    public void createSequestre(String prefix) throws MetaloadException, StorageException {
        try {
            storageManager.generateBucketSequestre(prefix);
        }catch (Exception e) {
            LOGGER.error("Error in create bucket sequestre : " + e.getMessage(), e);
        }
    }

    public void writeOnSequestre(String enclosureId){
        try{
            String nameBucketSource = RedisUtils.getBucketName(redisManager, enclosureId,bucketPrefix );
            String fileName = storageManager.getZippedEnclosureName(enclosureId);
            fileName = fileName + ".zip";
            storageManager.moveOnSequestre(nameBucketSource,fileName);
        }catch (Exception e){
            LOGGER.error("Error while coping in sequestre : " + e.getMessage(), e);
        }
    }

    public void removeEnclosureSequestre(String enclosureId){
        List<RedisKeysEnum> RedisKeysEnumvalidate = new ArrayList<>();
        for(RedisKeysEnum val :RedisKeysEnum.values()){
            if(val.getFirstKeyPart().startsWith("enclosure:")){
                RedisKeysEnumvalidate.add(val);
            }
        }
        try{
            RedisKeysEnumvalidate.forEach(val -> {
                redisManager.renameKey(val.getKey(enclosureId),val.getKey(enclosureId).replace("enclosure:","sequestre:"));
            });
        }catch (Exception e) {
            LOGGER.error("Error find key : " + e.getMessage(), e);
        }
    }
}
