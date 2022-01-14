package fr.gouv.culture.francetransfert.services.stat;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import fr.gouv.culture.francetransfert.core.enums.EnclosureKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.TypeStat;
import fr.gouv.culture.francetransfert.core.services.RedisManager;
import fr.gouv.culture.francetransfert.core.utils.Base64CryptoService;
import fr.gouv.culture.francetransfert.core.utils.RedisUtils;
import fr.gouv.culture.francetransfert.security.WorkerException;

@Service
public class StatServices {

	private static final Logger LOGGER = LoggerFactory.getLogger(StatServices.class);

	@Autowired
	RedisManager redisManager;

	@Autowired
	Base64CryptoService base64CryptoService;

	private static final String[] HEADER = { "ID_PLIS", "DATE", "DOMAINE_EXPEDITEUR", "DOMAINE_DESTINATAIRE", "TAILLE",
			"HASH_MAIL", "TYPE_ACTION" };

	public boolean saveDataUpload(String enclosureId) throws WorkerException {
		try {
			LOGGER.info("STEP SAVE UPLOAD STATS");
			boolean isSaved = true;

			Map<String, String> enclosureRedis = RedisUtils.getEnclosure(redisManager, enclosureId);

			String sender = RedisUtils.getEmailSenderEnclosure(redisManager, enclosureId);
			long plisSize = RedisUtils.getTotalSizeEnclosure(redisManager, enclosureId);
			String totalSizeEnclosure = byteCountToDisplaySize(plisSize);
			Map<String, String> recipient = RedisUtils.getRecipientsEnclosure(redisManager, enclosureId);

			String recipientList = recipient.keySet().stream().map(x -> x.split("@")[1]).distinct()
					.collect(Collectors.joining("|"));

			LocalDateTime date = LocalDateTime.parse(enclosureRedis.get(EnclosureKeysEnum.TIMESTAMP.getKey()));
			String hostname = InetAddress.getLocalHost().getHostName().split("\\.")[0];
			LOGGER.debug("Hostname: " + hostname);
			String fileName = hostname + "_" + date.format(DateTimeFormatter.ISO_LOCAL_DATE) + "_"
					+ TypeStat.UPLOAD.getValue() + ".csv";
			Path filePath = Path.of(System.getProperty("java.io.tmpdir"), fileName);
			StringBuilder sb = new StringBuilder();
			CSVFormat option = CSVFormat.DEFAULT.builder().setQuoteMode(QuoteMode.ALL).setHeader(HEADER)
					.setSkipHeaderRecord(Files.exists(filePath)).build();
			CSVPrinter csvPrinter = new CSVPrinter(sb, option);

			// PLIS,DATE,Expediteur,destinataire,poids,hash_sender,type
			csvPrinter.printRecord(enclosureId, date.format(DateTimeFormatter.ISO_LOCAL_DATE), sender.split("@")[1],
					recipientList, totalSizeEnclosure, base64CryptoService.encodedHash(sender),
					TypeStat.UPLOAD.getValue());

			csvPrinter.flush();
			csvPrinter.close();
			Files.writeString(filePath, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			return isSaved;
		} catch (Exception e) {
			LOGGER.error("Error save data in CSV UPLOAD : " + e.getMessage(), e);
			throw new WorkerException("error save data in CSV UPLOAD");
		}
	}

	public boolean saveDataDownload(String enclosureId, String recipientMail) throws WorkerException {
		try {
			LOGGER.info("STEP SAVE STATS DOWNLOAD");
			boolean isSaved = true;

			Map<String, String> enclosureRedis = RedisUtils.getEnclosure(redisManager, enclosureId);

			String sender = RedisUtils.getEmailSenderEnclosure(redisManager, enclosureId);
			long plisSize = RedisUtils.getTotalSizeEnclosure(redisManager, enclosureId);
			String totalSizeEnclosure = byteCountToDisplaySize(plisSize);

			String recipientList = "";
			String hashedMail = "";
			if (StringUtils.isNotBlank(recipientMail)) {
				recipientList = recipientMail.split("@")[1];
				hashedMail = base64CryptoService.encodedHash(recipientMail);
			}

			LocalDateTime date = LocalDateTime.parse(enclosureRedis.get(EnclosureKeysEnum.TIMESTAMP.getKey()));
			String hostname = InetAddress.getLocalHost().getHostName().split("\\.")[0];
			LOGGER.debug("Hostname: " + hostname);
			String fileName = hostname + "_" + date.format(DateTimeFormatter.ISO_LOCAL_DATE) + "_"
					+ TypeStat.DOWNLOAD.getValue() + ".csv";
			Path filePath = Path.of(System.getProperty("java.io.tmpdir"), fileName);
			StringBuilder sb = new StringBuilder();
			CSVFormat option = CSVFormat.DEFAULT.builder().setQuoteMode(QuoteMode.ALL).setHeader(HEADER)
					.setSkipHeaderRecord(Files.exists(filePath)).build();
			CSVPrinter csvPrinter = new CSVPrinter(sb, option);

			// PLIS,DATE,Expediteur,destinataire,poids,hash_reciever,type
			csvPrinter.printRecord(enclosureId, date.format(DateTimeFormatter.ISO_LOCAL_DATE), sender.split("@")[1],
					recipientList, totalSizeEnclosure, hashedMail, TypeStat.DOWNLOAD.getValue());

			csvPrinter.flush();
			csvPrinter.close();
			Files.writeString(filePath, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			return isSaved;
		} catch (Exception e) {
			LOGGER.error("Error save data in CSV DOWNLOAD : " + e.getMessage(), e);
			throw new WorkerException("error save data in CSV DOWNLOAD");
		}
	}

	private String byteCountToDisplaySize(long plisSize) {
		String size = FileUtils.byteCountToDisplaySize(plisSize);
		size = size.replace("bytes", "B");
		return size;
	}

}
