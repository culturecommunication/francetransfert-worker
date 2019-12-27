package fr.gouv.culture.francetransfert.services.mail.notification.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum NotificationTemplate {
    MAIL_TEMPLATE("mail-template"),
    MAIL_SENDER("mail-sender-template"),
    MAIL_RECIPIENT("mail-recipient-template");


    private String value;

}
