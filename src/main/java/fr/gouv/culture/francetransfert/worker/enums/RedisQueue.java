package fr.gouv.culture.francetransfert.worker.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RedisQueue {
    MAIL_QUEUE("email-notification-queue"),
    ZIP_QUEUE("zip-worker-queue");


    private String value;
}
