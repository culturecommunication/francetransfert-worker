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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.LocaleUtils;
import org.joda.time.LocalDateTime;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.amazonaws.services.s3.model.S3Object;
import com.google.gson.Gson;

import fr.gouv.culture.francetransfert.core.enums.EnclosureKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.RedisKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.RedisQueueEnum;
import fr.gouv.culture.francetransfert.core.enums.SourceEnum;
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
import fr.gouv.culture.francetransfert.model.ScanInfo;
import fr.gouv.culture.francetransfert.security.GlimpsException;
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

	@Value("${glimps.delay.seconds:120}")
	private int glimpsDelay;

	@Value("${glimps.enabled:false}")
	private boolean glimpsEnabled;

	@Value("${glimps.knownCode}")
	private List<String> knownCode;

	@Value("${glimps.maxTry}")
	private Long glipmsMaxTry;

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
					encStatut = StatutEnum.ANA.getCode();
				} else {
					isClean = performScan(list, enclosureId);
					finishedScan = true;
				}
			}

			if (StatutEnum.ANA.getCode().equals(encStatut)) {
				String lastGlimpsCheckStr = redisManager
						.getString(RedisKeysEnum.FT_ENCLOSURE_SCAN_DELAY.getKey(enclosureId));
				if (StringUtils.isBlank(lastGlimpsCheckStr) || LocalDateTime.parse(lastGlimpsCheckStr)
						.plusSeconds(glimpsDelay).isBefore(LocalDateTime.now())) {
					LOGGER.info("Checking glimps for enclosure {}", enclosureId);
					isClean = checkGlipms(enclosureId);
					if (!isClean) {
						Map<String, String> scanJsonList = redisManager
								.hmgetAllString(RedisKeysEnum.FT_ENCLOSURE_VIRUS.getKey(enclosureId));
						boolean toStop = scanJsonList.values().stream().map(json -> {
							return new Gson().fromJson(json, ScanInfo.class);
						}).anyMatch(x -> x.isError() || x.isVirus());
						Long tryCount = redisManager.incrBy(RedisKeysEnum.FT_ENCLOSURE_SCAN_RETRY.getKey(enclosureId),
								1);
						if (toStop || (tryCount >= glipmsMaxTry)) {
							redisManager.deleteKey(RedisKeysEnum.FT_ENCLOSURE_SCAN.getKey(enclosureId));
						} else {
							LOGGER.info("Retry error waiting before next call");
							isClean = true;
							finishedScan = false;
						}
					}
					if (CollectionUtils.isEmpty(redisManager
							.hmgetAllString(RedisKeysEnum.FT_ENCLOSURE_SCAN.getKey(enclosureId)).values())) {
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

				if (StatutEnum.ANA.getCode().equals(encStatut)) {
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

				LocalDateTime depotDate = LocalDateTime.now();
				LOGGER.debug("enclosure update depotDate : {}", depotDate);
				redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosure.getGuid()),
						EnclosureKeysEnum.TIMESTAMP.getKey(), depotDate.toString(), -1);

				LOGGER.debug(" STEP STATE ZIP OK");

			} else if (!isClean && finishedScan) {

				redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosure.getGuid()),
						EnclosureKeysEnum.STATUS_CODE.getKey(), StatutEnum.EAV.getCode(), -1);
				redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosure.getGuid()),
						EnclosureKeysEnum.STATUS_WORD.getKey(), StatutEnum.EAV.getWord(), -1);
				Map<String, String> scanJsonList = redisManager
						.hmgetAllString(RedisKeysEnum.FT_ENCLOSURE_VIRUS.getKey(enclosureId));
				List<ScanInfo> scanList = scanJsonList.values().stream().map(json -> {
					return new Gson().fromJson(json, ScanInfo.class);
				}).collect(Collectors.toList());
				enclosure.getVirusScan().clear();
				enclosure.getVirusScan().addAll(scanList);
				enclosure.getVirusScan().stream().findFirst().ifPresentOrElse(x -> {
					enclosure.setErrorCode(x.getErrorCode());
				}, () -> {
					enclosure.setErrorCode("unknown");
				});
				if (scanList.stream().anyMatch(ScanInfo::isFatalError)) {
					cleanUpEnclosure(bucketName, enclosureId, enclosure,
							NotificationTemplateEnum.MAIL_VIRUS_ERROR_SENDER.getValue(), subjectVirusError);
				} else if (scanList.stream().anyMatch(ScanInfo::isVirus)) {
					LOGGER.warn("msgtype: VIRUS || enclosure: {} || sender: {}", enclosure.getGuid(),
							enclosure.getSender());
					cleanUpEnclosure(bucketName, enclosureId, enclosure,
							NotificationTemplateEnum.MAIL_VIRUS_SENDER.getValue(), subjectVirusFound);
				} else {
					cleanUpEnclosure(bucketName, enclosureId, enclosure,
							NotificationTemplateEnum.MAIL_VIRUS_INDISP_SENDER.getValue(), subjectVirusError);
				}
			} else if (!finishedScan) {
				LOGGER.debug("Scan in progress for enclosure {}", enclosureId);
				redisManager.publishFT(RedisQueueEnum.ZIP_QUEUE.getValue(), enclosure.getGuid());
			}
		} catch (InvalidSizeTypeException sizeEx) {
			enclosure.setFileError(sizeEx.getFile());
			redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosure.getGuid()),
					EnclosureKeysEnum.STATUS_CODE.getKey(), StatutEnum.ETF.getCode(), -1);
			redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosure.getGuid()),
					EnclosureKeysEnum.STATUS_WORD.getKey(), StatutEnum.ETF.getWord(), -1);
			LOGGER.error("Enclosure " + enclosure.getGuid() + " as invalid type or size : ", sizeEx);
			cleanUpEnclosure(bucketName, enclosureId, enclosure,
					NotificationTemplateEnum.MAIL_INVALID_ENCLOSURE_SENDER.getValue(), subjectVirusError);
		} catch (GlimpsException exGlimps) {
			redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosure.getGuid()),
					EnclosureKeysEnum.STATUS_CODE.getKey(), StatutEnum.EAV.getCode(), -1);
			redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosure.getGuid()),
					EnclosureKeysEnum.STATUS_WORD.getKey(), StatutEnum.EAV.getWord(), -1);
			cleanUpEnclosure(bucketName, enclosureId, enclosure,
					NotificationTemplateEnum.MAIL_VIRUS_INDISP_SENDER.getValue(), subjectVirusError);
		} catch (Exception e) {
			redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosure.getGuid()),
					EnclosureKeysEnum.STATUS_CODE.getKey(), StatutEnum.EAV.getCode(), -1);
			redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosure.getGuid()),
					EnclosureKeysEnum.STATUS_WORD.getKey(), StatutEnum.EAV.getWord(), -1);
			LOGGER.error("Error in zip process : " + e.getMessage(), e);
			Map<String, String> scanJsonList = redisManager
					.hmgetAllString(RedisKeysEnum.FT_ENCLOSURE_VIRUS.getKey(enclosureId));
			List<ScanInfo> scanList = scanJsonList.values().stream().map(json -> {
				return new Gson().fromJson(json, ScanInfo.class);
			}).collect(Collectors.toList());
			enclosure.getVirusScan().clear();
			enclosure.getVirusScan().addAll(scanList);
			enclosure.getVirusScan().stream().findFirst().ifPresentOrElse(x -> {
				enclosure.setErrorCode(x.getErrorCode());
			}, () -> {
				enclosure.setErrorCode("unknown");
			});
			cleanUpEnclosure(bucketName, enclosureId, enclosure, NotificationTemplateEnum.MAIL_ERROR_SENDER.getValue(),
					subjectVirusError);
		}
	}

	/**
	 * Check all remaining file from enclosure to glimps
	 * 
	 * @param enclosureId enclosureId
	 * @return
	 */
	private boolean checkGlipms(String enclosureId) {

		boolean isClean = true;
		Map<String, String> scanJsonList = redisManager
				.hmgetAllString(RedisKeysEnum.FT_ENCLOSURE_SCAN.getKey(enclosureId));
		isClean = scanJsonList.values().stream().map(glimpsJson -> glimpsUnitCheck(enclosureId, glimpsJson))
				.allMatch(x -> x);

		return isClean;
	}

	/**
	 * Check file uuid from glimps
	 * 
	 * @param enclosureId enclosureId
	 * @param glimpsJson  Glimps Json string from redis
	 * @return true if file is clean or unchecked, false if error or virus
	 */
	private boolean glimpsUnitCheck(String enclosureId, String glimpsJson) {

		HttpHeaders headers = new HttpHeaders();
		headers.set(glimpsTokenKey, glimpsTokenValue);
		ScanInfo glimps = new Gson().fromJson(glimpsJson, ScanInfo.class);
		LOGGER.debug("Checking Glimps check for enclosure {} and uuid {}", enclosureId, glimps.getUuid());
		LOGGER.debug("Glimps url : {}", glimpsCheckUrl + glimps.getUuid());
		HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
		ResponseEntity<GlimpsResultResponse> templateReturn = restTemplate.exchange(glimpsCheckUrl + glimps.getUuid(),
				HttpMethod.GET, requestEntity, GlimpsResultResponse.class);
		GlimpsResultResponse ret = templateReturn.getBody();

		if (ret.isDone() || StringUtils.isNotBlank(ret.getErrorCode())) {
			LOGGER.debug("Glimps scan done for enclosure {} - glimpsId {} - filename {}", enclosureId, glimps.getUuid(),
					glimps.getFilename());
			if (StringUtils.isNotBlank(ret.getErrorCode()) || !ret.isStatus()) {
				LOGGER.error("Error while scanning enclosure {} file {} / {}, Body : {}", enclosureId, glimps.getUuid(),
						glimps.getFilename(), templateReturn.getBody());
				boolean fatalError = false;
				fatalError = knownCode.contains(ret.getErrorCode());
				glimps.setError(true);
				glimps.setFatalError(fatalError);
				if (!knownCode.contains(ret.getErrorCode())) {
					glimps.setErrorCode("unknown");
				} else {
					glimps.setErrorCode(ret.getErrorCode());
				}
				String jsonInString = new Gson().toJson(glimps);
				redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE_VIRUS.getKey(enclosureId), glimps.getUuid(),
						jsonInString, -1);
				redisManager.hdel(RedisKeysEnum.FT_ENCLOSURE_SCAN.getKey(enclosureId), glimps.getUuid());
				return false;
			} else if (ret.isMalware()) {
				LOGGER.error("Virus found for enclosure {} in file {} / {}", enclosureId, glimps.getUuid(),
						glimps.getFilename());
				glimps.setVirus(true);
				String jsonInString = new Gson().toJson(glimps);
				redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE_VIRUS.getKey(enclosureId), glimps.getUuid(),
						jsonInString, -1);
				redisManager.hdel(RedisKeysEnum.FT_ENCLOSURE_SCAN.getKey(enclosureId), glimps.getUuid());
				return false;
			} else {
				redisManager.hdel(RedisKeysEnum.FT_ENCLOSURE_SCAN.getKey(enclosureId), glimps.getUuid());
			}
		}
		return true;
	}

	/**
	 * Sort file and send to glimps
	 * 
	 * @param list        list of file to send to glimps
	 * @param enclosureId enclosureId
	 */
	private void sendToGlipms(ArrayList<String> list, String enclosureId) {

		// Create file for length and glimps upload
		list.stream().map(x -> {
			if (!x.endsWith(File.separator) && !x.endsWith("\\") && !x.endsWith("/")) {
				String baseFolderName = getBaseFolderName();
				return new File(baseFolderName + x);
			} else {
				return null;
			}
			// remove null and sort
		}).filter(Objects::nonNull).sorted(Comparator.comparing(File::length, Comparator.reverseOrder()))
				.forEach(file -> uploadGlimps(file, enclosureId));
	}

	/**
	 * Upload file to glimps
	 * 
	 * @param file        file to send
	 * @param enclosureId enclosureId
	 */
	private void uploadGlimps(File file, String enclosureId) {

		HttpHeaders headers = new HttpHeaders();
		headers.set(glimpsTokenKey, glimpsTokenValue);
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		String currentfileName = file.getPath().replace(getBaseFolderNameWithEnclosurePrefix(enclosureId), "");
		String hash = "UNKNOWN";
		try (FileInputStream fs = new FileInputStream(file)) {
			hash = RedisUtils.generateHashSha256(fs);
			MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
			body.add("file", new FileSystemResource(file));
			HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
			LOGGER.debug("Start sending file to glimps for enclosure {} - fileName {} - hash {}", enclosureId,
					currentfileName, hash);
			ResponseEntity<GlimpsInitResponse> initResponse = restTemplate.exchange(glimpsScanUrl, HttpMethod.POST,
					requestEntity, GlimpsInitResponse.class);
			String uuid = "";

			if (StringUtils.isNotBlank(initResponse.getBody().getUuid())) {
				uuid = initResponse.getBody().getUuid();
			} else {
				uuid = initResponse.getBody().getId();
			}

			if (StringUtils.isBlank(uuid)) {
				LOGGER.error("Glimps body : {}", initResponse.getBody());
				throw new WorkerException(
						"Not uuid for glimps file scan enclosure : " + enclosureId + ", file : " + currentfileName);
			}

			ScanInfo rec = ScanInfo.builder().filename(currentfileName).uuid(uuid).build();
			String jsonInString = new Gson().toJson(rec);
			LOGGER.info("File sended to glimps for enclosure {} - fileName {} - glimpsId {} - hash {}", enclosureId,
					currentfileName, rec.getUuid(), hash);
			redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE_SCAN.getKey(enclosureId), rec.getUuid(), jsonInString,
					-1);
		} catch (HttpClientErrorException re) {
			LOGGER.error("Hash : {}, Glimps body : {}, Status : {}, Enclosure: {}", hash, re.getResponseBodyAsString(),
					re.getStatusText(), enclosureId);
			LOGGER.error("Error while sending to glimps for enclosure " + enclosureId, re);
			ScanInfo glimps = ScanInfo.builder().filename(currentfileName).fatalError(true).error(true)
					.errorCode("unknown").uuid("NONE").build();
			String jsonInString = new Gson().toJson(glimps);
			redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE_VIRUS.getKey(enclosureId), "NONE", jsonInString, -1);
			throw new GlimpsException("Error while sending to glimps enclosure :" + enclosureId, re);
		} catch (Exception e) {
			LOGGER.error("Error while sending file " + currentfileName + " with hash " + hash
					+ " to glimps for enclosure " + enclosureId, e);
			ScanInfo glimps = ScanInfo.builder().filename(currentfileName).fatalError(true).error(true)
					.errorCode("unknown").uuid("NONE").build();
			String jsonInString = new Gson().toJson(glimps);
			redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE_VIRUS.getKey(enclosureId), "NONE", jsonInString, -1);
			throw new GlimpsException("Error while sending to glimps enclosure :" + enclosureId, e);
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

	private boolean performScan(ArrayList<String> list, String enclosureId) throws InvalidSizeTypeException {
		boolean isClean = true;
		String currentFileName = null;
		LOGGER.info("Checking clamav for enclosure {}", enclosureId);
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
							LOGGER.debug("Virus status: " + status);
							if (!StringUtils.equalsIgnoreCase("OK", status)) {
								isClean = false;
								ScanInfo glimps = ScanInfo.builder().error(false).fatalError(false).virus(true)
										.filename(currentFileName.replace(enclosureId + "/", "")).uuid(enclosureId)
										.build();
								String jsonInString = new Gson().toJson(glimps);
								redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE_VIRUS.getKey(enclosureId),
										glimps.getUuid(), jsonInString, -1);
							}
						}
					}
				}
			}
		} catch (Exception e) {
			ScanInfo glimps = ScanInfo.builder().error(true).fatalError(true)
					.filename(currentFileName.replace(enclosureId + "/", "")).uuid(enclosureId).build();
			String jsonInString = new Gson().toJson(glimps);
			redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE_VIRUS.getKey(enclosureId), glimps.getUuid(),
					jsonInString, -1);
			LOGGER.error("Error lors du traitement du fichier {} : {}  ", currentFileName, e.getMessage(), e);
			throw new WorkerException("Error During File scanning [" + currentFileName + "]");
		}

		return isClean;
	}

	private void checkSizeAndMimeType(String currentFileName, long enclosureSize, long currentSize,
			FileInputStream fileInputStream) throws IOException, InvalidSizeTypeException {
		if (!mimeService.isAuthorisedMimeTypeFromFile(fileInputStream)) {
			String mimetype = mimeService.getMimeTypeFromFile(fileInputStream);
			String file = StringUtils.substringAfterLast(currentFileName, "/");
			throw new InvalidSizeTypeException("File " + currentFileName + " as invalid mimetype : " + mimetype, file);
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
			// Notify sender
			Locale language = Locale.FRANCE;
			try {
				language = LocaleUtils.toLocale(RedisUtils.getEnclosureValue(redisManager, enclosure.getGuid(),
						EnclosureKeysEnum.LANGUAGE.getKey()));
			} catch (Exception eL) {
				LOGGER.error("Error while getting local", eL);
			}
			if (subjectVirusFound.equalsIgnoreCase(emailSubject)) {
				if (Locale.UK.equals(language)) {
					emailSubject = subjectVirusFoundEn;
				}

			} else if (subjectVirusError.equalsIgnoreCase(emailSubject)) {
				if (Locale.UK.equals(language)) {
					emailSubject = subjectVirusErrorEn;
				}
			}

			if (StringUtils.isNotBlank(enclosure.getSubject())) {
				emailSubject = emailSubject.concat(" : ").concat(enclosure.getSubject());
			}

			mailNotificationService.prepareAndSend(enclosure.getSender(), emailSubject, enclosure, emailTemplateName,
					language);
		} catch (Exception e) {
			LOGGER.error("Error while sending mail for Enclosure " + enclosure.getGuid() + " : " + e.getMessage(), e);
		} finally {

			try {
				/** Clean : OSU, REDIS, UPLOADER FOLDER, and NOTIFY SNDER **/
				LOGGER.info("Processing clean up for enclosure{} - {} / {} - {} ", enclosure.getGuid(), bucketName,
						prefix, bucketPrefix);
				LOGGER.debug("clean up OSU");
				deleteFilesFromOSU(manager, bucketName, prefix);

				// clean temp data in REDIS for Enclosure
				LOGGER.debug("clean up REDIS temp data");
				cleanUpServices.cleanUpEnclosureTempDataInRedis(prefix, true);

				LOGGER.debug("clean up REDIS");
				// Keep enclosure envelope for mail api check if from mail
				String sourceCode = RedisUtils.getEnclosure(redisManager, prefix)
						.get(EnclosureKeysEnum.SOURCE.getKey());
				if (SourceEnum.PUBLIC.getValue().equals(sourceCode)) {
					cleanUpServices.cleanUpEnclosurePartiallyCoreInRedis(prefix);
				} else {
					cleanUpServices.cleanUpEnclosureCoreInRedis(prefix);
				}

				// clean up for Upload directory
				cleanUpServices.deleteEnclosureTempDirectory(getBaseFolderNameWithEnclosurePrefix(prefix));
			} catch (Exception e) {
				LOGGER.error("Error while cleaning up Enclosure " + enclosure.getGuid() + " : " + e.getMessage(), e);
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
