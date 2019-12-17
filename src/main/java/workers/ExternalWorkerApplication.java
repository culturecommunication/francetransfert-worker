package workers;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.RedisManager;

@SpringBootApplication
@EnableScheduling
public class ExternalWorkerApplication {

  public static void main(String[] args) throws Exception {
    SpringApplication.run(ExternalWorkerApplication.class, args);
    ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(5);
    StatsWorker statsWorker = new StatsWorker();
	ZipWorker zipWorker = new ZipWorker();
    UploadDownloadEmailNotificationWorker uploadDownloadEmailNotificationWorker = new UploadDownloadEmailNotificationWorker();
    executor.execute(zipWorker);
    executor.execute(statsWorker);
    executor.execute(uploadDownloadEmailNotificationWorker);
  }
} 