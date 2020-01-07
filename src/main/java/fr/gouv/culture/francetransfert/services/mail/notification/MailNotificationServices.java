package fr.gouv.culture.francetransfert.services.mail.notification;

import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.RedisManager;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.enums.EnclosureKeysEnum;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.enums.RedisKeysEnum;
import com.opengroup.mc.francetransfert.api.francetransfert_metaload_api.utils.DateUtils;
import com.opengroup.mc.francetransfert.api.francetransfert_storage_api.StorageManager;
import fr.gouv.culture.francetransfert.security.JwtRequest;
import fr.gouv.culture.francetransfert.security.JwtTokenUtil;
import fr.gouv.culture.francetransfert.security.WorkerException;
import fr.gouv.culture.francetransfert.services.mail.notification.enums.NotificationTemplate;
import fr.gouv.culture.francetransfert.model.Enclosure;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
public class MailNotificationServices {
//  subject mail
    @Value("${subject.sender}")
    private String subjectSender;

    @Value("${subject.recipient}")
    private String subjectRecipient;

    @Value("${subject.relaunch.recipient}")
    private String subjectRelaunchRecipient;

    @Value("${subject.relaunch.sender}")
    private String subjectRelaunchSender;

//    properties mail France transfert SMTP
    @Value("${spring.mail.username}")
    private String franceTransfertMail;

    @Value("${url.download.api}")
    private String urlDownloadApi;

    @Value("${relaunch.mail.days}")
    private int relaunchDays;

    private final static String logo_france_transfert = "/static/images/france_transfert.PNG";

    @Autowired
    private JavaMailSender emailSender;

    @Autowired
    private MailContentBuilder htmlBuilder;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    // Send Mails to snder and recipients
    public void sendMailsAvailableEnclosure(Enclosure enclosure) throws Exception{
        sendToSenderEnclosure(enclosure, subjectSender, NotificationTemplate.MAIL_AVAILABLE_SENDER.getValue());
        sendToRecipients(enclosure, subjectRecipient, NotificationTemplate.MAIL_AVAILABLE_RECIPIENT.getValue());
    }

    // Send mails relaunch to recipients
    public void sendMailsRelaunch() throws Exception {
        RedisManager redisManager = RedisManager.getInstance();
        redisManager.smembersString(RedisKeysEnum.FT_ENCLOSURE_DATES.getKey("")).stream().forEach(date -> {
            redisManager.smembersString(RedisKeysEnum.FT_ENCLOSURE_DATE.getKey(date)).stream().forEach(enclosureId -> {
                try {
                    LocalDateTime exipireEnclosureDate = DateUtils.convertStringToLocalDateTime(redisManager.getHgetString(enclosureId, EnclosureKeysEnum.EXPIRED_TIMESTAMP.getKey()));
                    if (LocalDate.now().equals(exipireEnclosureDate.toLocalDate().minusDays(relaunchDays))) {
                        Enclosure enclosure = Enclosure.build(enclosureId);
                        sendToSenderEnclosure(enclosure, subjectRelaunchSender, NotificationTemplate.MAIL_RELAUNCH_SENDER.getValue());
                        sendToRecipients(enclosure, subjectRelaunchRecipient, NotificationTemplate.MAIL_RELAUNCH_RECIPIENT.getValue());
                    }
                } catch (Exception e) {
                    throw new WorkerException("Enclosure build error");
                }
            });
        });
    }

    // Send mails to sender
    public void sendToSenderEnclosure(Enclosure enclosure, String subject, String templateName) throws Exception{
        prepareAndSend(enclosure.getSender(), subject, enclosure, templateName);
    }

    // Send mails to recipients
    public void sendToRecipients(Enclosure enclosure, String subject, String templateName) throws Exception{
        subject = enclosure.getSender() + " " + subject;
        List<String> recipients = enclosure.getRecipients();
        if (!CollectionUtils.isEmpty(recipients)) {
            for (String recipient: recipients) {
                String token = jwtTokenUtil.generateTokenDownload(new JwtRequest(enclosure.getGuid(), recipient, enclosure.isWithPassword()));
                enclosure.setUrlDownload(urlDownloadApi + "/" + token);
                prepareAndSend(recipient, subject, enclosure, templateName);
            }
        }
    }

    public void prepareAndSend(String to, String subject, Object object, String templateName) {
        try {
            log.debug("start send emails for enclosure ");
            templateName = templateName != null && !templateName.isEmpty() ? templateName : NotificationTemplate.MAIL_TEMPLATE.getValue();
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true,"UTF-8");
            helper.setFrom(franceTransfertMail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.addAttachment("france_transfert", new ClassPathResource(logo_france_transfert));
            String htmlContent = htmlBuilder.build(object, templateName);
            helper.setText(htmlContent, true);
            emailSender.send(message);
        } catch (MessagingException | IOException e) {
            e.printStackTrace();
        }
    }



}