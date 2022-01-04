package fr.gouv.culture.francetransfert.services.satisfaction;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import fr.gouv.culture.francetransfert.core.model.RateRepresentation;
import fr.gouv.culture.francetransfert.security.WorkerException;

@Service
public class SatisfactionService {

	private static final Logger LOGGER = LoggerFactory.getLogger(SatisfactionService.class);

	public boolean saveData(RateRepresentation rate) throws WorkerException {
		try {
			String fileName = rate.getDate().format(DateTimeFormatter.ISO_LOCAL_DATE) + "_" + rate.getType().getValue()
					+ ".csv";
			Path filePath = Path.of(System.getProperty("java.io.tmpdir"), fileName);
			StringBuilder sb = new StringBuilder();
			CSVFormat option = CSVFormat.DEFAULT.builder().setQuoteMode(QuoteMode.ALL).build();
			CSVPrinter csvPrinter = new CSVPrinter(sb, option);
			csvPrinter.printRecord(rate.getPlis(), rate.getDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
					rate.getMessage(), rate.getSatisfaction(), rate.getType().getValue(), rate.getDomain());
			csvPrinter.flush();
			csvPrinter.close();
			Files.writeString(filePath, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			return true;
		} catch (Exception e) {
			LOGGER.error("error save data in CsvFile", e);
			throw new WorkerException("error save data in CsvFile");
		}
	}
}
