package com.lab.pbft.networkObjects.acknowledgements;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lab.pbft.util.ConverterUtil.ByteStringConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ClientReply implements Serializable {
    private static final long serialVersionUID = 1L;

    private long currentView;

    private String timestamp;

    private String requestDigest;

    private boolean approved;

    private long finalBalance;

    private String replyDigest;

    private Map<Long, String> signatures;

    @JsonIgnore
    public String getHash() throws NoSuchAlgorithmException {
        StringBuilder hash = new StringBuilder();
        hash.append(currentView)
                .append(timestamp)
                .append(requestDigest)
                .append(finalBalance)
                .append(approved);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        byte[] hashBytes = digest.digest(hash.toString().getBytes(StandardCharsets.UTF_8));

        return Base64.getEncoder().encodeToString(hashBytes);
    }

    @JsonIgnore
    public boolean verifyMessage(String sign, PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(getHash().getBytes("UTF-8"));

        return signature.verify(ByteStringConverter.base64StringToByteArray(sign));
    }

}