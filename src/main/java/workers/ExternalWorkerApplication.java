package workers;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.RedisManager;

import redis.clients.jedis.JedisPubSub;

@SpringBootApplication
@EnableScheduling
public class ExternalWorkerApplication {

  public static void main(String[] args) throws Exception {
    SpringApplication.run(ExternalWorkerApplication.class, args);
    RedisManager manager = new RedisManager();
    ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
//    StatsWorker statsWorker = new StatsWorker(manager);
    ZipWorker zipWorker = new ZipWorker(manager);
//    executor.execute(statsWorker);
    executor.execute(zipWorker);
  }
} 