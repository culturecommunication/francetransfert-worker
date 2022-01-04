package fr.gouv.culture.francetransfert.services.ignimission;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import fr.gouv.culture.francetransfert.model.IgnimissionAuthenticationResponse;
import fr.gouv.culture.francetransfert.model.IgnimissionDomainParameter;
import fr.gouv.culture.francetransfert.model.IgnimissionDomainResponse;
import fr.gouv.culture.francetransfert.model.IgnimissionParameter;

@Component
public class RestClientUtils {

	Logger LOGGER = LoggerFactory.getLogger(RestClientUtils.class);

	@Autowired
	private RestTemplateBuilder restTemplateBuilder;

	/**
	 * Ignimission Authentication REST CALL
	 *
	 * @param parameter
	 * @param requestUri
	 * @param httpMethod
	 * @return
	 */
	public IgnimissionAuthenticationResponse getAuthentication(IgnimissionParameter parameter, String requestUri,
			HttpMethod httpMethod) {

		try {
			LOGGER.info("================================> Ignimission : get authentication token from [{}] ",
					requestUri);
			RestTemplate restTemplate = restTemplateBuilder.messageConverters(new MappingJackson2HttpMessageConverter())
					.errorHandler(new IgnimissionErrorHandler()).build();

			ResponseEntity<IgnimissionAuthenticationResponse> response = restTemplate.exchange(requestUri, httpMethod,
					getHttpEntity(parameter), IgnimissionAuthenticationResponse.class);
			return response.getBody();

		} catch (HttpMessageNotReadableException e) {
			LOGGER.error("Ignimission AUth CALL ERROR {} ", e.getMessage(), e);
		}

		return null;
	}

	/**
	 * Ignimission Get domains Rest call
	 *
	 * @param ignimissionDomainParameter
	 * @param token
	 * @param requestUri
	 * @param httpMethod
	 * @return
	 */
	public IgnimissionDomainResponse getAsamExtensions(IgnimissionDomainParameter ignimissionDomainParameter,
			String token, String requestUri, HttpMethod httpMethod) {

		ResponseEntity<IgnimissionDomainResponse[]> response = null;
		try {

			LOGGER.info("================================> worker Ignimission domain update from [{}]", requestUri);
			RestTemplate restTemplate = restTemplateBuilder.messageConverters(new MappingJackson2HttpMessageConverter())
					.errorHandler(new IgnimissionErrorHandler()).build();

			response = restTemplate.exchange(requestUri, httpMethod,
					getHttpEntityWithCredentials(ignimissionDomainParameter, token), IgnimissionDomainResponse[].class);
		} catch (HttpMessageNotReadableException e) {
			LOGGER.error("Worker Ignimission domain update ERROR {} ", e.getMessage(), e);
		}

		return Objects.nonNull(response) ? Arrays.stream(response.getBody()).findFirst().orElse(null) : null;
	}

	public void sendIgniStat(String token, String requestUri, HttpMethod httpMethod, File file) {

		ResponseEntity<IgnimissionDomainResponse[]> response = null;
		try (FileInputStream finput = new FileInputStream(file)) {

			LOGGER.info("================================> worker Ignimission domain update from [{}]", requestUri);
			RestTemplate restTemplate = restTemplateBuilder.messageConverters(new MappingJackson2HttpMessageConverter())
					.errorHandler(new IgnimissionErrorHandler()).build();

			HttpHeaders headers = new HttpHeaders();
			headers.add("Authorization", "Bearer " + token);
			headers.setContentType(MediaType.MULTIPART_FORM_DATA);

			LinkedMultiValueMap<String, String> csvHeaderMap = new LinkedMultiValueMap<>();
			csvHeaderMap.add("Content-type", "text/csv");
			HttpEntity<byte[]> doc = new HttpEntity<byte[]>(IOUtils.toByteArray(finput), csvHeaderMap);

			LinkedMultiValueMap<String, Object> multipartReqMap = new LinkedMultiValueMap<>();
			multipartReqMap.add("file", doc);

			HttpEntity<LinkedMultiValueMap<String, Object>> reqEntity = new HttpEntity<>(multipartReqMap, headers);

//				HttpEntity<?> headerEntity = getHttpEntityWithCredentials(null, token);
//				MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
//				body.add("file", file);
//				headerEntity.getHeaders().setContentType(MediaType.MULTIPART_FORM_DATA);

			response = restTemplate.exchange(requestUri, httpMethod, reqEntity, IgnimissionDomainResponse[].class);

		} catch (HttpMessageNotReadableException | IOException e) {
			LOGGER.error("Worker Ignimission domain update ERROR {} ", e.getMessage(), e);
		}

		return;
	}

	private <T> HttpEntity<?> getHttpEntityWithCredentials(T reqBody, String token) {

		return new HttpEntity<>(reqBody, getHeadersWithCredentials(token));
	}

	private <T> HttpEntity<?> getHttpEntity(T reqBody) {

		return new HttpEntity<>(reqBody, getHeaders());
	}

	private HttpHeaders getHeadersWithCredentials(String token) {

		HttpHeaders headers = new HttpHeaders();
		headers.add("Authorization", "Bearer " + token);
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
		return headers;
	}

	private HttpHeaders getHeaders() {

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

		return headers;
	}

}
