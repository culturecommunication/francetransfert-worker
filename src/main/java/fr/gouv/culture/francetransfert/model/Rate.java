package fr.gouv.culture.francetransfert.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class Rate {

    private String mailAdress;

    private int satisfaction;

    private String message;
}
