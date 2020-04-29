package fr.gouv.culture.francetransfert.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fr.gouv.culture.francetransfert.model.Rate;
import fr.gouv.culture.francetransfert.services.satisfaction.SatisfactionService;

@Component
public class SatisfactionTask implements Runnable {
 
	private static final Logger LOGGER = LoggerFactory.getLogger(SatisfactionTask.class);
	
    private SatisfactionService satisfactionService;

	private Rate rate;

	public SatisfactionTask(Rate rate, SatisfactionService satisfactionService) {
		this.rate = rate;
		this.satisfactionService = satisfactionService;
	}
	
	public SatisfactionTask() {
		
	}
	
	@Override
	public void run() {
    	try {
    		System.out.println("ThreadName: " + Thread.currentThread().getName() + " | ThreadId: " + Thread.currentThread().getId());
    		LOGGER.info("================================> convert json in string to object rate");
            LOGGER.info("================================> start save satisfaction data in mongoDb");
            satisfactionService.saveData(rate);
    		
		} catch (Exception e) {
			LOGGER.error(e.getMessage());
		}
    }
}
