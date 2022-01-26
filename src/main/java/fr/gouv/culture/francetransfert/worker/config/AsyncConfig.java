package fr.gouv.culture.francetransfert.worker.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Value("${satisfactionWorkerExecutor.pool.size:10}")
    private int satisfactionWorkerExecutorPoolSize;
    
    @Value("${sendEmailConfirmationCodeWorker.pool.size:10}")
    private int sendEmailConfirmationCodeWorkerExecutorPoolSize;
    
    @Value("${tempDataCleanUpWorkerExecutor.pool.size:10}")
    private int tempDataCleanUpWorkerExecutorPoolSize;
    
    @Value("${zipWorkerExecutor.pool.size:10}")
    private int zipWorkerExecutorPoolSize;
    
    @Value("${sendEmailDownloadInProgressWorkerExecutor.pool.size:10}")
    private int sendEmailDownloadInProgressWorkerExecutorPoolSize;
    
    @Value("${uploadDownloadWorkerExecutor.pool.size:10}")
    private int sendEmailNotificationUploadDownloadWorkerExecutorPoolSize;
    
    @Value("${statWorkerExecutor.pool.size:10}")
    private int statWorkerExecutorPoolSize;

    @Value("5")
    private int sequestreWorkerExecutorPoolSize;

    @Value("5")
    private int formuleContactWorkerExecutorPoolSize;


    @Bean(name = "formuleContactWorkerExecutor")
    public Executor formuleContactWorkerExecutor() {
        return generateThreadPoolTaskExecutor(formuleContactWorkerExecutorPoolSize);
    }

    @Bean(name = "satisfactionWorkerExecutor")
    public Executor satisfactionWorkerExecutor() {
    	return generateThreadPoolTaskExecutor(satisfactionWorkerExecutorPoolSize);
    }
    
    @Bean(name = "sendEmailConfirmationCodeWorkerExecutor")
    public Executor sendEmailConfirmationCodeWorkerExecutor() {
    	return generateThreadPoolTaskExecutor(sendEmailConfirmationCodeWorkerExecutorPoolSize);
    }

	@Bean(name = "tempDataCleanUpWorkerExecutor")
    public Executor tempDataCleanUpWorkerExecutor() {
		return generateThreadPoolTaskExecutor(tempDataCleanUpWorkerExecutorPoolSize);
    }
	
	@Bean(name = "zipWorkerExecutor")
    public Executor zipWorkerExecutor() {
		return generateThreadPoolTaskExecutor(zipWorkerExecutorPoolSize);
    }
	
	@Bean(name = "sendEmailDownloadInProgressWorkerExecutor")
    public Executor sendEmailDownloadInProgressWorkerExecutor() {
		return generateThreadPoolTaskExecutor(sendEmailDownloadInProgressWorkerExecutorPoolSize);
    }
	
	@Bean(name = "sendEmailNotificationUploadDownloadWorkerExecutor")
    public Executor sendEmailNotificationUploadDownloadWorkerExecutor() {
		return generateThreadPoolTaskExecutor(sendEmailNotificationUploadDownloadWorkerExecutorPoolSize);
    }
	
	@Bean(name = "statWorkerExecutor")
    public Executor statWorkerExecutor() {
		return generateThreadPoolTaskExecutor(statWorkerExecutorPoolSize);
    }

    @Bean(name = "sequestreWorkerExecutor")
    public Executor sequestreWorkerExecutor() {
        return generateThreadPoolTaskExecutor(sequestreWorkerExecutorPoolSize);
    }
	
	private Executor generateThreadPoolTaskExecutor(int maxPoolSize) {
		ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
		exec.setMaxPoolSize(maxPoolSize);
		exec.setCorePoolSize(maxPoolSize);
		exec.setKeepAliveSeconds(0);
		return exec;
	}
}
