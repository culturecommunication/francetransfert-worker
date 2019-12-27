package fr.gouv.culture.francetransfert.services.mail.notification;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;

@Service
public class MailContentBuilder {
    private TemplateEngine templateEngine;

    private final static String logo_france_transfert = "/static/images/france_transfert.PNG";

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
        if(obj != null){
            String jsonInString = new Gson().toJson(obj);
            jsonObject = new JsonParser().parse(jsonInString).getAsJsonObject();
        }
        Context context = new Context();
        context.setVariable("enclosure", jsonObject);
        context.setVariable("france_transfert", new ClassPathResource(logo_france_transfert));
        return templateEngine.process(tempName, context);
    }

}
