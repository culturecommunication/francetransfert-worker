package fr.gouv.culture.francetransfert.worker;

import com.google.gson.Gson;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RedisQueueEnum;
import fr.gouv.culture.francetransfert.model.RateRepresentation;
import fr.gouv.culture.francetransfert.security.WorkerException;
import fr.gouv.culture.francetransfert.services.app.sync.AppSyncServices;
import fr.gouv.culture.francetransfert.services.cleanup.CleanUpServices;
import fr.gouv.culture.francetransfert.services.ignimission.IgnimissionServices;
import fr.gouv.culture.francetransfert.services.mail.notification.MailAvailbleEnclosureServices;
import fr.gouv.culture.francetransfert.services.mail.notification.MailConfirmationCodeServices;
import fr.gouv.culture.francetransfert.services.mail.notification.MailDownloadServices;
import fr.gouv.culture.francetransfert.services.mail.notification.MailRelaunchServices;
import fr.gouv.culture.francetransfert.services.satisfaction.SatisfactionService;
import fr.gouv.culture.francetransfert.services.sequestre.SequestreService;
import fr.gouv.culture.francetransfert.services.stat.StatServices;
import fr.gouv.culture.francetransfert.services.zipworker.ZipWorkerServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Component
public class ScheduledTasks {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledTasks.class);

    @Value("${bucket.sequestre}")
    private String sequestreBucket;

    @Autowired
    private MailAvailbleEnclosureServices mailAvailbleEnclosureServices;

    @Autowired
    private MailRelaunchServices mailRelaunchServices;

    @Autowired
    private AppSyncServices appSyncServices;

    @Autowired
    private MailDownloadServices mailDownloadServices;

    @Autowired
    private MailConfirmationCodeServices mailConfirmationCodeServices;

    @Autowired
    private CleanUpServices cleanUpServices;

    @Autowired
    private ZipWorkerServices zipWorkerServices;

    @Autowired
    private StatServices statServices;

    @Autowired
    private SatisfactionService satisfactionService;

    @Autowired
    private IgnimissionServices ignimissionServices;

    @Autowired
    private SequestreService sequestreService;

    @Autowired
    private RedisManager redisManager;

    @Autowired
    @Qualifier("satisfactionWorkerExecutor")
    Executor satisfactionWorkerExecutorFromBean;

    @Autowired
    @Qualifier("sendEmailConfirmationCodeWorkerExecutor")
    Executor sendEmailConfirmationCodeWorkerExecutorFromBean;

    @Autowired
    @Qualifier("tempDataCleanUpWorkerExecutor")
    Executor tempDataCleanUpWorkerExecutorFromBean;

    @Autowired
    @Qualifier("zipWorkerExecutor")
    Executor zipWorkerExecutorFromBean;

    @Autowired
    @Qualifier("sendEmailDownloadInProgressWorkerExecutor")
    Executor sendEmailDownloadInProgressWorkerExecutorFromBean;

    @Autowired
    @Qualifier("sendEmailNotificationUploadDownloadWorkerExecutor")
    Executor sendEmailNotificationUploadDownloadWorkerExecutorFromBean;

    @Autowired
    @Qualifier("statWorkerExecutor")
    Executor statWorkerExecutorFromBean;

    @Autowired
    @Qualifier("sequestreWorkerExecutor")
    Executor sequestreWorkerExecutorFromBean;

    @Scheduled(cron = "${scheduled.relaunch.mail}")
    public void relaunchMail() throws WorkerException {
        LOGGER.info("Worker : start relaunch for download Check");
        if (appSyncServices.shouldRelaunch()) {
            LOGGER.info("Worker : start relaunch for download Checked and Started");
            mailRelaunchServices.sendMailsRelaunch();
            mailDownloadServices.sendMailsDownload();
        }
    }

    @Scheduled(cron = "${scheduled.clean.up}")
    public void cleanUp() throws WorkerException {
        LOGGER.info("Worker : start clean-up expired enclosure Check");
        if (appSyncServices.shouldCleanup()) {
            LOGGER.info("Worker : start clean-up expired enclosure Checked and Started");
            cleanUpServices.cleanUp();
        }
    }

    @Scheduled(cron = "${scheduled.app.sync.cleanup}")
    public void appSyncCleanup() throws WorkerException {
        LOGGER.info("Worker : start Application synchronization cleanup");
        appSyncServices.appSyncCleanup();
    }

    @Scheduled(cron = "${scheduled.app.sync.relaunch}")
    public void appSyncRelaunch() throws WorkerException {
        LOGGER.info("Worker : start Application synchronization relaunch");
        appSyncServices.appSyncRelaunch();
    }

    @Scheduled(cron = "${scheduled.ignimission.domain}")
    public void ignimissionDomainUpdate() throws WorkerException {
        if (appSyncServices.shouldUpdateIgnimissionDomain()) {
            LOGGER.info("Worker : start Application ignimission domain extension update");
            ignimissionServices.updateDomains();
        }
    }

    @Scheduled(cron = "${scheduled.send.stat}")
    public void ignimissionSendStat() throws WorkerException {
        LOGGER.info("Worker : start Application ignimission domain extension update");
        ignimissionServices.sendStats();
    }

    @Scheduled(cron = "${scheduled.app.sync.ignimission.domain}")
    public void appSyncIgnimissionDomain() {
        LOGGER.info("Worker : start Application synchronization ignimission domain");
        appSyncServices.appSyncIgnimissionDomain();
    }

    @PostConstruct
    public void initWorkers() throws WorkerException {
        initZipWorkers();
        initSendEmailNotificationUploadDownloadWorkers();
        initSendEmailConfirmationCodeWorkers();
        initTempDataCleanupWorkers();
        initSatisfactionWorkers();
        initStatWorker();
        initSequestre();
    }

    private void initSequestre() {
        LOGGER.info("initSequestre");
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                ThreadPoolTaskExecutor sequestreWorkerExecutor = (ThreadPoolTaskExecutor) sequestreWorkerExecutorFromBean;
                while (true) {
                    try {
                        List<String> returnedBLPOPList = redisManager.subscribeFT(RedisQueueEnum.SEQUESTRE_QUEUE.getValue());
                        if (!CollectionUtils.isEmpty(returnedBLPOPList)) {
                            String enclosureId = returnedBLPOPList.get(1);
                            SequestreWorkerTask cleantask = new SequestreWorkerTask(enclosureId, sequestreBucket, sequestreService);
                            sequestreWorkerExecutor.execute(cleantask);
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error initStatWorker : " + e.getMessage(), e);
                    }
                }
            }
        });
    }

    private void initStatWorker() {
        LOGGER.info("initStatWorker");
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                ThreadPoolTaskExecutor statWorkerExecutor = (ThreadPoolTaskExecutor) statWorkerExecutorFromBean;
                while (true) {
                    try {
                        List<String> returnedBLPOPList = redisManager.subscribeFT(RedisQueueEnum.STAT_QUEUE.getValue());
                        if (!CollectionUtils.isEmpty(returnedBLPOPList)) {
                            String statMessage = returnedBLPOPList.get(1);
                            StatTask task = new StatTask(statMessage, redisManager, statServices);
                            statWorkerExecutor.execute(task);
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error initStatWorker : " + e.getMessage(), e);
                    }
                }
            }
        });
    }

    private void initTempDataCleanupWorkers() {
        LOGGER.info("initTempDataCleanupWorkers");
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                ThreadPoolTaskExecutor TempDataCleanupWorkerExecutor = (ThreadPoolTaskExecutor) tempDataCleanUpWorkerExecutorFromBean;
                while (true) {
                    try {
                        List<String> returnedBLPOPList = redisManager
                                .subscribeFT(RedisQueueEnum.TEMP_DATA_CLEANUP_QUEUE.getValue());
                        if (!CollectionUtils.isEmpty(returnedBLPOPList)) {
                            String enclosureId = returnedBLPOPList.get(1);
                            TempDataCleanupTask task = new TempDataCleanupTask(enclosureId, cleanUpServices);
                            TempDataCleanupWorkerExecutor.execute(task);
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error initTempDataCleanupWorkers : " + e.getMessage(), e);
                    }
                }
            }
        });
    }

    private void initSendEmailConfirmationCodeWorkers() {
        LOGGER.info("initSendEmailConfirmationCodeWorkers");
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                ThreadPoolTaskExecutor SendEmailConfirmationCodeWorkerExecutor = (ThreadPoolTaskExecutor) sendEmailConfirmationCodeWorkerExecutorFromBean;
                while (true) {
                    try {
                        List<String> returnedBLPOPList = redisManager
                                .subscribeFT(RedisQueueEnum.CONFIRMATION_CODE_MAIL_QUEUE.getValue());
                        if (!CollectionUtils.isEmpty(returnedBLPOPList)) {
                            String mailCode = returnedBLPOPList.get(1);
                            SendEmailConfirmationCodeTask task = new SendEmailConfirmationCodeTask(mailCode,
                                    mailConfirmationCodeServices);
                            SendEmailConfirmationCodeWorkerExecutor.execute(task);
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error initSendEmailConfirmationCodeWorkers : " + e.getMessage(), e);
                    }
                }
            }
        });
    }

    private void initSendEmailNotificationUploadDownloadWorkers() {
        LOGGER.info("initSendEmailNotificationUploadDownloadWorkers");
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                ThreadPoolTaskExecutor SendEmailNotificationUploadDownloadWorkerExecutor = (ThreadPoolTaskExecutor) sendEmailNotificationUploadDownloadWorkerExecutorFromBean;
                while (true) {
                    try {
                        List<String> returnedBLPOPList = redisManager.subscribeFT(RedisQueueEnum.MAIL_QUEUE.getValue());
                        if (!CollectionUtils.isEmpty(returnedBLPOPList)) {
                            String enclosureId = returnedBLPOPList.get(1);
                            SendEmailNotificationUploadDownloadTask task = new SendEmailNotificationUploadDownloadTask(
                                    enclosureId, redisManager, mailAvailbleEnclosureServices);
                            SendEmailNotificationUploadDownloadWorkerExecutor.execute(task);
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error initSendEmailNotificationUploadDownloadWorkers : " + e.getMessage(), e);
                    }
                }
            }
        });
    }

    private void initZipWorkers() {
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                ThreadPoolTaskExecutor zipWorkerExecutor = (ThreadPoolTaskExecutor) zipWorkerExecutorFromBean;
                while (true) {
                    try {
                        List<String> returnedBLPOPList = redisManager.subscribeFT(RedisQueueEnum.ZIP_QUEUE.getValue());
                        if (!CollectionUtils.isEmpty(returnedBLPOPList)) {
                            String enclosureId = returnedBLPOPList.get(1);
                            ZipWorkerTask task = new ZipWorkerTask(enclosureId, zipWorkerServices);
                            zipWorkerExecutor.execute(task);
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error initZipWorkers : " + e.getMessage(), e);
                    }
                }
            }
        });
    }

    private void initSatisfactionWorkers() {
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                ThreadPoolTaskExecutor satisfactionWorkerExecutor = (ThreadPoolTaskExecutor) satisfactionWorkerExecutorFromBean;
                while (true) {
                    try {
                        List<String> returnedBLPOPList = redisManager
                                .subscribeFT(RedisQueueEnum.SATISFACTION_QUEUE.getValue());
                        if (!CollectionUtils.isEmpty(returnedBLPOPList)) {
                            RateRepresentation rate = new Gson().fromJson(returnedBLPOPList.get(1),
                                    RateRepresentation.class);
                            SatisfactionTask task = new SatisfactionTask(rate, satisfactionService);
                            satisfactionWorkerExecutor.execute(task);
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error initSatisfactionWorkers : " + e.getMessage(), e);
                    }
                }
            }
        });
    }
}
