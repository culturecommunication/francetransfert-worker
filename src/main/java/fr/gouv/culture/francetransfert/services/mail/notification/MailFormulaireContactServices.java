package fr.gouv.culture.francetransfert.services.mail.notification;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import fr.gouv.culture.francetransfert.core.exception.MetaloadException;
import fr.gouv.culture.francetransfert.core.model.FormulaireContactData;
import fr.gouv.culture.francetransfert.security.WorkerException;

@Service
public class MailFormulaireContactServices {

	private static final Logger LOGGER = LoggerFactory.getLogger(MailFormulaireContactServices.class);

	@Autowired
	private MailNotificationServices mailNotificationServices;

	@Value("${subject.mail.contact}")
	private String subjectMailContact;

	public void sendMailContact(FormulaireContactData formulaireContactData, String templateName)
			throws WorkerException, MetaloadException {

		if (StringUtils.isNotBlank(formulaireContactData.getFrom())) {
			subjectMailContact = subjectMailContact.concat(formulaireContactData.getFrom());
		}
		if (StringUtils.isNotBlank(formulaireContactData.getSubject())) {
			subjectMailContact = subjectMailContact.concat(":").concat(formulaireContactData.getSubject());
		}

		LOGGER.info(" send mail to service contact by {} ", formulaireContactData.getFrom());
		mailNotificationServices.prepareAndSendMailContact(formulaireContactData.getFrom(), subjectMailContact,
				formulaireContactData, templateName);

	}
}
