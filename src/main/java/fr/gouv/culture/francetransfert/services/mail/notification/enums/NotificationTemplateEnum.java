package fr.gouv.culture.francetransfert.services.mail.notification.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum NotificationTemplateEnum {
    MAIL_TEMPLATE("mail-template"),
    MAIL_AVAILABLE_SENDER("mail-available-sender-template"),
    MAIL_AVAILABLE_RECIPIENT("mail-available-recipient-template"),
    MAIL_RELAUNCH_RECIPIENT("mail-relaunch-recipient-template"),
    MAIL_RELAUNCH_SENDER("mail-relaunch-sender-template"),
    MAIL_ENCLOSURE_NO_AVAILBLE_RECIPIENTS("mail-enclosure-no-availble-recipient-template"),
    MAIL_ENCLOSURE_NO_AVAILBLE_SENDER("mail-enclosure-no-availble-sender-template"),
    MAIL_CONFIRMATION_CODE("mail-confirmation-code-template"),
    MAIL_DOWNLOAD_SENDER_TEMPLATE("mail-download-sender-template"),
    MAIL_VIRUS_SENDER("mail-virus-sender-template"),
    MAIL_VIRUS_ERROR_SENDER("mail-virus-error-sender-template");


    private String value;

}
