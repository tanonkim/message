package com.message.global.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.AlgorithmParameters;
import java.security.Key;
import java.util.Base64;

@Slf4j
@Converter
@Component
@RequiredArgsConstructor
public class StringCryptoConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private final CryptoProperties cryptoProperties;


    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, getKey(), getIv());
            return Base64.getEncoder().encodeToString(cipher.doFinal(attribute.getBytes()));
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new IllegalStateException("암호화 실패", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if  (dbData == null) return null;

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, getKey(), getIv());
            return new String(cipher.doFinal(Base64.getDecoder().decode(dbData)));
        }
        catch (Exception e) {
            log.error("Decryption failed", e);
            throw new IllegalStateException("복호화 실패", e);
        }
    }

    private Key getKey() {
        return new SecretKeySpec(cryptoProperties.key().getBytes(), "AES");
    }

    private IvParameterSpec getIv() {
        return new IvParameterSpec(cryptoProperties.iv().getBytes());
    }
}
