package fr.gouv.culture.francetransfert.model;

import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.RedisManager;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.enums.EnclosureKeysEnum;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.utils.RedisUtils;
import fr.gouv.culture.francetransfert.utils.WorkerUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Enclosure {

    private String guid;

    private List<String> rootFiles;

    private List<String> rootDirs;

    private int countElements;

    private int totalSize;

    private String expireDate;

    private String sender;

    private List<String> recipients;

    private String message;

    private boolean existPassword;

    private String urlDownload;

    public static Enclosure build(String enclosureId) throws Exception {
        RedisManager redisManager = RedisManager.getInstance();
        List<String> filesOfEnclosure = RedisUtils.getRootFiles(redisManager, enclosureId);
        List<String> dirsOfEnclosure = RedisUtils.getRootDirs(redisManager, enclosureId);
        int totalSize = RedisUtils.getTotalSizeEnclosure(redisManager, enclosureId);
        String senderEnclosure = RedisUtils.getSenderEnclosure(redisManager, enclosureId);
        List<String> recipientsEnclosure = RedisUtils.getRecipientsEnclosure(redisManager, enclosureId);
        Map<String, String> enclosureRedis = RedisUtils.getEnclosure(redisManager, enclosureId);
        String dateCreationEnclosure = enclosureRedis.get(EnclosureKeysEnum.TIMESTAMP.getKey());
        String message = enclosureRedis.get(EnclosureKeysEnum.MESSAGE.getKey());
        String password = enclosureRedis.get(EnclosureKeysEnum.PASSWORD.getKey());
        boolean existPassword = password != null && !password.isEmpty();

        return Enclosure.builder()
                .guid(enclosureId)
                .rootFiles(filesOfEnclosure)
                .rootDirs(dirsOfEnclosure)
                .countElements(filesOfEnclosure.size()+dirsOfEnclosure.size())
                .totalSize(totalSize)
                .expireDate(WorkerUtils.getExipreDate(dateCreationEnclosure))
                .sender(senderEnclosure)
                .recipients(recipientsEnclosure)
                .message(message)
                .existPassword(existPassword)
                .build();
    }

}
