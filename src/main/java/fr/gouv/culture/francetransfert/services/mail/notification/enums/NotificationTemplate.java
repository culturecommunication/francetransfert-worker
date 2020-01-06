package fr.gouv.culture.francetransfert.services.mail.notification.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum NotificationTemplate {
    MAIL_TEMPLATE("mail-template"),
    MAIL_AVAILABLE_SENDER("mail-available-sender-template"),
    MAIL_AVAILABLE_RECIPIENT("mail-available-recipient-template"),
    MAIL_RELAUNCH_RECIPIENT("mail-relaunch-recipient-template"),
    MAIL_RELAUNCH_SENDER("mail-relaunch-sender-template");


    private String value;

}
