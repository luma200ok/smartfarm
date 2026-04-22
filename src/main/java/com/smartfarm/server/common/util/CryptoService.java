package com.smartfarm.server.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM 대칭 키 암호화/복호화 서비스.
 *
 * <p>암호화된 값은 {@code ENC:} 접두사 + Base64(IV + ciphertext + authTag) 형식입니다.
 * 접두사가 없는 값은 평문으로 간주하여 그대로 반환합니다 (마이그레이션 기간 호환).</p>
 *
 * <p>암호화 키는 {@code smartfarm.encryption.secret-key} (환경변수 ENCRYPTION_SECRET_KEY)에서
 * 주입 받으며, SHA-256으로 32바이트 AES 키를 파생합니다.</p>
 */
@Slf4j
@Service
public class CryptoService {

    private static final String ALGORITHM   = "AES/GCM/NoPadding";
    private static final int IV_LENGTH      = 12;   // bytes (GCM 권장 96-bit IV)
    private static final int TAG_LENGTH     = 128;  // bits (GCM 인증 태그)
    public static final  String ENC_PREFIX  = "ENC:";

    private final SecretKeySpec secretKey;

    public CryptoService(@Value("${smartfarm.encryption.secret-key}") String rawKey) {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(rawKey.getBytes(StandardCharsets.UTF_8));
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
            log.info(">>> CryptoService 초기화 완료 (AES-256-GCM)");
        } catch (Exception e) {
            throw new IllegalStateException("암호화 서비스 초기화 실패", e);
        }
    }

    /**
     * 평문을 AES-256-GCM으로 암호화합니다.
     * 이미 암호화된 값({@code ENC:} 접두사)이거나 null이면 그대로 반환합니다.
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        if (plaintext.startsWith(ENC_PREFIX)) return plaintext;

        try {
            byte[] iv = new byte[IV_LENGTH];
            SecureRandom.getInstanceStrong().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, IV_LENGTH, encrypted.length);

            return ENC_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("암호화 실패", e);
        }
    }

    /**
     * 암호화된 값을 복호화합니다.
     * {@code ENC:} 접두사가 없으면 평문으로 간주하여 그대로 반환합니다 (마이그레이션 기간 허용).
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null) return null;
        if (!ciphertext.startsWith(ENC_PREFIX)) {
            log.debug("복호화 대상이 평문으로 식별됨 (마이그레이션 기간 허용)");
            return ciphertext;
        }

        try {
            byte[] combined     = Base64.getDecoder().decode(ciphertext.substring(ENC_PREFIX.length()));
            byte[] iv           = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] encryptedData = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(encryptedData), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("복호화 실패", e);
        }
    }
}
