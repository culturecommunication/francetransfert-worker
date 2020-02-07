package fr.gouv.culture.francetransfert.services.satisfaction;

import fr.gouv.culture.francetransfert.model.DetailedSatisfactionData;
import fr.gouv.culture.francetransfert.model.MongoLocalDateTime;
import fr.gouv.culture.francetransfert.model.Rate;
import fr.gouv.culture.francetransfert.model.SimpleSatisfactionData;
import fr.gouv.culture.francetransfert.repository.DetailedSatisfactionDataRepository;
import fr.gouv.culture.francetransfert.repository.SimpleSatisfactionDataRepository;
import fr.gouv.culture.francetransfert.security.WorkerException;
import fr.gouv.culture.francetransfert.utils.WorkerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
@Service
public class SatisfactionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SatisfactionService.class);

    @Autowired
    private DetailedSatisfactionDataRepository detailedSatisfactionDataRepository;

    @Autowired
    private SimpleSatisfactionDataRepository simpleSatisfactionDataRepository;

    public boolean saveData(Rate rate) throws WorkerException {
        try {
            boolean isSaved = true;
            MongoLocalDateTime mongoLocalDateTime = MongoLocalDateTime.of(LocalDateTime.now());
            LOGGER.info("=================== save collection Simple Satisfaction Data mongoDB");
            SimpleSatisfactionData simpleSatisfactionData = SimpleSatisfactionData.builder()
                    .date(mongoLocalDateTime)
                    .satisfaction(rate.getSatisfaction())
                    .isAgent(WorkerUtils.isGouvEmail(rate.getMailAdress()))
                    .build();
            simpleSatisfactionData = simpleSatisfactionDataRepository.save(simpleSatisfactionData);
            isSaved = (simpleSatisfactionData != null);
            LOGGER.info("=================== save collection Detailed Satisfaction Data mongoDB");
            DetailedSatisfactionData detailedSatisfactionData = DetailedSatisfactionData.builder()
                    .date(mongoLocalDateTime)
                    .email(rate.getMailAdress())
                    .message(rate.getMessage())
                    .satisfaction(rate.getSatisfaction())
                    .build();
            detailedSatisfactionData = detailedSatisfactionDataRepository.save(detailedSatisfactionData);
            isSaved = (detailedSatisfactionData != null);
            return isSaved;
        } catch (Exception e) {
            LOGGER.error("=================== error save data in mongoDB");
            throw new WorkerException("error save data in mongoDb");
        }
    }
}
