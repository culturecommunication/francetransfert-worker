package fr.gouv.culture.francetransfert.services.mail.notification;

import fr.gouv.culture.francetransfert.security.WorkerException;
import fr.gouv.culture.francetransfert.services.mail.notification.enums.NotificationTemplate;
import fr.gouv.culture.francetransfert.utils.WorkerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

@Component
public class MailNotificationServices {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailNotificationServices.class);

//    properties mail France transfert SMTP
    @Value("${spring.mail.username}")
    private String franceTransfertMail;

    @Value("${url.download.api}")
    private String urlDownloadApi;

    @Autowired
    private JavaMailSender emailSender;

    @Autowired
    private MailContentBuilder htmlBuilder;

    public void prepareAndSend(String to, String subject, Object object, String templateName) {
        try {
            LOGGER.debug("start send emails for enclosure ");
            templateName = templateName != null && !templateName.isEmpty() ? templateName : NotificationTemplate.MAIL_TEMPLATE.getValue();
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true,"UTF-8");
            helper.setFrom(franceTransfertMail);
            helper.setTo(to);
            helper.setSubject(subject);
            String htmlContent = htmlBuilder.build(object, templateName);
            helper.setText(htmlContent, true);
            emailSender.send(message);
        } catch (MessagingException | IOException e) {
            throw new WorkerException("Enclosure build error");
        }
    }

    public void prepareAndSend(String to, String subject, String body, String templateName) {
        try {
            LOGGER.debug("start send emails for enclosure ");
            templateName = templateName != null && !templateName.isEmpty() ? templateName : NotificationTemplate.MAIL_TEMPLATE.getValue();
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true,"UTF-8");
            helper.setFrom(franceTransfertMail);
            helper.setTo(to);
            helper.setSubject(subject);
            String htmlContent = htmlBuilder.build(body, templateName);
            helper.setText(htmlContent, true);
            emailSender.send(message);
        } catch (MessagingException e) {
            throw new WorkerException("Enclosure build error");
        }
    }

    public String generateUrlForDownload(String enclosureId, String recipientMail, String recipientId) {
        try {
            return "http://" + urlDownloadApi + "?enclosure=" + enclosureId + "&recipient=" + WorkerUtils.base64Encoder(recipientMail) + "&token=" + recipientId;
        } catch (UnsupportedEncodingException e) {
            throw new WorkerException("Download url error");
        }
    }

}
