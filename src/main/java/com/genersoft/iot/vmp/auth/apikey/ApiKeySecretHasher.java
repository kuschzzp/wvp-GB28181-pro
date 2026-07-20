package com.genersoft.iot.vmp.auth.apikey;

import com.genersoft.iot.vmp.auth.config.AuthorizationProperties;
import com.genersoft.iot.vmp.auth.model.AuthorizationMode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

@Component
@Slf4j
public class ApiKeySecretHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String RAW_PREFIX = "wvp_";

    private final SecureRandom secureRandom = new SecureRandom();
    private final Environment environment;
    private final boolean enforceMode;
    private byte[] pepper;

    public ApiKeySecretHasher(AuthorizationProperties properties, Environment environment) {
        this.environment = environment;
        this.enforceMode = properties.mode() == AuthorizationMode.ENFORCE;
        this.pepper = properties.apiKeyPepper().getBytes(StandardCharsets.UTF_8);
    }

    ApiKeySecretHasher(String pepper) {
        this.environment = null;
        this.enforceMode = true;
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
        validatePepper(true);
    }

    @PostConstruct
    void initialize() {
        validatePepper(false);
    }

    public GeneratedApiKey generate() {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String raw = RAW_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        return new GeneratedApiKey(
                raw,
                raw.substring(0, 12),
                raw.substring(raw.length() - 4),
                hash(raw));
    }

    public String hash(String rawSecret) {
        if (rawSecret == null || rawSecret.isBlank()) {
            throw new IllegalArgumentException("API Key 不能为空");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(pepper, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(rawSecret.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("API Key HMAC 初始化失败", exception);
        }
    }

    public boolean matches(String rawSecret, String expectedHash) {
        if (expectedHash == null || expectedHash.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(rawSecret).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII));
    }

    private void validatePepper(boolean forceProductionRules) {
        boolean development = !forceProductionRules && !enforceMode && environment != null
                && Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("dev")
                        || profile.endsWith("-dev")
                        || profile.equals("test")
                        || profile.endsWith("-test")
                        || profile.equals("local"));
        if (pepper.length >= 32) {
            return;
        }
        if (!development) {
            throw new IllegalStateException(
                    "生产环境必须通过 API_KEY_PEPPER 注入至少 32 字节的 API Key pepper");
        }
        byte[] ephemeralPepper = new byte[32];
        secureRandom.nextBytes(ephemeralPepper);
        pepper = ephemeralPepper;
        log.warn("API_KEY_PEPPER 未配置；开发环境使用进程级临时 pepper，重启后已有 API Key 将失效");
    }
}
