package fr.gouv.culture.francetransfert.model;

import lombok.Data;

@Data
public class GlimpsResultResponse {

	private boolean done;
	private boolean is_malware;
	private boolean status;
	private String error;

}
