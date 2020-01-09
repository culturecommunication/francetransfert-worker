/*
package workers;

import org.slf4j.Logger;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class MailSender {
	
	private String sender;
	private String host;
	private String port;
	private String textTemplate;
	private Logger logger;
	
	private final static String PROD_ENV_CONFIG_FILE_NAME = "mailsender.prod.config.properties";
	private final static String ENV_CONFIG_FILE_NAME = "env.config.properties";
	private final static String DEV_ENV_CONFIG_FILE_NAME = "mailsender.dev.config.properties";
	private final static String PROD_ENV_NAME = "prod";
	private final static String DEV_ENV_NAME = "dev";
	
	public MailSender() {
		try {
			setUpProps();
		} catch (IOException e) {
			
		}
	}
	
	private void setUpProps() throws IOException {
		InputStream inputStreamEnv = null;
		InputStream inputStream = null;
		try {
			Properties propEnv = new Properties();
			Properties prop = new Properties();
			String propFileEnvName = ENV_CONFIG_FILE_NAME;
			String propFileName = "";
 
			inputStreamEnv = getClass().getClassLoader().getResourceAsStream(propFileEnvName);
 
			if (inputStreamEnv != null) {
				propEnv.load(inputStreamEnv);
			} else {
				throw new FileNotFoundException("property file '" + propFileEnvName + "' not found in the classpath");
			}
			
			String env = propEnv.getProperty("env");
			
			if(env != null && env.equalsIgnoreCase(PROD_ENV_NAME)) {
				propFileName = PROD_ENV_CONFIG_FILE_NAME;
			}else if(env != null && env.equalsIgnoreCase(DEV_ENV_NAME)) {
				propFileName = DEV_ENV_CONFIG_FILE_NAME;
			}
			
			inputStream = getClass().getClassLoader().getResourceAsStream(propFileName);
			 
			if (inputStream != null) {
				prop.load(inputStream);
			} else {
				throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
			}
			
			setHost(prop.getProperty("host"));
			setPort(prop.getProperty("port"));
			setSender(prop.getProperty("sender"));
			setTextTemplate(prop.getProperty("deletionNotificationEmailTemplate"));
 
		} catch (Exception e) {
			
		} finally {
			inputStream.close();
			inputStreamEnv.close();
		}
	}

	public void sendDeletionNotificationMail(String emailAddress) {
		String subject = getDeletionNotificationMailSubject();
		prepareAndSendMail(subject, emailAddress);
	}

	private String getDeletionNotificationMailSubject() {
		// TODO Auto-generated method stub
		return null;
	}

	public void sendUploadSuccessNotificationMail(String emailAddress) {
		String subject = getUploadSuccessMailSubject();
		prepareAndSendMail(subject, emailAddress);
	}

	private String getUploadSuccessMailSubject() {
		// TODO Auto-generated method stub
		return "";
	}

	public void sendDownloadLinkMail(String emailAddress) {
		String subject = getDownloadLinkMailSubject();
		prepareAndSendMail(subject, emailAddress);
	}
	
	private String getDownloadLinkMailSubject() {
		// TODO Auto-generated method stub
		return "";
	}

	private Session getSession() {
		Properties properties = System.getProperties();
		properties.setProperty("mail.smtp.host", getHost());
		properties.setProperty("mail.smtp.port", "1025");
		return Session.getDefaultInstance(properties);
	}

	private void prepareAndSendMail(String subject, String emailAddress) {
		emailAddress = "tahtahussein@gmai.com";
		Session session = getSession();
		try {
			MimeMessage message = new MimeMessage(session);
			message.setFrom(new InternetAddress(getSender()));
			message.addRecipient(Message.RecipientType.TO, new InternetAddress(emailAddress));
			message.setSubject(subject);
//			message.setText(getTextTemplate());
			message.setText(subject);
			Transport.send(message);

		} catch (MessagingException e) {
			e.printStackTrace();
		}
	}

	public String getSender() {
		return sender;
	}

	public void setSender(String sender) {
		this.sender = sender;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getPort() {
		return port;
	}

	public void setPort(String port) {
		this.port = port;
	}

	public String getTextTemplate() {
		return textTemplate;
	}

	public void setTextTemplate(String textTemplate) {
		this.textTemplate = textTemplate;
	}
}
*/
