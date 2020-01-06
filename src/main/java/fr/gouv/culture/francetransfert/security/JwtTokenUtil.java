package fr.gouv.culture.francetransfert.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.Serializable;
import java.security.Key;
import java.security.KeyStore;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Component
public class JwtTokenUtil implements Serializable {

	private static final long serialVersionUID = -2550185165626007488L;

	@Value("${jwt.token-validity-in-hours}")
	private int jwtTokenValidity;

	@Value("${security.jwt.secret.path}")
	private String keyPath;

	@Value("${security.jwt.secret.alias}")
	private String alias;

	@Value("${security.jwt.secret.storepass}")
	private String storePass;

	@Value("${security.jwt.secret.keypass}")
	private String keyPass;

	/**
	 * Create Token Download FranceTransfert
	 * @param jwtToken
	 * @return
	 */
	public String generateTokenDownload(JwtRequest jwtToken) throws ParseException {
		return Jwts.builder()
				.claim("mailRecipient", jwtToken.getMailRecipient())
				.claim("enclosureId", jwtToken.getEnclosureId())
				.claim("withPassword", jwtToken.isWithPassword())
				.setIssuedAt(new Date(System.currentTimeMillis()))
				.setExpiration(new Date(System.currentTimeMillis() + jwtTokenValidity * 60 * 60 * 1000))
				.signWith(getKey(), SignatureAlgorithm.HS512)
				.compact();
	}

	/**
	 * Create Key
	 * @param
	 * @return
	 */
	public Key getKey() {
		try {
			FileInputStream in = new FileInputStream(keyPath);
			KeyStore ks = KeyStore.getInstance("jceks");
			ks.load(in, (storePass).toCharArray());
			return ks.getKey(alias, keyPass.toCharArray());
		} catch (Exception var5) {
			throw new WorkerException("access denied");
		}
	}
}