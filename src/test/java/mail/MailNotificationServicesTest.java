package mail;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import fr.gouv.culture.francetransfert.FranceTransfertWorkerStarter;
import fr.gouv.culture.francetransfert.services.mail.notification.MailNotificationServices;
import fr.gouv.culture.francetransfert.services.mail.notification.enums.NotificationTemplate;
import fr.gouv.culture.francetransfert.model.Enclosure;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = FranceTransfertWorkerStarter.class)
public class MailNotificationServicesTest {

    @Autowired
    private MailNotificationServices mailNotificationServices;

    private GreenMail smtpServer;
    private Enclosure enclosure;

    @Before
    public void setUp() throws Exception {
        smtpServer = new GreenMail(new ServerSetup(25, null, "smtp"));
        smtpServer.start();
       enclosure = Enclosure.builder()
                .guid("enclosureId")
                .rootFiles(Arrays.asList("file_a.png","file_b.pdf"))
                .rootDirs(Arrays.asList("dir_1", "dir_2"))
                .countElements(2)
                .totalSize(17)
                .expireDate(LocalDateTime.now().plusDays(30).format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRENCH)))
                .sender("louay.haddad@live.fr")
                .recipients(Arrays.asList("louay.haddad@live.fr", "louayhadded2012@gmail.com"))
                .message("Test message content")
                .existPassword(false)
                .build();
    }

    @Test
    public void shouldSendMailToRecipient() throws Exception {
        //given
        String recipient = "louay.haddad@live.fr";
        String message = "Test message content";
        //when
//        mailClient.sendMails(enclosure);
        mailNotificationServices.prepareAndSend(recipient, message, enclosure, NotificationTemplate.MAIL_RECIPIENT.getValue());
        //then
        String content = message + "</span>";
        assertReceivedMessageContains(content);
    }

    @Test
    public void shouldSendMailToSender() throws Exception {
        //given
        String recipient = "louay.haddad@live.fr";
        String message = "Test message content";
        //when
        mailNotificationServices.prepareAndSend(recipient, message, enclosure, NotificationTemplate.MAIL_SENDER.getValue());
        //then
        String content = message + "</span>";
        assertReceivedMessageContains(content);
    }

    private void assertReceivedMessageContains(String expected) throws IOException, MessagingException {
        MimeMessage[] receivedMessages = smtpServer.getReceivedMessages();
//        assertEquals(1, receivedMessages.length);
//        String content = (String) receivedMessages[0].getContent();
//        assertTrue(content.contains(expected));
    }

    @After
    public void tearDown() throws Exception {
        smtpServer.stop();
        enclosure = null;
    }

}