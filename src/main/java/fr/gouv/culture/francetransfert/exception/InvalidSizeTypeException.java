/*
  * Copyright (c) Ministère de la Culture (2022) 
  * 
  * SPDX-License-Identifier: Apache-2.0 
  * License-Filename: LICENSE.txt 
  */

package fr.gouv.culture.francetransfert.exception;

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