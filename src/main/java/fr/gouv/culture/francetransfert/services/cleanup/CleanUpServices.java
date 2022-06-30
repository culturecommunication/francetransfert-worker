package fr.gouv.culture.francetransfert.services.cleanup;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.amazonaws.services.s3.model.Bucket;

import fr.gouv.culture.francetransfert.core.enums.EnclosureKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.RedisKeysEnum;
import fr.gouv.culture.francetransfert.core.exception.MetaloadException;
import fr.gouv.culture.francetransfert.core.exception.StorageException;
import fr.gouv.culture.francetransfert.core.services.RedisManager;
import fr.gouv.culture.francetransfert.core.services.StorageManager;
import fr.gouv.culture.francetransfert.core.utils.Base64CryptoService;
import fr.gouv.culture.francetransfert.core.utils.DateUtils;
import fr.gouv.culture.francetransfert.core.utils.RedisUtils;
import fr.gouv.culture.francetransfert.model.Enclosure;
import fr.gouv.culture.francetransfert.security.WorkerException;
import fr.gouv.culture.francetransfert.services.mail.notification.MailEnclosureNoLongerAvailbleServices;

@Service
public class CleanUpServices {

	private static final Logger LOGGER = LoggerFactory.getLogger(CleanUpServices.class);

	private static final DateTimeFormatter DATE_FORMAT_BUCKET = DateTimeFormatter.ofPattern("yyyyMMdd");

	@Value("${bucket.prefix}")
	private String bucketPrefix;

	@Autowired
	MailEnclosureNoLongerAvailbleServices mailEnclosureNoLongerAvailbleServices;

	@Autowired
	StorageManager storageManager;

	@Autowired
	RedisManager redisManager;

	@Autowired
	Base64CryptoService base64CryptoService;

	@Value("${worker.expired.limit}")
	private int maxUpdateDate;

	/**
	 * clean all expired data in OSU and REDIS
	 *
	 * @throws WorkerException
	 */
	public void cleanUp() throws WorkerException {

		redisManager.smembersString(RedisKeysEnum.FT_ENCLOSURE_DATES.getKey("")).forEach(date -> {
			redisManager.smembersString(RedisKeysEnum.FT_ENCLOSURE_DATE.getKey(date)).forEach(enclosureId -> {
				try {
					LocalDate enclosureExipireDateRedis = DateUtils.convertStringToLocalDateTime(
							redisManager.getHgetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId),
									EnclosureKeysEnum.EXPIRED_TIMESTAMP.getKey()))
							.toLocalDate();

					boolean archive = false;

					String archiveDate = redisManager.getHgetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId),
							EnclosureKeysEnum.EXPIRED_TIMESTAMP_ARCHIVE.getKey());

					LocalDate enclosureExpireArchiveDateRedis = DateUtils.convertStringToLocalDateTime(
							redisManager.getHgetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId),
									EnclosureKeysEnum.EXPIRED_TIMESTAMP_ARCHIVE.getKey()))
							.toLocalDate();

