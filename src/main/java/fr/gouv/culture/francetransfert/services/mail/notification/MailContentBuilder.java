package fr.gouv.culture.francetransfert.services.mail.notification;

import java.io.IOException;
import java.util.Collections;
import java.util.Locale;

import javax.annotation.PostConstruct;

import com.google.gson.GsonBuilder;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;
import org.thymeleaf.spring5.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class MailContentBuilder {
	
	   private static final Logger LOGGER = LoggerFactory.getLogger(MailContentBuilder.class);
	@Autowired
	private TemplateEngine mailTemplateEngine;
	

	private String serviceContact = "mail-contact-template";

	@Value("${mail.image.ft.logo}")
	private String logoFT;

	@Value("${mail.image.ft.accessbutton}")
	private String accessButtonImg;

	@Value("${mail.image.ft.file}")
	private String fileIcone;

	@Value("${mail.image.ft.folder}")
	private String folderIcone;

	private static String IMG_RESSOURCE;

	@PostConstruct
	private void setImageData() {
		try {
			IMG_RESSOURCE = Base64.encodeBase64String(
					new ClassPathResource("static/images/logo-ft.png").getInputStream().readAllBytes());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}



	public String build(String message, String tempName, final Locale locale) {
		Context context = new Context(locale);
		context.setVariable("message", message);
		return mailTemplateEngine.process(tempName, context);
	}

	public String build(Object obj, String tempName, final Locale locale) throws IOException {
		JsonObject jsonObject = null;
		if (obj != null) {
			String jsonInString = new Gson().toJson(obj);
			jsonObject = new JsonParser().parse(jsonInString).getAsJsonObject();
		}
		Context context = new Context(locale);

		if(tempName.equalsIgnoreCase(serviceContact)){
			context = buildFormulaireContact(jsonObject);
		}else{
		context.setVariable("enclosure", jsonObject);



		context.setVariable("fileIcone", fileIcone);
		context.setVariable("folerIcone", folderIcone);
		context.setVariable("accessButton", accessButtonImg);
		}

		context.setVariable("logoFt", logoFT);
		context.setVariable("logoFtRessource", "data:image/png;base64," + IMG_RESSOURCE);
		return mailTemplateEngine.process(tempName, context);
	}

	public Context buildFormulaireContact(JsonObject obj){

		Context context = new Context();
		context.setVariable("contact", obj);
		return  context;
	}
	
	
	 
	 

	
	    
	    
	    
	    


}
