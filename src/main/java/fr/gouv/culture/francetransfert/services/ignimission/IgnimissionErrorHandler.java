package fr.gouv.culture.francetransfert.services.ignimission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.ResponseErrorHandler;

import java.io.IOException;

public class IgnimissionErrorHandler implements ResponseErrorHandler {

    Logger LOGGER = LoggerFactory.getLogger(IgnimissionErrorHandler.class);

    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
        return new DefaultResponseErrorHandler().hasError(response);
    }

    @Override
    public void handleError(ClientHttpResponse response) throws IOException {

        if (response.getStatusCode().series() == HttpStatus.Series.SERVER_ERROR) {
            // http status code e.g. `500 INTERNAL_SERVER_ERROR`
            LOGGER.error("============> Ignimission ERROR {}", response.getStatusCode());

        } else if (response.getStatusCode().series() == HttpStatus.Series.CLIENT_ERROR) {
            // handle 4xx errors
            // raw http status code e.g `404`
            // LOGGER.error("============> Ignimission ERROR {}",response.getRawStatusCode());

            // http status code e.g. `404 NOT_FOUND`
            LOGGER.error("============> Ignimission ERROR {}", response.getStatusCode());

           /* // get response body
            LOGGER.error("============> Ignimission ERROR {}",response.getBody());

            // get http headers
            HttpHeaders headers = response.getHeaders();
            LOGGER.error("============> Ignimission ERROR {}",headers.get("Content-Type"));
            LOGGER.error("============> Ignimission ERROR {}",headers.get("Server"));*/
        }
    }
}
