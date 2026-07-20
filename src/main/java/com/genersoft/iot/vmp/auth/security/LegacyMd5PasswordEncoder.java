package com.genersoft.iot.vmp.auth.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * 仅用于校验 2.7.x 遗留 MD5 密码。新密码不得使用此编码器写入。
 */
public final class LegacyMd5PasswordEncoder implements PasswordEncoder {

    @Override
    public String encode(CharSequence rawPassword) {
        return DigestUtils.md5DigestAsHex(rawPassword.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        byte[] actual = encode(rawPassword).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = encodedPassword.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(actual, expected);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return true;
    }
}
