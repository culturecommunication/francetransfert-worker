package fr.gouv.culture.francetransfert.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RootData {
    private String name;
    private String extension;
    private String size;
}