					if (enclosureExipireDateRedis.plusDays(1).equals(LocalDate.now())
							|| enclosureExipireDateRedis.plusDays(1).isBefore(LocalDate.now())) {
						if (StringUtils.isBlank(archiveDate)) {
							cleanEnclosure(enclosureId, archive);
						} else {
							if (!StringUtils.isBlank(archiveDate)
									&& (enclosureExpireArchiveDateRedis.plusDays(1).equals(LocalDate.now())
											|| enclosureExpireArchiveDateRedis.plusDays(1).isBefore(LocalDate.now()))) {
								archive = true;
								cleanEnclosure(enclosureId, archive);
								cleanUpEnclosureDatesInRedis(date);
							}
						}
						// clean enclosure date : delete list enclosureId and date expired
					}
				} catch (Exception e) {
					LOGGER.error("Cannot clean enclosure {} : " + e.getMessage(), enclosureId, e);
				}
			});
		});
	}

	public void cleanEnclosure(String enclosureId, boolean archive) throws MetaloadException {
		// expire date + 1
		Enclosure enc = Enclosure.build(enclosureId, redisManager);
		Integer countDownload = 0;
		if (enc.isPublicLink()) {
			Map<String, String> enclosureMap = redisManager
					.hmgetAllString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId));
			if (enclosureMap != null) {
				countDownload = Integer.parseInt(enclosureMap.get(EnclosureKeysEnum.PUBLIC_DOWNLOAD_COUNT.getKey()));
			}
		} else {
			countDownload = enc.getRecipients().stream().map(recipi -> {
				try {
					return RedisUtils.getNumberOfDownloadsPerRecipient(redisManager, recipi.getId());
				} catch (MetaloadException e) {
					LOGGER.error("Cannot get nbDown recipient", e);
				}
				return 0;
			}).collect(Collectors.summingInt(x -> x));

		}

		if (countDownload == 0) {
			LOGGER.warn("msgtype: NOT_DOWNLOADED || enclosure: {} || sender: {}", enc.getGuid(), enc.getSender());
		}

		if (!archive) {
			LocalDate enclosureExipireDateRedis = DateUtils.convertStringToLocalDateTime(redisManager.getHgetString(
					RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId), EnclosureKeysEnum.EXPIRED_TIMESTAMP.getKey()))
					.toLocalDate();
			Map<String, String> enclosureMap = redisManager
					.hmgetAllString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId));
			LocalDateTime expiredArchiveDate = enclosureExipireDateRedis.atStartOfDay().plus(Period.ofDays(365));
			enclosureMap.put(EnclosureKeysEnum.EXPIRED_TIMESTAMP_ARCHIVE.getKey(), expiredArchiveDate.toString());
			redisManager.insertHASH(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId), enclosureMap);
			mailEnclosureNoLongerAvailbleServices.sendEnclosureNotAvailble(enc);
		}

		LOGGER.info(" clean up for enclosure N° {}", enclosureId);
		String bucketName = RedisUtils.getBucketName(redisManager, enclosureId, bucketPrefix);

		// clean temp data in REDIS for Enclosure
		cleanUpEnclosureTempDataInRedis(enclosureId, archive);
		LOGGER.info("Clean up REDIS temp data");

		// clean enclosure in OSU : delete enclosure
		LOGGER.info("Clean up OSU");

		try {
			cleanUpOSU(bucketName, enclosureId);
		} catch (Exception ex) {
			LOGGER.error("Cannot delete enclosure " + enclosureId);
		}

		// clean enclosure Core in REDIS : delete files, root-files, root-dirs,
		// recipients, sender and enclosure
		LOGGER.info("Clean up REDIS");

		if (archive) {
			cleanUpEnclosureCoreInRedis(enclosureId);
		}

	}

	/**
	 * clean all data expired in OSU
	 *
	 * @param enclosureId
	 * @throws StorageException
	 */
	private void cleanUpOSU(String bucketName, String enclosureId) throws StorageException {
		storageManager.deleteFilesWithPrefix(bucketName, storageManager.getZippedEnclosureName(enclosureId));
	}

	/**
	 * clean expired data in REDIS: Enclosure core
	 *
	 * @param enclosureId
	 * @throws WorkerException
	 * @throws MetaloadException
	 */
	public void cleanUpEnclosureCoreInRedis(String enclosureId) throws WorkerException, MetaloadException {
		Enclosure enclosure = Enclosure.build(enclosureId, redisManager);
		if (!enclosure.isPublicLink()) {
			// Delete received list
			enclosure.getRecipients().stream().forEach(x -> {
				redisManager.srem(RedisKeysEnum.FT_RECEIVE.getKey(x.getMail()), enclosureId);
			});
		}
		// delete list and HASH root-files
		deleteRootFiles(enclosureId);
		LOGGER.debug("clean root-files {}", RedisKeysEnum.FT_ROOT_FILES.getKey(enclosureId));
		// delete list and HASH root-dirs
		deleteRootDirs(enclosureId);
		LOGGER.debug("clean root-dirs {}", RedisKeysEnum.FT_ROOT_DIRS.getKey(enclosureId));
		// delete list and HASH recipients
		deleteListAndHashRecipients(enclosureId);
		LOGGER.debug("clean recipients {}", RedisKeysEnum.FT_RECIPIENTS.getKey(enclosureId));
		// delete hash sender
		redisManager.deleteKey(RedisKeysEnum.FT_SENDER.getKey(enclosureId));
		redisManager.deleteKey(RedisKeysEnum.FT_ADMIN_TOKEN.getKey(enclosureId));
		LOGGER.debug("clean sender HASH {}", RedisKeysEnum.FT_SENDER.getKey(enclosureId));
		// delete hash enclosure
		redisManager.deleteKey(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId));
		// delete enclosureid from sendlist
		redisManager.srem(RedisKeysEnum.FT_SEND.getKey(enclosure.getSender()), enclosureId);
		LOGGER.debug("clean enclosure HASH {}", RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId));
	}

	/**
	 * clean temp data in REDIS for Enclosure
	 *
	 * @param redisManager
	 * @param enclosureId
	 * @throws WorkerException
	 */
	public void cleanUpEnclosureTempDataInRedis(String enclosureId, boolean archive) throws WorkerException {
		// delete part-etags
		deleteListPartEtags(enclosureId);

		// delete id container list
		if (archive) {
			deleteListIdContainer(enclosureId);
		}
		// delete list and HASH files
		deleteFiles(enclosureId);
	}

	/**
	 * clean expired data in REDIS: Enclosure dates
	 *
	 * @param redisManager
	 * @param date
	 * @throws WorkerException
	 */
	private void cleanUpEnclosureDatesInRedis(String date) throws WorkerException {
		// delete list enclosureId of expired date
		redisManager.deleteKey(RedisKeysEnum.FT_ENCLOSURE_DATE.getKey(date));
		LOGGER.info("clean list enclosure per date {}", RedisKeysEnum.FT_ENCLOSURE_DATE.getKey(date));
		// delete date expired from the list of dates
		redisManager.sremString(RedisKeysEnum.FT_ENCLOSURE_DATES.getKey(""), date);
		LOGGER.info("finish clean up list dates {} delete date : {} ", RedisKeysEnum.FT_ENCLOSURE_DATE.getKey(date),
				date);
	}

	/**
	 * @param redisManager
	 * @param enclosureId
	 */
	private void deleteFiles(String enclosureId) {
		String keyFiles = RedisKeysEnum.FT_FILES_IDS.getKey(enclosureId);
		// list files
		List<String> listFileIds = redisManager.lrange(keyFiles, 0, -1);
		// delete Hash files info
		LOGGER.debug("clean up files: {}", RedisKeysEnum.FT_FILES_IDS.getKey(enclosureId));
		for (String fileId : listFileIds) {
			redisManager.deleteKey(RedisKeysEnum.FT_FILE.getKey(fileId));
			LOGGER.debug("clean up file: {}", RedisKeysEnum.FT_FILE.getKey(fileId));
		}
		// delete list of files
		redisManager.deleteKey(keyFiles);
	}

	private void deleteRootFiles(String enclosureId) {
		String keyRootFiles = RedisKeysEnum.FT_ROOT_FILES.getKey(enclosureId);
		// list root-files
		List<String> listRootFileIds = redisManager.lrange(keyRootFiles, 0, -1);
		// delete Hash root-files info
		LOGGER.debug("clean up root-files: {}", RedisKeysEnum.FT_ROOT_FILES.getKey(enclosureId));
		for (String rootFileId : listRootFileIds) {
			redisManager.deleteKey(
					RedisKeysEnum.FT_ROOT_FILE.getKey(RedisUtils.generateHashsha1(enclosureId + ":" + rootFileId)));
			LOGGER.debug("clean up root-file: {}", RedisKeysEnum.FT_ROOT_FILE.getKey(rootFileId));
		}
		// delete list of root-files
		redisManager.deleteKey(keyRootFiles);
	}

	private void deleteRootDirs(String enclosureId) {
		String keyrootDirs = RedisKeysEnum.FT_ROOT_DIRS.getKey(enclosureId);
		// list root-dirs
		List<String> listRootDirIds = redisManager.lrange(keyrootDirs, 0, -1);
		// delete Hash root-dirs info
		LOGGER.debug("clean up root-dirs: {}", RedisKeysEnum.FT_ROOT_DIRS.getKey(enclosureId));
		for (String rootDirId : listRootDirIds) {
//            redisManager.hmgetAllString(RedisKeysEnum.FT_ROOT_DIR.getKey(RedisUtils.generateHashsha1(enclosureId + ":" + rootDirId)))
			redisManager.deleteKey(
					RedisKeysEnum.FT_ROOT_DIR.getKey(RedisUtils.generateHashsha1(enclosureId + ":" + rootDirId)));
			LOGGER.debug("clean up root-dir: {}", RedisKeysEnum.FT_ROOT_DIR.getKey(rootDirId));
		}
		// delete list of root-dirs
		redisManager.deleteKey(keyrootDirs);
	}

	private void deleteListPartEtags(String enclosureId) {
		// list files
		List<String> listFileIds = redisManager.lrange(RedisKeysEnum.FT_FILES_IDS.getKey(enclosureId), 0, -1);
		// delete list part-etags
		for (String fileId : listFileIds) {
			redisManager.deleteKey(RedisKeysEnum.FT_PART_ETAGS.getKey(fileId));
			LOGGER.debug("clean part-etags {}", RedisKeysEnum.FT_PART_ETAGS.getKey(fileId));
		}
	}

	private void deleteListIdContainer(String enclosureId) {
		// list files
		List<String> listFileIds = redisManager.lrange(RedisKeysEnum.FT_FILES_IDS.getKey(enclosureId), 0, -1);
		// delete list id container
		for (String fileId : listFileIds) {
			redisManager.deleteKey(RedisKeysEnum.FT_ID_CONTAINER.getKey(fileId));
			LOGGER.debug("clean id container {}", RedisKeysEnum.FT_ID_CONTAINER.getKey(fileId));
		}
	}

	/**
	 * @param redisManager
	 * @param enclosureId
	 * @throws WorkerException
	 */
	private void deleteListAndHashRecipients(String enclosureId) throws WorkerException {
		try {
			// Map recipients exemple : "charles.domenech@drac-idf.culture.gouv.fr":
			// "93e86440-fc67-4d71-9f74-fe17325e946a",
			Map<String, String> mapRecipients = RedisUtils.getRecipientsEnclosure(redisManager, enclosureId);
			for (String recipientId : mapRecipients.values()) {
				// delete Hash recipient info
				redisManager.deleteKey(RedisKeysEnum.FT_RECIPIENT.getKey(recipientId));
			}
			// delete Hash recipients info
			redisManager.deleteKey(RedisKeysEnum.FT_RECIPIENTS.getKey(enclosureId));
		} catch (Exception e) {
			throw new WorkerException(e);
		}
	}

	/**
	 * Delete directory from uri
	 *
	 * @param path
	 */
	public void deleteEnclosureTempDirectory(String path) {
		LOGGER.info(" clean up Enclosure temp directory {} ", path);
		try {

			Path pathToBeDeleted = Paths.get(path);
			try (Stream<Path> walk = Files.walk(pathToBeDeleted)) {
				walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
			}
			;
		} catch (IOException e) {
			LOGGER.error("unable to delete Enclosure temp directory [{}] / {} ", path, e.getMessage(), e);
		}
	}

	public void deleteBucketOutOfTime() throws StorageException {

		LocalDateTime now = LocalDateTime.now();
		for (int i = 0; i < 7; i++) {
			try {
				String buckName = bucketPrefix + now.format(DATE_FORMAT_BUCKET);
				storageManager.createBucket(buckName);
			} catch (Exception e) {
				LOGGER.debug("Error while creating bucket : " + e.getMessage(), e);
			}
			now = now.plusDays(1L);
		}

		List<Bucket> listeBucket = storageManager.listBuckets();
		listeBucket.forEach(bucket -> {
			try {
				String bucketDate = bucket.getName().substring(bucketPrefix.length());
				LocalDate date = LocalDate.parse(bucketDate, DATE_FORMAT_BUCKET);
				if (date.plusDays(maxUpdateDate).isBefore(LocalDate.now())
						&& bucket.getName().startsWith(bucketPrefix)) {
					try {
						deleteContentBucket(bucket.getName());
					} catch (StorageException e) {
						LOGGER.error("unable to delete content of bucket {} ", bucket.getName(), e.getMessage(), e);
					}
					try {
						storageManager.deleteBucket(bucket.getName());
					} catch (StorageException e) {
						LOGGER.error("unable to delete bucket {} ", bucket.getName(), e.getMessage(), e);
					}
				}
			} catch (Exception e) {
				LOGGER.error("cannot parse bucket date {} ", bucket.getName(), e.getMessage(), e);
			}
		});

	}

	public void deleteContentBucket(String bucketName) throws StorageException {
		ArrayList<String> objectListing = storageManager.listBucketContent(bucketName);

		objectListing.forEach(file -> {
			try {
				storageManager.deleteFilesWithPrefix(bucketName, file);
			} catch (StorageException e) {
				LOGGER.error("unable to delete file {} ", file, e.getMessage(), e);
			}
		});
	}

}
