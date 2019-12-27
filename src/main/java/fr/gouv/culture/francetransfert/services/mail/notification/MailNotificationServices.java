package fr.gouv.culture.francetransfert.services.mail.notification;

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
import java.util.List;

@Service
@Slf4j
public class MailNotificationServices {

    @Autowired
    private JavaMailSender emailSender;

    @Autowired
    private MailContentBuilder htmlBuilder;

    @Value("${subject.recipient}")
    private String subjectRecipient;

    @Value("${subject.sender}")
    private String subjectSender;

    @Value("${spring.mail.username}")
    private String franceTransfertMail;


    private final static String logo_france_transfert = "/static/images/france_transfert.PNG";

    public void sendMails(Enclosure enclosure) throws Exception{
        sendToSenderEnclosure(enclosure, NotificationTemplate.MAIL_SENDER.getValue());
        sendToRecipients(enclosure, NotificationTemplate.MAIL_RECIPIENT.getValue());
    }

    public void sendToSenderEnclosure(Enclosure enclosure, String templateName) throws Exception{
        prepareAndSend(enclosure.getSender(), subjectSender, enclosure, templateName);
    }

    public void sendToRecipients(Enclosure enclosure, String templateName) throws Exception{
        String subject = enclosure.getSender() + " " + subjectRecipient;
        List<String> recipients = enclosure.getRecipients();
        if (!CollectionUtils.isEmpty(recipients)) {
            for (String recipient: recipients) {
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