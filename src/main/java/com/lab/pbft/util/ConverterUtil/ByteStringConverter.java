package com.lab.pbft.util.ConverterUtil;

import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class ByteStringConverter {

    public static String byteArrayToBase64String(byte[] signatureBytes) {
        return Base64.getEncoder().encodeToString(signatureBytes);
    }

    public static byte[] base64StringToByteArray(String base64String) {
        return Base64.getDecoder().decode(base64String);
    }

}
