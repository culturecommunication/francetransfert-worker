package fr.gouv.culture.francetransfert.security;

public class WorkerException extends RuntimeException {

    public WorkerException(String extension) {
        super(extension);
    }

}