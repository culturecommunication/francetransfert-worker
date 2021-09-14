package fr.gouv.culture.francetransfert.services.zipworker;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.amazonaws.services.s3.model.S3Object;

import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.EnclosureKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RedisQueueEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.RedisUtils;
import fr.gouv.culture.francetransfert.francetransfert_storage_api.StorageManager;
import fr.gouv.culture.francetransfert.model.Enclosure;
import fr.gouv.culture.francetransfert.security.WorkerException;
import fr.gouv.culture.francetransfert.services.clamav.ClamAVScannerManager;
import fr.gouv.culture.francetransfert.services.cleanup.CleanUpServices;
import fr.gouv.culture.francetransfert.services.mail.notification.MailVirusFoundServices;
import fr.gouv.culture.francetransfert.services.mail.notification.enums.NotificationTemplateEnum;
import fr.gouv.culture.francetransfert.utils.Base64CryptoService;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.io.outputstream.ZipOutputStream;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;

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

	@Value("${scan.clamav.maxFileSize}")
	private long scanMaxFileSize;

	@Value("${subject.virus.sender}")
	private String subjectVirusFound;

	@Value("${subject.virus.error.sender}")
	private String subjectVirusError;

	@Autowired
	MailVirusFoundServices mailVirusFoundServices;

	@Autowired
	CleanUpServices cleanUpServices;

	@Autowired
	Base64CryptoService base64CryptoService;

	public void startZip(String prefix) throws Exception {
		manager.getZippedEnclosureName(prefix);
		String bucketName = RedisUtils.getBucketName(redisManager, prefix, bucketPrefix);
		ArrayList<String> list = manager.getUploadedEnclosureFiles(bucketName, prefix);
		LOGGER.info(" STEP STATE ZIP ");
		LOGGER.info(" SIZE " + list.size() + " LIST ===> " + list.toString());
		try {
			Enclosure enclosure = Enclosure.build(prefix, redisManager);
			String passwordRedis = RedisUtils.getEnclosureValue(redisManager, enclosure.getGuid(),
					EnclosureKeysEnum.PASSWORD.getKey());
			String passwordUnHashed = base64CryptoService.aesDecrypt(passwordRedis);
			LOGGER.info(
					" start copy files temp to disk and scan for vulnerabilities {} / {} - {} ++ {} ",
					bucketName, list, prefix, bucketPrefix);
			downloadFilesToTempFolder(manager, bucketName, list);
			LOGGER.info(" Start scanning files {} with ClamaV", list);
			LocalDateTime beginDate = LocalDateTime.now();
			boolean isClean = performScan(list, bucketName, prefix, enclosure);
			if (!isClean) {
				LOGGER.error("Virus found in bucketName [{}] files {} ",
						bucketName, list);
			}
			LOGGER.info(" End scanning file {} with ClamaV. Duration(s) = [{}]", list,
					Duration.between(beginDate, LocalDateTime.now()).getSeconds());

			if (isClean) {

				LOGGER.info(" start zip files temp to disk");
				zipDownloadedContent(prefix, passwordUnHashed);

				LOGGER.info(" start upload zip file temp to OSU");
				uploadZippedEnclosure(bucketName, manager, manager.getZippedEnclosureName(prefix) + ".zip",
						getBaseFolderNameWithZipPrefix(prefix));
				File fileToDelete = new File(getBaseFolderNameWithEnclosurePrefix(prefix));
				LOGGER.info(" start delete zip file in local disk");
				deleteFilesFromTemp(fileToDelete);
				File fileZip = new File(getBaseFolderNameWithZipPrefix(prefix));
				if (!fileZip.delete()) {
					throw new WorkerException("error delete zip file");
				}
				LOGGER.info(" start delete zip file in OSU");
				deleteFilesFromOSU(manager, bucketName, prefix);
				notifyEmailWorker(prefix);
			} else {
				cleanUpEnclosure(bucketName, prefix, enclosure, NotificationTemplateEnum.MAIL_VIRUS_SENDER.getValue(),
						subjectVirusFound);
			}
			LOGGER.info(" STEP STATE ZIP OK");
		} catch (IOException e) {
			LOGGER.error(e.getMessage(), e);
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

	public void uploadZippedEnclosure(String bucketName, StorageManager manager, String fileName, String fileZipPath)
			throws Exception {
		manager.uploadMultipartForZip(bucketName, fileName, fileZipPath);
//		manager.createFile(bucketName, fileToUpload, fileName);
	}

	private void zipDownloadedContent(String zippedFileName, String password) throws IOException {
		String sourceFile = getBaseFolderNameWithEnclosurePrefix(zippedFileName);
		try (FileOutputStream fos = new FileOutputStream(getBaseFolderNameWithZipPrefix(zippedFileName));
				ZipOutputStream zipOut = new ZipOutputStream(fos, password.toCharArray());) {
			File fileToZip = new File(sourceFile);
			for (File file : fileToZip.listFiles()) {
				zipFile(file, file.getName(), zipOut);
			}
			zipOut.flush();
			fos.flush();
		}
	}

	private static void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
		FileInputStream fis = null;
		try {
			ZipParameters parameters = new ZipParameters();
			parameters.setCompressionMethod(CompressionMethod.DEFLATE);
			parameters.setCompressionLevel(CompressionLevel.NORMAL);
			parameters.setEncryptFiles(true);
			parameters.setEncryptionMethod(EncryptionMethod.AES);
			parameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
			parameters.setFileNameInZip(fileName);
			if (fileToZip.isDirectory()) {
				if (fileName.endsWith(File.separator)) {
					zipOut.putNextEntry(parameters);
					zipOut.closeEntry();
				} else {
					parameters.setFileNameInZip(fileName + File.separator);
					zipOut.putNextEntry(parameters);
					zipOut.closeEntry();
				}
				File[] children = fileToZip.listFiles();
				for (File childFile : children) {
					LOGGER.info(" start zip file {} temp to disk",
							childFile.getName());
					zipFile(childFile, fileName + File.separator + childFile.getName(), zipOut);
				}
				return;
			}
			fis = new FileInputStream(fileToZip);
			zipOut.putNextEntry(parameters);
			byte[] bytes = new byte[1024];
			int length;
			while ((length = fis.read(bytes)) >= 0) {
				zipOut.write(bytes, 0, length);
			}
			zipOut.closeEntry();
		} catch (Exception e) {
			log.error("Error During ZipFile", e);
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
			LOGGER.error("Error During File Dowload from OSU to Temp Folder : " + e.getMessage(), e);
			throw new WorkerException("Error During File Dowload from OSU to Temp Folder ");
		}
	}

	/**
	 * @param object
	 * @param fileName
	 * @throws IOException
	 */
	public void writeFile(S3Object object, String fileName) throws IOException {
		LOGGER.info(" start download file : {}  to disk ", fileName);
		try (InputStream reader = new BufferedInputStream(object.getObjectContent());) {
			String baseFolderName = getBaseFolderName();
			File file = new File(baseFolderName + fileName);
			file.getParentFile().mkdirs();
			try (OutputStream writer = new BufferedOutputStream(new FileOutputStream(file));) {
				int read = -1;
				while ((read = reader.read()) != -1) {
					writer.write(read);
				}
				writer.flush();
			}
		}
	}

	/**
	 * Writing files into temp directory and scanning for vulnerabilities
	 *
	 * @param list
	 * @param bucketName
	 * @param prefix
	 * @param enclosure
	 * @return
	 */
	private boolean performScan(ArrayList<String> list, String bucketName, String prefix, Enclosure enclosure) {
		boolean isClean = true;
		String currentFileName = null;
		try {
			for (String fileName : list) {

				if (!isClean) {
					break;
				}
				currentFileName = fileName;
				if (!fileName.endsWith(File.separator) && !fileName.endsWith("\\") && !fileName.endsWith("/")) {
					String baseFolderName = getBaseFolderName();
					try (FileInputStream fileInputStream = new FileInputStream(baseFolderName + fileName);) {

						FileChannel fileChannel = fileInputStream.getChannel();
						if (fileChannel.size() <= scanMaxFileSize) {
							String status = clamAVScannerManager.performScan(fileChannel);
							if (!Objects.equals("OK", status)) {
								isClean = false;
							}
						}
					}
				}
			}
		} catch (Exception e) {
			cleanUpEnclosure(bucketName, prefix, enclosure, NotificationTemplateEnum.MAIL_VIRUS_ERROR_SENDER.getValue(),
					subjectVirusError);
			LOGGER.error("Error lors du traitement du fichier {} : {}  ", currentFileName, e.getMessage(), e);
			throw new WorkerException("Error During File scanning [" + currentFileName + "]");
		}

		return isClean;
	}

	/**
	 *
	 * @param bucketName
	 * @param prefix
	 */
	private void cleanUpEnclosure(String bucketName, String prefix, Enclosure enclosure, String emailTemplateName,
			String emailSubject) {
		try {
			/** Clean : OSU, REDIS, UPLOADER FOLDER, and NOTIFY SNDER **/
			LOGGER.info(" Processing clean up {} / {} - {} ", bucketName, prefix,
					bucketPrefix);
			LOGGER.info(" clean up OSU");
			deleteFilesFromOSU(manager, bucketName, prefix);

			// clean temp data in REDIS for Enclosure
			LOGGER.info(" clean up REDIS temp data");
			cleanUpServices.cleanUpEnclosureTempDataInRedis(redisManager, prefix);

			// clean enclosure Core in REDIS : delete files, root-files, root-dirs,
			// recipients, sender and enclosure
			LOGGER.info(" clean up REDIS");
			cleanUpServices.cleanUpEnclosureCoreInRedis(redisManager, prefix);

			// clean up for Upload directory
			cleanUpServices.deleteEnclosureTempDirectory(getBaseFolderNameWithEnclosurePrefix(prefix));
			// Notify sender
			mailVirusFoundServices.sendToSender(enclosure, emailTemplateName, emailSubject);
		} catch (Exception e) {
			LOGGER.error("Error while cleaning up Enclosure : "+ e.getMessage(), e);
		}
	}

	/**
	 * @param inputStream
	 * @param fileName
	 * @throws IOException
	 */
	public void writeFile(InputStream inputStream, String fileName) throws IOException {
		LOGGER.info(" start download file : {}  to disk ", fileName);
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
