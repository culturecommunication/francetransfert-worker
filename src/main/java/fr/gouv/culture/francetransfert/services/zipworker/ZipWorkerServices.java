/*
  * Copyright (c) Ministère de la Culture (2022) 
  * 
  * SPDX-License-Identifier: Apache-2.0 
  * License-Filename: LICENSE.txt 
  */

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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.amazonaws.services.s3.model.S3Object;

import fr.gouv.culture.francetransfert.core.enums.EnclosureKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.RedisKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.RedisQueueEnum;
import fr.gouv.culture.francetransfert.core.enums.StatutEnum;
import fr.gouv.culture.francetransfert.core.enums.TypeStat;
import fr.gouv.culture.francetransfert.core.exception.MetaloadException;
import fr.gouv.culture.francetransfert.core.exception.StorageException;
import fr.gouv.culture.francetransfert.core.services.MimeService;
import fr.gouv.culture.francetransfert.core.services.RedisManager;
import fr.gouv.culture.francetransfert.core.services.StorageManager;
import fr.gouv.culture.francetransfert.core.utils.Base64CryptoService;
import fr.gouv.culture.francetransfert.core.utils.RedisUtils;
import fr.gouv.culture.francetransfert.exception.InvalidSizeTypeException;
import fr.gouv.culture.francetransfert.model.Enclosure;
import fr.gouv.culture.francetransfert.model.GlimpsInitResponse;
import fr.gouv.culture.francetransfert.model.GlimpsResultResponse;
import fr.gouv.culture.francetransfert.security.WorkerException;
import fr.gouv.culture.francetransfert.services.clamav.ClamAVScannerManager;
import fr.gouv.culture.francetransfert.services.cleanup.CleanUpServices;
import fr.gouv.culture.francetransfert.services.mail.notification.MailNotificationServices;
import fr.gouv.culture.francetransfert.services.mail.notification.enums.NotificationTemplateEnum;
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
	MimeService mimeService;

	@Autowired
	RedisManager redisManager;

	@Autowired
	StorageManager storageManager;

	@Autowired
	ClamAVScannerManager clamAVScannerManager;

	@Autowired
	private RestTemplate restTemplate;

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

	@Value("${subject.virus.senderEn}")
	private String subjectVirusFoundEn;

	@Value("${subject.virus.error.senderEn}")
	private String subjectVirusErrorEn;

	@Value("${upload.limit}")
	private long maxEnclosureSize;

	@Value("${upload.file.limit}")
	private long maxFileSize;

	@Value("${glimps.scan.url}")
	private String glimpsScanUrl;

	@Value("${glimps.check.url}")
	private String glimpsCheckUrl;

	@Value("${glimps.auth.token.key}")
	private String glimpsTokenKey;

	@Value("${glimps.auth.token.value}")
	private String glimpsTokenValue;

	@Value("${glimps.delay.minute}")
	private int glimpsDelay;

	@Value("${glimps.enabled}")
	private boolean glimpsEnabled;

	@Autowired
	MailNotificationServices mailNotificationService;

	@Autowired
	CleanUpServices cleanUpServices;

	@Autowired
	Base64CryptoService base64CryptoService;

	public void startZip(String enclosureId) throws MetaloadException, StorageException {
		String bucketName = RedisUtils.getBucketName(redisManager, enclosureId, bucketPrefix);
		ArrayList<String> list = manager.getUploadedEnclosureFiles(bucketName, enclosureId);
		LOGGER.debug(" STEP STATE ZIP ");
		LOGGER.debug(" SIZE " + list.size() + " LIST ===> " + list.toString());
		Enclosure enclosure = Enclosure.build(enclosureId, redisManager);

		try {

			boolean finishedScan = false;
			boolean isClean = false;

			String encStatut = enclosure.getStatut();

			if (StatutEnum.CHT.getCode().equals(encStatut)) {

				LOGGER.info("[Worker] Start scan process for enclosur N°  {}", enclosureId);

				redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosure.getGuid()),
						EnclosureKeysEnum.STATUS_CODE.getKey(), StatutEnum.ANA.getCode(), -1);
				redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosure.getGuid()),
						EnclosureKeysEnum.STATUS_WORD.getKey(), StatutEnum.ANA.getWord(), -1);

				LOGGER.info(" start copy files temp to disk and scan for vulnerabilities {} / {} - {} ++ {} ",
						bucketName, list, enclosureId, bucketPrefix);

				downloadFilesToTempFolder(manager, bucketName, list);
				sizeCheck(list);

				if (glimpsEnabled) {
					sendToGlipms(list, enclosureId);
					File fileToDelete = new File(getBaseFolderNameWithEnclosurePrefix(enclosureId));
					deleteFilesFromTemp(fileToDelete);
				} else {
					isClean = performScan(list);
					finishedScan = true;
				}

			} else if (StatutEnum.ANA.getCode().equals(encStatut)) {
				String lastGlimpsCheckStr = redisManager
						.getString(RedisKeysEnum.FT_ENCLOSURE_SCAN_DELAY.getKey(enclosureId));
				if (StringUtils.isBlank(lastGlimpsCheckStr) || LocalDateTime.parse(lastGlimpsCheckStr)
						.plusMinutes(glimpsDelay).isBefore(LocalDateTime.now())) {
					LOGGER.info("Checking glimps for enclosure {}", enclosureId);
					isClean = checkGlipms(enclosureId);
					if (!isClean) {
						redisManager.deleteKey(RedisKeysEnum.FT_ENCLOSURE_SCAN.getKey(enclosureId));
					}
					if (CollectionUtils.isEmpty(
							redisManager.smembersString(RedisKeysEnum.FT_ENCLOSURE_SCAN.getKey(enclosureId)))) {
						finishedScan = true;
					}
					redisManager.setString(RedisKeysEnum.FT_ENCLOSURE_SCAN_DELAY.getKey(enclosureId),
							LocalDateTime.now().toString());
				} else {
					LOGGER.debug("Waiting before next call");
					isClean = true;
					finishedScan = false;
				}
			}

			if (isClean && finishedScan) {
				LOGGER.info("Finished scan start zipping for enclosure {}", enclosureId);

				if (glimpsEnabled) {
					downloadFilesToTempFolder(manager, bucketName, list);
				}

				String passwordRedis = RedisUtils.getEnclosureValue(redisManager, enclosure.getGuid(),
						EnclosureKeysEnum.PASSWORD.getKey());

				String zipPassword = RedisUtils.getEnclosureValue(redisManager, enclosure.getGuid(),
						EnclosureKeysEnum.PASSWORD_ZIP.getKey());

				String passwordUnHashed = base64CryptoService.aesDecrypt(passwordRedis);

				LOGGER.debug(" start zip files temp to disk");
				zipDownloadedContent(enclosureId, passwordUnHashed, zipPassword);

				LOGGER.debug(" start upload zip file temp to OSU");
				uploadZippedEnclosure(bucketName, manager, manager.getZippedEnclosureName(enclosureId),
						getBaseFolderNameWithZipPrefix(enclosureId));

				LOGGER.debug(" add hashZipFile to redis");
				addHashFilesToMetData(enclosureId, getHashFromS3(enclosureId));

				File fileToDelete = new File(getBaseFolderNameWithEnclosurePrefix(enclosureId));
				LOGGER.debug(" start delete zip file in local disk");
				deleteFilesFromTemp(fileToDelete);
				File fileZip = new File(getBaseFolderNameWithZipPrefix(enclosureId));
				if (!fileZip.delete()) {
					throw new WorkerException("error delete zip file");
				}
				LOGGER.debug(" start delete zip file in OSU");
				deleteFilesFromOSU(manager, bucketName, enclosureId);
				notifyEmailWorker(enclosureId);
				RedisUtils.updateListOfPliSent(redisManager, enclosure.getSender(), enclosureId);
				if (!CollectionUtils.isEmpty(enclosure.getRecipients())) {
					RedisUtils.updateListOfPliReceived(redisManager,
							enclosure.getRecipients().stream().map(x -> x.getMail()).collect(Collectors.toList()),
							enclosureId);
				}

				String statMessage = TypeStat.UPLOAD + ";" + enclosureId;
				redisManager.publishFT(RedisQueueEnum.STAT_QUEUE.getValue(), statMessage);

				redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosure.getGuid()),
						EnclosureKeysEnum.STATUS_CODE.getKey(), StatutEnum.APT.getCode(), -1);
				redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosure.getGuid()),
						EnclosureKeysEnum.STATUS_WORD.getKey(), StatutEnum.APT.getWord(), -1);
				LOGGER.debug(" STEP STATE ZIP OK");

			} else if (!isClean && finishedScan) {

				LOGGER.error("Virus found in bucketName [{}] files {} ", bucketName, list);
				LOGGER.warn("msgtype: VIRUS || enclosure: {} || sender: {}", enclosure.getGuid(),
						enclosure.getSender());

				redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosure.getGuid()),
						EnclosureKeysEnum.STATUS_CODE.getKey(), StatutEnum.EAV.getCode(), -1);
				redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosure.getGuid()),
						EnclosureKeysEnum.STATUS_WORD.getKey(), StatutEnum.EAV.getWord(), -1);

				cleanUpEnclosure(bucketName, enclosureId, enclosure,
						NotificationTemplateEnum.MAIL_VIRUS_SENDER.getValue(), subjectVirusFound);
			} else if (!finishedScan) {
				LOGGER.debug("Scan in progress for enclosure {}", enclosureId);
				redisManager.publishFT(RedisQueueEnum.ZIP_QUEUE.getValue(), enclosure.getGuid());
			}

		} catch (InvalidSizeTypeException sizeEx) {
			LOGGER.error("Enclosure " + enclosure.getGuid() + " as invalid type or size : " + sizeEx);
			cleanUpEnclosure(bucketName, enclosureId, enclosure,
					NotificationTemplateEnum.MAIL_INVALID_ENCLOSURE_SENDER.getValue(), subjectVirusError);
		} catch (Exception e) {
			LOGGER.error("Error in zip process : " + e.getMessage(), e);
			cleanUpEnclosure(bucketName, enclosureId, enclosure,
					NotificationTemplateEnum.MAIL_VIRUS_ERROR_SENDER.getValue(), subjectVirusError);
		}
	}

	private boolean checkGlipms(String enclosureId) {

		HttpHeaders headers = new HttpHeaders();
		headers.set(glimpsTokenKey, glimpsTokenValue);
		boolean isClean = true;
		Set<String> scanUuidList = redisManager.smembersString(RedisKeysEnum.FT_ENCLOSURE_SCAN.getKey(enclosureId));
		isClean = scanUuidList.stream().map(uuid -> {
			HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
			GlimpsResultResponse ret = restTemplate
					.exchange(glimpsCheckUrl + uuid, HttpMethod.GET, requestEntity, GlimpsResultResponse.class)
					.getBody();
			if (ret.isDone()) {
				redisManager.srem(RedisKeysEnum.FT_ENCLOSURE_SCAN.getKey(enclosureId), uuid);
				if (ret.is_malware()) {
					LOGGER.error("Virus found in file {}", uuid);
					return false;
				}
				if (StringUtils.isNotBlank(ret.getError()) || !ret.isStatus()) {
					LOGGER.error("Error while scanning file {}", uuid);
					return false;
				}
			}
			return true;
		}).allMatch(x -> (x == true));

		return isClean;
	}

	private void sendToGlipms(ArrayList<String> list, String enclosureId) {

		HttpHeaders headers = new HttpHeaders();
		headers.set(glimpsTokenKey, glimpsTokenValue);
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);

		try {
			for (String fileName : list) {
				if (!fileName.endsWith(File.separator) && !fileName.endsWith("\\") && !fileName.endsWith("/")) {
					String baseFolderName = getBaseFolderName();
					File file = new File(baseFolderName + fileName);
					MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
					body.add("file", new FileSystemResource(file));
					HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
					ResponseEntity<GlimpsInitResponse> initResponse = restTemplate.exchange(glimpsScanUrl,
							HttpMethod.POST, requestEntity, GlimpsInitResponse.class);
					redisManager.saddString(RedisKeysEnum.FT_ENCLOSURE_SCAN.getKey(enclosureId),
							initResponse.getBody().getUuid());
				}
			}
		} catch (Exception e) {
			LOGGER.error("Error while sending to glimps ", e);
			throw new WorkerException("Error while sending to glimps enclosure :" + enclosureId, e);
		}

	}

	private String getHashFromS3(String enclosureId) throws MetaloadException, StorageException {
		String bucketName = RedisUtils.getBucketName(redisManager, enclosureId, bucketPrefix);
		String fileToDownload = storageManager.getZippedEnclosureName(enclosureId);
		String hashFileFromS3 = storageManager.getEtag(bucketName, fileToDownload);
		return hashFileFromS3;
	}

	/*
	 * private void getContentMd5ForRedis(String prefix) throws IOException { File
	 * fileZip = new File(getBaseFolderNameWithZipPrefix(prefix)); FileInputStream
	 * fis = new FileInputStream(fileZip); byte[] content_bytes =
	 * IOUtils.toByteArray(fis); String md5 = new
	 * String(DigestUtils.md5Hex(content_bytes)); addHashFilesToMetData(prefix,md5);
	 * fis.close(); }
	 */

	public void addHashFilesToMetData(String enclosureId, String hashFile) {
		try {
			Map<String, String> tokenMap = redisManager
					.hmgetAllString(RedisKeysEnum.FT_ADMIN_TOKEN.getKey(enclosureId));
			if (tokenMap != null) {
				Map<String, String> enclosureMap = redisManager
						.hmgetAllString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId));
				enclosureMap.put(EnclosureKeysEnum.HASH_FILE.getKey(), hashFile);
				redisManager.insertHASH(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId), enclosureMap);
			} else {
				throw new WorkerException("tokenMap from Redis is null");
			}
		} catch (Exception e) {
			throw new WorkerException("Unable to add hashFile to redis");
		}
	}

	private void notifyEmailWorker(String prefix) {
		redisManager.publishFT(RedisQueueEnum.MAIL_QUEUE.getValue(), prefix);
	}

	private void deleteFilesFromOSU(StorageManager manager, String bucketName, String prefix) throws StorageException {
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
			throws StorageException {
		manager.uploadMultipartForZip(bucketName, fileName, fileZipPath);
	}

	private void zipDownloadedContent(String zippedFileName, String password, String zipPassword) throws IOException {

		if (zipPassword.equalsIgnoreCase("true")) {
			String sourceFile = getBaseFolderNameWithEnclosurePrefix(zippedFileName);
			try (FileOutputStream fos = new FileOutputStream(getBaseFolderNameWithZipPrefix(zippedFileName));
					ZipOutputStream zipOut = new ZipOutputStream(fos, password.toCharArray());) {
				File fileToZip = new File(sourceFile);
				for (File file : fileToZip.listFiles()) {
					zipFile(file, file.getName(), zipOut, true);
				}
				zipOut.flush();
				fos.flush();
			}
		} else {
			zipDownloadedContentWithoutPassword(zippedFileName);
		}

	}

	private void zipDownloadedContentWithoutPassword(String zippedFileName) throws IOException {
		String sourceFile = getBaseFolderNameWithEnclosurePrefix(zippedFileName);
		try (FileOutputStream fos = new FileOutputStream(getBaseFolderNameWithZipPrefix(zippedFileName));
				ZipOutputStream zipOut = new ZipOutputStream(fos);) {
			File fileToZip = new File(sourceFile);
			for (File file : fileToZip.listFiles()) {
				zipFile(file, file.getName(), zipOut, false);
			}
			zipOut.flush();
			fos.flush();
		}
	}

	private static void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut, boolean crypted)
			throws IOException {
		try {
			ZipParameters parameters = new ZipParameters();
			parameters.setCompressionMethod(CompressionMethod.DEFLATE);
			parameters.setCompressionLevel(CompressionLevel.NORMAL);
			if (crypted) {
				parameters.setEncryptFiles(true);
				parameters.setEncryptionMethod(EncryptionMethod.AES);
				parameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
			} else {
				parameters.setEncryptFiles(false);
			}
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
					LOGGER.info(" start zip file {} temp to disk", childFile.getName());
					zipFile(childFile, fileName + File.separator + childFile.getName(), zipOut, crypted);
				}
				return;
			}
			try (FileInputStream fis = new FileInputStream(fileToZip)) {
				zipOut.putNextEntry(parameters);
				byte[] bytes = new byte[1024];
				int length;
				while ((length = fis.read(bytes)) >= 0) {
					zipOut.write(bytes, 0, length);
				}
				zipOut.closeEntry();
			}

		} catch (Exception e) {
			log.error("Error During ZipFile", e);
			throw new WorkerException("Error During ZipFile");
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
	 * @return
	 * @throws InvalidSizeTypeException
	 */
	private void sizeCheck(ArrayList<String> list) throws InvalidSizeTypeException {
		String currentFileName = null;
		long enclosureSize = 0;
		try {
			for (String fileName : list) {
				long currentSize = 0;
				currentFileName = fileName;
				if (!fileName.endsWith(File.separator) && !fileName.endsWith("\\") && !fileName.endsWith("/")) {
					String baseFolderName = getBaseFolderName();
					try (FileInputStream fileInputStream = new FileInputStream(baseFolderName + fileName);) {

						currentSize = fileInputStream.getChannel().size();

						enclosureSize += currentSize;

						checkSizeAndMimeType(currentFileName, enclosureSize, currentSize, fileInputStream);
					}
				}
			}
		} catch (InvalidSizeTypeException ex) {
			throw ex;
		} catch (Exception e) {
			LOGGER.error("Error lors du traitement du fichier {} : {}  ", currentFileName, e.getMessage(), e);
			throw new WorkerException("Error During File size check [" + currentFileName + "]");
		}
	}

	private boolean performScan(ArrayList<String> list) throws InvalidSizeTypeException {
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
							if (!StringUtils.equalsIgnoreCase("OK", status)) {
								isClean = false;
							}
						}
					}
				}
			}
		} catch (Exception e) {
			LOGGER.error("Error lors du traitement du fichier {} : {}  ", currentFileName, e.getMessage(), e);
			throw new WorkerException("Error During File scanning [" + currentFileName + "]");
		}

		return isClean;
	}

	private void checkSizeAndMimeType(String currentFileName, long enclosureSize, long currentSize,
			FileInputStream fileInputStream) throws IOException, InvalidSizeTypeException {
		if (!mimeService.isAuthorisedMimeTypeFromFile(fileInputStream)) {
			String mimetype = mimeService.getMimeTypeFromFile(fileInputStream);
			throw new InvalidSizeTypeException("File " + currentFileName + " as invalid mimetype : " + mimetype);
		}

		if (currentSize > maxFileSize || enclosureSize > maxEnclosureSize) {
			throw new InvalidSizeTypeException("File " + currentFileName + " or enclose is too big");
		}
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
			LOGGER.info("Processing clean up for enclosure{} - {} / {} - {} ", enclosure.getGuid(), bucketName, prefix,
					bucketPrefix);
			LOGGER.debug("clean up OSU");
			deleteFilesFromOSU(manager, bucketName, prefix);

			// clean temp data in REDIS for Enclosure
			LOGGER.debug("clean up REDIS temp data");
			cleanUpServices.cleanUpEnclosureTempDataInRedis(prefix, true);

			// clean enclosure Core in REDIS : delete files, root-files, root-dirs,
			// recipients, sender and enclosure
			LOGGER.debug("clean up REDIS");
			cleanUpServices.cleanUpEnclosureCoreInRedis(prefix);

			// clean up for Upload directory
			cleanUpServices.deleteEnclosureTempDirectory(getBaseFolderNameWithEnclosurePrefix(prefix));
		} catch (Exception e) {
			LOGGER.error("Error while cleaning up Enclosure " + enclosure.getGuid() + " : " + e.getMessage(), e);
		} finally {
			try {

				// Notify sender

				Locale language;
				language = LocaleUtils.toLocale(RedisUtils.getEnclosureValue(redisManager, enclosure.getGuid(),
						EnclosureKeysEnum.LANGUAGE.getKey()));
				if (emailSubject == subjectVirusFound) {
					if (Locale.UK.equals(language)) {
						emailSubject = subjectVirusFoundEn;
					}

				} else if (emailSubject == subjectVirusError) {
					if (Locale.UK.equals(language)) {
						emailSubject = subjectVirusErrorEn;
					}
				}

				if (StringUtils.isNotBlank(enclosure.getSubject())) {
					emailSubject = emailSubject.concat(" : ").concat(enclosure.getSubject());
				}

				mailNotificationService.prepareAndSend(enclosure.getSender(), emailSubject, enclosure,
						emailTemplateName, language);
			} catch (MetaloadException e) {
				LOGGER.error("Error while sending mail for Enclosure " + enclosure.getGuid() + " : " + e.getMessage(),
						e);
			}
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
