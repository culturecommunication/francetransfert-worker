package fr.gouv.culture.francetransfert.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@Builder
@ToString
public class IgnimissionParameter {
    private String grant_type;
    private String client_id;
    private String client_secret;
}