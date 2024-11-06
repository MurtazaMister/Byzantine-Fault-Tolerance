package com.lab.pbft.networkObjects.acknowledgements;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Prepare implements Serializable {
    private static final long serialVersionUID = 1L;

    private long currentView;

    private long sequenceNumber;

    private String requestDigest;

    private String prepareDigest;

    private byte[] signature;

    @JsonIgnore
    public String getHash() throws NoSuchAlgorithmException {
        StringBuilder hash = new StringBuilder();

        hash.append(currentView)
                .append(sequenceNumber)
                .append(requestDigest);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        byte[] hashBytes = digest.digest(hash.toString().getBytes(StandardCharsets.UTF_8));

        return Base64.getEncoder().encodeToString(hashBytes);
    }

    @JsonIgnore
    public void signMessage(PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(getHash().getBytes("UTF-8"));

        this.signature = signature.sign();
    }

    @JsonIgnore
    public boolean verifyMessage(PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(getHash().getBytes("UTF-8"));

        return signature.verify(this.signature);
    }

}
