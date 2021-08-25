package fr.gouv.culture.francetransfert.exception;

public class ClamAVException  extends Exception {

    public ClamAVException(String message) {
        super(message);
    }

    public ClamAVException(String message, Throwable cause) {
        super(message, cause);
    }
}
