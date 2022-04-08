package fr.gouv.culture.francetransfert.model;

import java.util.Locale;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConfirmationCode {

	private String code;

	private String mail;

	private String dateExpiration;

	private int sessionTime;

	private int codeTime;
    
    //private String currentLanguage;
}
