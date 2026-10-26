package com.bebefish.erp.logistics;

import com.bebefish.erp.common.api.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LogisticsSecretCipher {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();
    public LogisticsSecretCipher(@Value("${ERP_LOGISTICS_MASTER_KEY:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) { key=null; return; }
        try {
            byte[] bytes=Base64.getDecoder().decode(base64Key.strip());
            if (bytes.length!=32) throw new IllegalArgumentException();
            key=new SecretKeySpec(bytes,"AES");
            Arrays.fill(bytes,(byte)0);
        } catch (IllegalArgumentException e) { throw new IllegalStateException("物流主密钥必须为32字节Base64值"); }
    }
    public boolean available() { return key!=null; }
    public String encrypt(String value,String context) {
        requireKey();
        try {
            byte[] nonce=new byte[12]; random.nextBytes(nonce);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload=new byte[nonce.length+encrypted.length];
            System.arraycopy(nonce,0,payload,0,nonce.length); System.arraycopy(encrypted,0,payload,nonce.length,encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch(Exception e) { throw new IllegalStateException("物流密钥加密失败"); }
    }
    public String decrypt(String value,String context) {
        requireKey();
        try {
            byte[] payload=Base64.getDecoder().decode(value);
            if(payload.length<28) throw new IllegalArgumentException();
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOf(payload,12)));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(payload,12,payload.length-12),StandardCharsets.UTF_8);
        } catch(Exception e) { throw new BusinessException("LOGISTICS_SECRET_UNAVAILABLE",HttpStatus.CONFLICT,"物流密钥无法验证，请检查主密钥配置"); }
    }
    private void requireKey() {
        if(key==null) throw new BusinessException("LOGISTICS_MASTER_KEY_MISSING",HttpStatus.CONFLICT,"未配置物流加密主密钥，无法保存或读取接口密钥");
    }
}
