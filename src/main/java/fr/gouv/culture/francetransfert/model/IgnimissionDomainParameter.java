package fr.gouv.culture.francetransfert.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class IgnimissionDomainParameter {

    private Integer chunk_size;
    private String data;

    public static IgnimissionDomainParameter of(int chunkSize, IgnimissionDomainDataParameter dataParam){

        return IgnimissionDomainParameter.builder()
                .chunk_size(chunkSize)
                .data(dataParam.toString())
                .build();
    }

}