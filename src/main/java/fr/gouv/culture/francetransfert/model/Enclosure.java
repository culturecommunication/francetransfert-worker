package fr.gouv.culture.francetransfert.model;

import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.EnclosureKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RedisKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.DateUtils;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.RedisUtils;
import fr.gouv.culture.francetransfert.utils.WorkerUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Enclosure {

    private String guid;

    private List<RootData> rootFiles;

    private List<RootData> rootDirs;

    private int countElements;

    private String totalSize;

    private String expireDate;

    private String sender;

    private List<Recipient> recipients;

    private String message;

    private boolean withPassword;

    private String urlDownload;

    private List<String> recipientDownloadInProgress;
    
    private String password;

    private String urlAdmin;

    private boolean publicLink;
    

    public static Enclosure build(String enclosureId, RedisManager redisManager) throws Exception {

        List<RootData> filesOfEnclosure = new ArrayList<>();
        for (Map.Entry<String, Long> rootFile: RedisUtils.getRootFilesWithSize(redisManager, enclosureId).entrySet()) {
            filesOfEnclosure.add(RootData.builder().name(rootFile.getKey()).extension(WorkerUtils.getExtension(rootFile.getKey())).size(WorkerUtils.getFormattedFileSize(rootFile.getValue())).nameWithoutExtension(FilenameUtils.removeExtension(rootFile.getKey())).build());
        }
        List<RootData> dirsOfEnclosure = new ArrayList<>();
        for (Map.Entry<String, Long> rootDir: RedisUtils.getRootDirsWithSize(redisManager, enclosureId).entrySet()) {
            dirsOfEnclosure.add(RootData.builder().name(rootDir.getKey()).size(WorkerUtils.getFormattedFileSize(rootDir.getValue())).nameWithoutExtension(rootDir.getKey()).build());
        }
        String totalSize = WorkerUtils.getFormattedFileSize(RedisUtils.getTotalSizeEnclosure(redisManager, enclosureId));
        String senderEnclosure = RedisUtils.getEmailSenderEnclosure(redisManager, enclosureId);
        List<Recipient> recipientsEnclosure = new ArrayList<>();
        for (Map.Entry<String, String> recipient: RedisUtils.getRecipientsEnclosure(redisManager, enclosureId).entrySet()) {
            recipientsEnclosure.add(Recipient.builder().mail(recipient.getKey()).id(recipient.getValue()).build());
        }
        Map<String, String> enclosureRedis = RedisUtils.getEnclosure(redisManager, enclosureId);
        String expireEnclosureDate = DateUtils.formatLocalDateTime(enclosureRedis.get(EnclosureKeysEnum.EXPIRED_TIMESTAMP.getKey()));
        String message = enclosureRedis.get(EnclosureKeysEnum.MESSAGE.getKey());
        String password = enclosureRedis.get(EnclosureKeysEnum.PASSWORD.getKey());
        boolean withPassword = password != null && !password.isEmpty();

        return Enclosure.builder()
                .guid(enclosureId)
                .rootFiles(filesOfEnclosure)
                .rootDirs(dirsOfEnclosure)
                .countElements(filesOfEnclosure.size()+dirsOfEnclosure.size())
                .totalSize(totalSize)
                .expireDate(expireEnclosureDate)
                .sender(senderEnclosure)
                .recipients(recipientsEnclosure)
                .message(message)
                .withPassword(withPassword)
                .build();
    }
}
