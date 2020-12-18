package fr.gouv.culture.francetransfert.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import fr.gouv.culture.francetransfert.services.zipworker.ZipWorkerServices;

@Component
public class ZipWorkerTask implements Runnable {

	private static final Logger LOGGER = LoggerFactory.getLogger(ZipWorkerTask.class);
	
//	@Autowired
    private ZipWorkerServices zipWorkerServices;
	
	private String enclosureId;

	public ZipWorkerTask(String enclosureId, ZipWorkerServices zipWorkerServices) {
		this.enclosureId = enclosureId;
		this.zipWorkerServices = zipWorkerServices;
	}
	
	public ZipWorkerTask() {
		
	}

	@Override
	public void run() {
		LOGGER.info("================================> worker : start zip  process for enclosur N°  {}", enclosureId);
    	try {
    		System.out.println("ThreadName: " + Thread.currentThread().getName() + " | ThreadId: " + Thread.currentThread().getId());
			zipWorkerServices.startZip(enclosureId);
		} catch (Exception e) {
			LOGGER.error(e.getMessage());
			e.printStackTrace();
		}
    	System.out.println("ThreadName: " + Thread.currentThread().getName() + " | ThreadId: " + Thread.currentThread().getId() + " IS DEAD");
    }
}