package fr.gouv.culture.francetransfert.services.mail.notification;

import java.io.IOException;

import javax.annotation.PostConstruct;

import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Service
public class MailContentBuilder {
	private TemplateEngine templateEngine;
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

	@Autowired
	public MailContentBuilder(TemplateEngine templateEngine) {
		this.templateEngine = templateEngine;
	}

	public String build(String message, String tempName) {
		Context context = new Context();
		context.setVariable("message", message);
		return templateEngine.process(tempName, context);
	}

	public String build(Object obj, String tempName) throws IOException {
		JsonObject jsonObject = null;
		if (obj != null) {
			String jsonInString = new Gson().toJson(obj);
			jsonObject = new JsonParser().parse(jsonInString).getAsJsonObject();
		}
		Context context = new Context();
		if(tempName.equalsIgnoreCase(serviceContact)){
			context = buildFormulaireContact(jsonObject);
		}else{
		context.setVariable("enclosure", jsonObject);
		context.setVariable("logoFt", logoFT);

		context.setVariable("logoFtRessource", "data:image/png;base64," + IMG_RESSOURCE);
		context.setVariable("fileIcone", fileIcone);
		context.setVariable("folerIcone", folderIcone);
		context.setVariable("accessButton", accessButtonImg);
		}
		return templateEngine.process(tempName, context);
	}

	public Context buildFormulaireContact(JsonObject obj){

		Context context = new Context();
		context.setVariable("contact", obj);
		return  context;
	}

}
