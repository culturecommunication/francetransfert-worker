package fr.gouv.culture.francetransfert.security;

public class InvalidSizeTypeException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7102070610446811207L;

	public InvalidSizeTypeException(String extension, Throwable e) {
		super(extension, e);
	}

	public InvalidSizeTypeException(String extension) {
		super(extension);
	}

}