package fr.gouv.culture.francetransfert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FranceTransfertWorkerStarter {
    public static void main(String[] args) {
        SpringApplication.run(FranceTransfertWorkerStarter.class, args);
    }

//    @Bean
//    public MessageSource messageSource() {
//        ReloadableResourceBundleMessageSource messageSource= new ReloadableResourceBundleMessageSource();
//        messageSource.setBasename("classpath:messages");
//        messageSource.setDefaultEncoding("UTF-8");
//        return messageSource;
//    }
}
