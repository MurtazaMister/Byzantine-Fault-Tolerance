package com.lab.pbft.util;

import com.lab.pbft.config.KeyConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;

@Component
public class SignatureVerificationUtil {

    @Autowired
    @Lazy
    public KeyConfig keyConfig;

    public boolean verifySignature(long id, String hash, byte[] signature) throws SignatureException, UnsupportedEncodingException, InvalidKeyException, NoSuchAlgorithmException {
        Signature sign = Signature.getInstance("SHA256withRSA");
        sign.initVerify(keyConfig.getPublicKeyStore().get(id));
        sign.update(hash.getBytes("UTF-8"));

        return sign.verify(signature);
    }

}
