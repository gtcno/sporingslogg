package no.nav.sporingslogg.oidc;

import java.io.IOException;
import java.security.Key;

import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.keys.resolvers.VerificationKeyResolver;
import org.jose4j.lang.UnresolvableKeyException;

public class OidcAuthenticate {

	// ID-porten gir ut tokens med "acr":"Level4" hvis innlogging er skjedd med BankID e.l.
	private static final String AUTH_LEVEL_CLAIM = "acr";
    private static final String AUTH_LEVEL_4 = "Level4";
    
	// Aksepterer ikke "evigvarende" tokens
	private static final int MAX_VALIDITY_SECONDS = 3600;
	// Godta slingringsmonn pga unøyaktige klokker
	private static final int AKSEPTERT_TIDSFORSKJELL_SEKUNDER = 30;

	private final Key publicKey;
	private final VerificationKeyResolver keyResolver;
	private final String issuer;
	
	public OidcAuthenticate(Key publicKey, String issuer) {
		this.publicKey = publicKey;
		this.keyResolver = null;
		this.issuer = issuer;
	}

	public OidcAuthenticate(VerificationKeyResolver keyResolver, String issuer) {
		this.publicKey = null;
		this.keyResolver = keyResolver;
		this.issuer = issuer;
	}

	private final int MISSING_ISSUER = 11;
	private final int FEIL_ISSUER = 12;
	private final int FOER_NOT_BEFORE = 6;
	private final int MANGLER_EXPIRY = 2;
	private final int PASSERT_EXPIRY = 1;
	private final int FEIL_SIGNATUR = 9;
	private final int FEIL_TOKEN_FORMAT = 17;

	public String getVerifiedSubject(String bearerToken)  { //TODO får vi autnivå av reststs????
		JwtConsumer jwtConsumer = buildJwtConsumer(); 
		try {
			JwtClaims jwtClaims = jwtConsumer.processToClaims(bearerToken);
			String authLevelClaim = jwtClaims.getStringClaimValue(AUTH_LEVEL_CLAIM);
			if (!AUTH_LEVEL_4.equals(authLevelClaim)) {
				throw new RuntimeException("OIDC-token har ikke tilstrekkelig autentiseringsnivå (Level4): "+authLevelClaim);
			}
	        return jwtClaims.getSubject();
	        
		} catch (InvalidJwtException e) {
			if (e.hasErrorCode(MISSING_ISSUER)) {
				throw new RuntimeException("OIDC-token er ikke gyldig, mangler issuer", e);
			}
			if (e.hasErrorCode(FEIL_ISSUER)) {
				throw new RuntimeException("OIDC-token er ikke gyldig, er ikke utgitt av " + issuer, e);
			}
			if (e.hasErrorCode(FOER_NOT_BEFORE)) {
				throw new RuntimeException("OIDC-token er ennå ikke gyldig, 'not before' er i framtiden", e);
			}
			if (e.hasErrorCode(MANGLER_EXPIRY)) {
				throw new RuntimeException("OIDC-token er ikke gyldig, har ingen gyldighetsgrense", e);
			}
			if (e.hasErrorCode(PASSERT_EXPIRY)) {
				throw new RuntimeException("OIDC-token er ikke gyldig, gyldighetstid utløpt", e);
			}
			if (e.hasErrorCode(FEIL_SIGNATUR)) {
				throw new RuntimeException("OIDC-token er ikke gyldig, feil signatur", e);
			}
			if (e.hasErrorCode(FEIL_TOKEN_FORMAT)) {
				Throwable cause = e.getCause();
				if (cause != null && cause instanceof UnresolvableKeyException) {
					Throwable causeCause = cause.getCause();
					if (causeCause != null && causeCause instanceof IOException) {
						throw new RuntimeException("Kan ikke kontakte OIDC-provider for å validere OIDC-token", e);
					}
					throw new RuntimeException("OIDC-token inneholder ukjent key-Id", e);
				}
				throw new RuntimeException("OIDC-token er ikke gyldig, feil format", e);
			}
			throw new RuntimeException("OIDC-token er ikke gyldig", e);
			
		} catch (MalformedClaimException e) {
			throw new RuntimeException("Kan ikke hente ut bruker fra OIDC-token", e);
		}
	}

	private JwtConsumer buildJwtConsumer() {
		JwtConsumerBuilder builder = new JwtConsumerBuilder()
	            .setRequireExpirationTime() 
	            .setMaxFutureValidityInMinutes(MAX_VALIDITY_SECONDS) 
	            .setAllowedClockSkewInSeconds(AKSEPTERT_TIDSFORSKJELL_SEKUNDER) 
	            .setSkipDefaultAudienceValidation()
	            .setExpectedIssuer(issuer);
		
		if (publicKey != null) {
            builder.setVerificationKey(publicKey);
		} else {
			builder.setVerificationKeyResolver(keyResolver);
		}
		
		return builder.build();
	}
}
