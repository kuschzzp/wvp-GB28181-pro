package com.genersoft.iot.vmp.media.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.HexFormat;
import java.util.Locale;

@Component
@Slf4j
public class MediaHookSecretHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String DERIVATION_DOMAIN = "wvp-media-hook-secret:v1:";

    private final SecureRandom secureRandom = new SecureRandom();
    private final Environment environment;
    private byte[] pepper;
    private boolean ephemeralPepper;

    @Autowired
    public MediaHookSecretHasher(
            MediaAccessProperties properties,
            Environment environment) {
        this.environment = environment;
        this.pepper = properties.hookSecretPepper()
                .getBytes(StandardCharsets.UTF_8);
    }

    MediaHookSecretHasher(String pepper) {
        this.environment = null;
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
        validatePepper(true);
    }

    @PostConstruct
    void initialize() {
        validatePepper(false);
    }

    public String deriveRawSecret(String mediaServerId, long version) {
        if (mediaServerId == null || mediaServerId.isBlank() || version <= 0) {
            throw new IllegalArgumentException("有效的媒体节点 ID 和密钥版本不能为空");
        }
        byte[] derived = hmac((DERIVATION_DOMAIN + mediaServerId.trim() + ":" + version)
                .getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(derived);
    }

    public String hash(String rawSecret) {
        if (rawSecret == null || rawSecret.isBlank()) {
            throw new IllegalArgumentException("Hook secret 不能为空");
        }
        return HexFormat.of().formatHex(
                hmac(rawSecret.getBytes(StandardCharsets.UTF_8)));
    }

    public boolean matches(String rawSecret, String expectedHash) {
        if (rawSecret == null || rawSecret.isBlank()
                || expectedHash == null || expectedHash.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(rawSecret).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII));
    }

    boolean usesEphemeralPepper() {
        return ephemeralPepper;
    }

    private byte[] hmac(byte[] value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(pepper, HMAC_ALGORITHM));
            return mac.doFinal(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("媒体 Hook HMAC 初始化失败", exception);
        }
    }

    private void validatePepper(boolean forceProductionRules) {
        boolean development = !forceProductionRules && environment != null
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
                    "生产环境必须通过 MEDIA_HOOK_SECRET_PEPPER 注入至少 32 字节的 Hook pepper");
        }
        byte[] ephemeralPepper = new byte[32];
        secureRandom.nextBytes(ephemeralPepper);
        pepper = ephemeralPepper;
        this.ephemeralPepper = true;
        log.warn("MEDIA_HOOK_SECRET_PEPPER 未配置；开发环境使用进程级临时 pepper，"
                + "重启后将由 WVP 重新下发媒体 Hook 凭证");
    }
}
