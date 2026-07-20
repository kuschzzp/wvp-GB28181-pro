package com.genersoft.iot.vmp.conf.security;

import com.genersoft.iot.vmp.auth.model.VerifiedAccessToken;
import com.genersoft.iot.vmp.auth.security.InvalidAccessTokenException;
import com.genersoft.iot.vmp.conf.UserSetting;
import lombok.extern.slf4j.Slf4j;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.lang.JoseException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtUtils implements InitializingBean {

    public static final String HEADER = "access-token";
    public static final String API_KEY_HEADER = "api-key";

    private static final String ACCESS_TOKEN_AUDIENCE = "wvp-web";
    private static final String TOKEN_VERSION_CLAIM = "tokenVersion";
    private static final String DEFAULT_KEY_ID = "3e79646c4dbc408383a9eed09f2b85ae";

    private final UserSetting userSetting;
    private RsaJsonWebKey signingKey;
    private JwtConsumer accessTokenConsumer;

    public JwtUtils(UserSetting userSetting) {
        this.userSetting = userSetting;
    }

    @Override
    public void afterPropertiesSet() {
        try {
            signingKey = loadOrCreateSigningKey();
            accessTokenConsumer = new JwtConsumerBuilder()
                    .setAllowedClockSkewInSeconds(30)
                    .setRequireExpirationTime()
                    .setRequireIssuedAt()
                    .setRequireSubject()
                    .setRequireJwtId()
                    .setExpectedAudience(ACCESS_TOKEN_AUDIENCE)
                    .setJwsAlgorithmConstraints(
                            AlgorithmConstraints.ConstraintType.PERMIT,
                            AlgorithmIdentifiers.RSA_USING_SHA256)
                    .setVerificationKey(signingKey.getPublicKey())
                    .build();
        } catch (JoseException exception) {
            throw new IllegalStateException("初始化 JWT 签名密钥失败", exception);
        }
    }

    public String createAccessToken(long userId, long tokenVersion, Duration ttl) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(ttl);
        JwtClaims claims = new JwtClaims();
        claims.setSubject(Long.toString(userId));
        claims.setGeneratedJwtId();
        claims.setIssuedAt(NumericDate.fromMilliseconds(issuedAt.toEpochMilli()));
        claims.setExpirationTime(NumericDate.fromMilliseconds(expiresAt.toEpochMilli()));
        claims.setAudience(ACCESS_TOKEN_AUDIENCE);
        claims.setClaim(TOKEN_VERSION_CLAIM, tokenVersion);
        return sign(claims);
    }

    public VerifiedAccessToken verifyAccessToken(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidAccessTokenException("访问令牌不能为空");
        }
        try {
            JwtClaims claims = accessTokenConsumer.processToClaims(token);
            long userId = Long.parseLong(claims.getSubject());
            String jti = claims.getJwtId();
            Long tokenVersion = claims.getClaimValue(TOKEN_VERSION_CLAIM, Long.class);
            if (userId <= 0 || jti == null || jti.isBlank() || tokenVersion == null || tokenVersion < 0) {
                throw new InvalidAccessTokenException("访问令牌声明不完整");
            }
            return new VerifiedAccessToken(
                    userId,
                    jti,
                    tokenVersion,
                    Instant.ofEpochSecond(claims.getIssuedAt().getValue()),
                    Instant.ofEpochSecond(claims.getExpirationTime().getValue()));
        } catch (InvalidAccessTokenException exception) {
            throw exception;
        } catch (InvalidJwtException | NumberFormatException exception) {
            throw new InvalidAccessTokenException("访问令牌无效或已过期", exception);
        } catch (Exception exception) {
            throw new InvalidAccessTokenException("访问令牌解析失败", exception);
        }
    }

    public static String getHeader() {
        return HEADER;
    }

    public static String getApiKeyHeader() {
        return API_KEY_HEADER;
    }

    private String sign(JwtClaims claims) {
        try {
            JsonWebSignature signature = new JsonWebSignature();
            signature.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
            signature.setKeyIdHeaderValue(signingKey.getKeyId());
            signature.setPayload(claims.toJson());
            signature.setKey(signingKey.getPrivateKey());
            return signature.getCompactSerialization();
        } catch (JoseException exception) {
            throw new IllegalStateException("访问令牌签发失败", exception);
        }
    }

    private RsaJsonWebKey loadOrCreateSigningKey() throws JoseException {
        String configuredPath = userSetting.getJwkFile();
        String jwkFile = configuredPath == null || configuredPath.isBlank()
                ? "config" + File.separator + "jwk.json"
                : configuredPath.trim();
        try (InputStream inputStream = openJwk(jwkFile);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String json = reader.lines().collect(Collectors.joining());
            List<JsonWebKey> keys = new JsonWebKeySet(json).getJsonWebKeys();
            for (JsonWebKey key : keys) {
                if (key instanceof RsaJsonWebKey rsaKey && rsaKey.getPrivateKey() != null) {
                    if (rsaKey.getKeyId() == null || rsaKey.getKeyId().isBlank()) {
                        rsaKey.setKeyId(DEFAULT_KEY_ID);
                    }
                    log.info("[API AUTH] 已加载 JWT RSA 密钥，keyId={}", rsaKey.getKeyId());
                    return rsaKey;
                }
            }
            log.error("[API AUTH] JWK 文件没有可用于签名的 RSA 私钥：{}", jwkFile);
        } catch (IOException | RuntimeException exception) {
            log.warn("[API AUTH] 无法读取 JWK 文件，将生成并持久化新密钥：{}", jwkFile);
        }
        return createAndPersistSigningKey(jwkFile);
    }

    private InputStream openJwk(String jwkFile) throws IOException {
        if (jwkFile.startsWith("classpath:")) {
            ClassPathResource resource = new ClassPathResource(jwkFile.substring("classpath:".length()));
            if (resource.exists()) {
                log.warn("[API AUTH] 正在使用 classpath 内的 JWK；生产环境应配置外部私钥文件");
                return resource.getInputStream();
            }
        }
        Path path = persistPath(jwkFile);
        if (Files.isRegularFile(path) && Files.isReadable(path)) {
            return Files.newInputStream(path);
        }
        throw new IOException("JWK 文件不存在或不可读：" + path.toAbsolutePath());
    }

    private RsaJsonWebKey createAndPersistSigningKey(String jwkFile) throws JoseException {
        RsaJsonWebKey generated = RsaJwkGenerator.generateJwk(4096);
        generated.setKeyId(DEFAULT_KEY_ID);
        Path path = persistPath(jwkFile);
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String json = new JsonWebKeySet(generated)
                    .toJson(JsonWebKey.OutputControlLevel.INCLUDE_PRIVATE);
            Files.writeString(path, json, StandardCharsets.UTF_8);
            log.info("[API AUTH] 已生成并持久化 JWT RSA 密钥：{}", path.toAbsolutePath());
        } catch (IOException exception) {
            throw new IllegalStateException("JWT 密钥无法持久化到 " + path.toAbsolutePath(), exception);
        }
        return generated;
    }

    private Path persistPath(String jwkFile) {
        if (jwkFile.startsWith("classpath:")) {
            return Paths.get("config", "jwk.json");
        }
        return Paths.get(jwkFile);
    }
}
