package fr.gouv.culture.francetransfert.services.zipworker;

import com.amazonaws.services.s3.model.S3Object;
import fr.gouv.culture.francetransfert.ClamAVScannerManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RedisQueueEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.RedisUtils;
import fr.gouv.culture.francetransfert.francetransfert_storage_api.StorageManager;
import fr.gouv.culture.francetransfert.model.Enclosure;
import fr.gouv.culture.francetransfert.security.WorkerException;
import fr.gouv.culture.francetransfert.services.cleanup.CleanUpServices;
import fr.gouv.culture.francetransfert.services.mail.notification.MailVirusFoundServices;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class ZipWorkerServices {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZipWorkerServices.class);

    @Autowired
    StorageManager manager;

    @Autowired
    RedisManager redisManager;

    @Autowired
    ClamAVScannerManager clamAVScannerManager;

    @Value("${tmp.folder.path}")
    private String tmpFolderPath;

    @Value("${bucket.prefix}")
    private String bucketPrefix;

    @Value("${scan.clamav.file.limitSize}")
    private long scanLimitSize;

    @Autowired
    MailVirusFoundServices mailVirusFoundServices;

    @Autowired
    CleanUpServices cleanUpServices;

    public void startZip(String prefix) throws Exception {
        manager.getZippedEnclosureName(prefix);
        String bucketName = RedisUtils.getBucketName(redisManager, prefix, bucketPrefix);
        ArrayList<String> list = manager.getUploadedEnclosureFiles(bucketName, prefix);
        LOGGER.info("================================> SIZE " + list.size() + " LIST ===> " + list.toString());
        try {
            Enclosure enclosure = Enclosure.build(prefix, redisManager);
            LOGGER.info("================================> start copy files temp to disk and scan for vulnerabilities {} / {} - {} ++ {} ", bucketName, list, prefix, bucketPrefix);
            boolean isClean = performScan(manager, bucketName, list);

            if (isClean) {

                LOGGER.info("================================> start zip files temp to disk");
                zipDownloadedContent(prefix);

                LOGGER.info("================================> start upload zip file temp to OSU");
                uploadZippedEnclosure(bucketName, manager, manager.getZippedEnclosureName(prefix) + ".zip", getBaseFolderNameWithZipPrefix(prefix));
                File fileToDelete = new File(getBaseFolderNameWithEnclosurePrefix(prefix));
                LOGGER.info("================================> start delete zip file in local disk");
                deleteFilesFromTemp(fileToDelete);
                File fileZip = new File(getBaseFolderNameWithZipPrefix(prefix));
                if (!fileZip.delete()) {
                    throw new WorkerException("error delete zip file");
                }
                LOGGER.info("================================> start delete zip file in OSU");
                deleteFilesFromOSU(manager, bucketName, prefix);
                notifyEmailWorker(prefix);
            } else {
                LOGGER.error("================================> Virus found in uploaded files And process clean up {} / {} - {} ++ {} ", bucketName, list, prefix, bucketPrefix);

                /** Clean : OSU, REDIS, UPLOADER FOLDER, and NOTIFY SNDER **/

                LOGGER.info("================================> clean up OSU");
                deleteFilesFromOSU(manager, bucketName, prefix);

                //clean temp data in REDIS for Enclosure
                LOGGER.info("================================> clean up REDIS temp data");
                cleanUpServices.cleanUpEnclosureTempDataInRedis(redisManager, prefix);

                // clean enclosure Core in REDIS : delete files, root-files, root-dirs, recipients, sender and enclosure
                LOGGER.info("================================> clean up REDIS");
                cleanUpServices.cleanUpEnclosureCoreInRedis(redisManager, prefix);

                // clean up for Upload directory
                cleanUpServices.deleteUploadDirectory(getBaseFolderNameWithEnclosurePrefix(prefix));

                //Notify sender
                mailVirusFoundServices.sendToSender(enclosure);

            }
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
        }
    }

    private void notifyEmailWorker(String prefix) throws Exception {
        redisManager.publishFT(RedisQueueEnum.MAIL_QUEUE.getValue(), prefix);
    }

    private void deleteFilesFromOSU(StorageManager manager, String bucketName, String prefix) throws Exception {
        manager.deleteFilesWithPrefix(bucketName, prefix);
    }

    private void deleteFilesFromTemp(File file) {
        for (File subFile : file.listFiles()) {
            if (subFile.isDirectory()) {
                deleteFilesFromTemp(subFile);
            } else {
                if (!subFile.delete()) {
                    LOGGER.error("unable to delete subfile");
                }
            }
        }
        if (!file.delete()) {
            LOGGER.error("unable to delete file");
        }
    }

    public void uploadZippedEnclosure(String bucketName, StorageManager manager, String fileName, String fileZipPath) throws Exception {
        manager.uploadMultipartForZip(bucketName, fileName, fileZipPath);
//		manager.createFile(bucketName, fileToUpload, fileName);
    }

    private void zipDownloadedContent(String zippedFileName) throws IOException {
        String sourceFile = getBaseFolderNameWithEnclosurePrefix(zippedFileName);
        try (FileOutputStream fos = new FileOutputStream(getBaseFolderNameWithZipPrefix(zippedFileName));
             ZipOutputStream zipOut = new ZipOutputStream(fos);) {
            File fileToZip = new File(sourceFile);
            for (File file : fileToZip.listFiles()) {
                zipFile(file, file.getName(), zipOut);
            }
//		zipFile(fileToZip, fileToZip.getName(), zipOut);
            zipOut.flush();
            zipOut.close();
            fos.flush();
            fos.close();
        }
    }

    private static void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
        FileInputStream fis = null;
        try {
            if (fileToZip.isDirectory()) {
                if (fileName.endsWith(File.separator)) {
                    zipOut.putNextEntry(new ZipEntry(fileName));
                    zipOut.closeEntry();
                } else {
                    zipOut.putNextEntry(new ZipEntry(fileName + File.separator));
                    zipOut.closeEntry();
                }
                File[] children = fileToZip.listFiles();
                for (File childFile : children) {
                    LOGGER.info("================================> start zip file {} temp to disk", childFile.getName());
                    zipFile(childFile, fileName + File.separator + childFile.getName(), zipOut);
                }
                return;
            }
            fis = new FileInputStream(fileToZip);
            ZipEntry zipEntry = new ZipEntry(fileName);
            zipOut.putNextEntry(zipEntry);
            byte[] bytes = new byte[1024];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zipOut.write(bytes, 0, length);
            }
        } catch (Exception e) {
            throw new WorkerException("Error During ZipFile");
        } finally {
            if (fis != null) {
                fis.close();
            }
        }
    }

    /**
     * Writing files into temp directory
     *
     * @param files
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    private void writeFile(Map<Path, InputStream> files) throws IOException {
        if (!CollectionUtils.isEmpty(files)) {

            for (Path pathKey : files.keySet()) {
                Files.createDirectories(pathKey.getParent().resolve(pathKey.getParent()));
                Files.write(pathKey, files.get(pathKey).readAllBytes());
            }

        }
    }

    private void downloadFilesToTempFolder(StorageManager manager, String bucketName, ArrayList<String> list) {
        try {
            for (String fileName : list) {
                S3Object object = manager.getObjectByName(bucketName, fileName);
                if (!fileName.endsWith(File.separator) && !fileName.endsWith("\\") && !fileName.endsWith("/")) {
                    writeFile(object, fileName);
                }
            }
        } catch (Exception e) {
            throw new WorkerException("Error During File Dowload from OSU to Temp Folder");
        }
    }

    /**
     * @param object
     * @param fileName
     * @throws IOException
     */
    public void writeFile(S3Object object, String fileName) throws IOException {
        LOGGER.info("================================> start download file : {}  to disk ", fileName);
        InputStream reader = new BufferedInputStream(object.getObjectContent());
        String baseFolderName = getBaseFolderName();
        File file = new File(baseFolderName + fileName);
        file.getParentFile().mkdirs();
        OutputStream writer = new BufferedOutputStream(new FileOutputStream(file));
        int read = -1;
        while ((read = reader.read()) != -1) {
            writer.write(read);
        }
        writer.flush();
        writer.close();
        reader.close();
    }

    /**
     * Writing files into temp directory and scanning for vulnerabilities
     *
     * @param manager
     * @param bucketName
     * @param list
     * @return
     */
    private boolean performScan(StorageManager manager, String bucketName, ArrayList<String> list) {
        boolean isClean = true;
        try {
            for (String fileName : list) {

                if (!isClean) {
                    break;
                }
                S3Object object = manager.getObjectByName(bucketName, fileName);
                if (!fileName.endsWith(File.separator) && !fileName.endsWith("\\") && !fileName.endsWith("/")) {

                    try (InputStream inputStream = new BufferedInputStream(object.getObjectContent());) {
                        String status = clamAVScannerManager.performScan(inputStream, fileName);
                        writeFile(inputStream, fileName);
                        if (!Objects.equals("OK", status)) {
                            isClean = false;
                        }
                    }
                }
            }

            if(isClean){
                downloadFilesToTempFolder(manager, bucketName, list);
            }
        } catch (Exception e) {
            throw new WorkerException("Error During File Dowload from OSU to Temp Folder And Security scan");
        }

        return isClean;
    }

    /**
     * @param inputStream
     * @param fileName
     * @throws IOException
     */
    public void writeFile(InputStream inputStream, String fileName) throws IOException {
        LOGGER.info("================================> start download file : {}  to disk ", fileName);
        String baseFolderName = getBaseFolderName();
        File file = new File(baseFolderName + fileName);
        file.getParentFile().mkdirs();
        try (OutputStream writer = new BufferedOutputStream(new FileOutputStream(file));) {
            int read = -1;
            while ((read = inputStream.read()) != -1) {
                writer.write(read);
            }
            writer.flush();
            writer.close();
            inputStream.close();
        }
    }
    private String getBaseFolderName() {
        String baseString = tmpFolderPath;
        return baseString;
    }

    private String getBaseFolderNameWithEnclosurePrefix(String prefix) {
        String baseString = tmpFolderPath + prefix;
        return baseString;
    }

    private String getBaseFolderNameWithZipPrefix(String zippedFileName) {
        String baseString = tmpFolderPath + zippedFileName + ".zip";
        return baseString;
    }
}
