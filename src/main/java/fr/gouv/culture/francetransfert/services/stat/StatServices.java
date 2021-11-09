package fr.gouv.culture.francetransfert.services.stat;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import fr.gouv.culture.francetransfert.enums.TypeStat;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.EnclosureKeysEnum;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.RedisUtils;
import fr.gouv.culture.francetransfert.security.WorkerException;
import fr.gouv.culture.francetransfert.utils.Base64CryptoService;

@Service
public class StatServices {

	private static final Logger LOGGER = LoggerFactory.getLogger(StatServices.class);

	@Autowired
	RedisManager redisManager;

	@Autowired
	Base64CryptoService base64CryptoService;

	public boolean saveData(String enclosureId) throws WorkerException {
		try {
			LOGGER.info("STEP SAVE STATS");
			boolean isSaved = true;

			Map<String, String> enclosureRedis = RedisUtils.getEnclosure(redisManager, enclosureId);

			String sender = RedisUtils.getEmailSenderEnclosure(redisManager, enclosureId);
			long plisSize = RedisUtils.getTotalSizeEnclosure(redisManager, enclosureId);
			String totalSizeEnclosure = FileUtils.byteCountToDisplaySize(plisSize);
			Map<String, String> recipient = RedisUtils.getRecipientsEnclosure(redisManager, enclosureId);

			String recipientList = recipient.keySet().stream().map(x -> x.split("@")[1]).distinct()
					.collect(Collectors.joining("|"));

			LocalDateTime date = LocalDateTime.parse(enclosureRedis.get(EnclosureKeysEnum.TIMESTAMP.getKey()));

			String fileName = date.format(DateTimeFormatter.ISO_LOCAL_DATE) + "_" + TypeStat.UPLOAD.getValue() + ".csv";
			Path filePath = Path.of(System.getProperty("java.io.tmpdir"), fileName);
			StringBuilder sb = new StringBuilder();
			CSVFormat option = CSVFormat.DEFAULT.builder().setQuoteMode(QuoteMode.ALL).build();
			CSVPrinter csvPrinter = new CSVPrinter(sb, option);

			// PLIS,DATE,Expediteur,destinataire,poids,donnes_utilisation,type,poidslong
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
}
