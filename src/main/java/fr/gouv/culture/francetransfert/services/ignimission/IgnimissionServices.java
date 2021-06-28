package fr.gouv.culture.francetransfert.services.ignimission;

import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RedisKeysEnum;
import fr.gouv.culture.francetransfert.model.*;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;


@Service
@Slf4j
public class IgnimissionServices {
    private static final Logger LOGGER = LoggerFactory.getLogger(IgnimissionServices.class);

    @Value("${ignimission.uri.base}")
    private String baseUri;

    @Value("${ignimission.path.auth}")
    private String authentifcationPath;

    @Value("${ignimission.path.domain}")
    private String domainPath;

    @Value("${ignimission.grant.type}")
    private String grantType;

    @Value("${ignimission.client.id}")
    private String clientId;

    @Value("${ignimission.client.secret}")
    private String clientSecret;

    @Value("${ignimission.domain.asam_product_filter}")
    private String asamProductFilter;

    @Value("${ignimission.domain.asam_autorise}")
    private String asamAutorise;

    @Value("${ignimission.domain.chunk_size}")
    private int chunkSize;


    @Autowired
    private RestClientUtils restClientUtils;

    @Autowired
    private RedisManager redisManager;


    /**
     * Update FT email domains from Ignimission ws
     */
    public void updateDomains() {

        try {
            IgnimissionAuthenticationResponse ignimissionAuth = getAuthentication();

            if (Objects.nonNull(ignimissionAuth)) {
                IgnimissionDomainParameter ignimissionDomainParameter = IgnimissionDomainParameter.of(chunkSize,
                        new IgnimissionDomainDataParameter(asamProductFilter, asamAutorise, LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)));
                IgnimissionDomainResponse ignimissionDomainResponse = restClientUtils.getAsamExtensions(ignimissionDomainParameter,
                        ignimissionAuth.getAccessToken(),
                        baseUri+domainPath,
                        HttpMethod.POST);

                if (Objects.nonNull(ignimissionDomainResponse) && ignimissionDomainResponse.getNbItems() > 0) {
                    LOGGER.debug("================================> worker Ignimission domains size {} ", ignimissionDomainResponse.getNbItems());

                    if (ignimissionDomainResponse.getNbItems() > 0 && !CollectionUtils.isEmpty(ignimissionDomainResponse.getDomainsAsList())) {
                        // LOGGER.debug("================================> worker Ignimission domains  sanitize size {}", ignimissionDomainResponse.getDomainsAsList());

                        ignimissionDomainResponse.getDomainsAsList().forEach(domain -> {
                            redisManager.saddString(RedisKeysEnum.FT_DOMAINS_MAILS_TMP.getKey(""), domain);
                        });

                        // Domains update from TMP list
                        redisManager.sInterStore(RedisKeysEnum.FT_DOMAINS_MAILS_MAILS.getKey(""), RedisKeysEnum.FT_DOMAINS_MAILS_TMP.getKey(""));

                        redisManager.deleteKey(RedisKeysEnum.FT_DOMAINS_MAILS_TMP.getKey(""));
                        LOGGER.info("================================> Redis domains mails cache size {} ", redisManager.smembersString(RedisKeysEnum.FT_DOMAINS_MAILS_MAILS.getKey("")).size());

                    }
                }

            }
        } catch (Exception ex) {
            LOGGER.error("================================> worker Ignimission domain update error {} ", ex.getMessage());
        }
    }

    /**
     * Get FT authentification credentials from Ignimission ws
     *
     * @return
     */
    private IgnimissionAuthenticationResponse getAuthentication() {
        IgnimissionParameter ignimissionParameter = new IgnimissionParameter(grantType, clientId, clientSecret);
        return restClientUtils.getAuthentication(ignimissionParameter, baseUri+authentifcationPath, HttpMethod.POST);
    }

}
