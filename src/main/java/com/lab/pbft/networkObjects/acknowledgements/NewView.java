package com.lab.pbft.networkObjects.acknowledgements;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lab.pbft.networkObjects.communique.PrePrepare;
import com.lab.pbft.networkObjects.communique.ViewChange;
import com.lab.pbft.util.ConverterUtil.ByteStringConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NewView implements Serializable {
    private static final long serialVersionUID = 1L;

    private int view;

    List<Bundle> bundles;

    private Map<Long, String> signatures;

    @JsonIgnore
    public String getHash() throws NoSuchAlgorithmException {
        StringBuilder hash = new StringBuilder();
        hash.append(view);

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

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Bundle implements Serializable {
        private static final long serialVersionUID = 1L;

        private long sequenceNumber;

        private PrePrepare prePrepare;

        private Map<Long, String> signatures;

        private boolean approved;

        public static Bundle toBundle(ViewChange.Bundle b){
            return Bundle.builder()
                    .sequenceNumber(b.getSequenceNumber())
                    .prePrepare(b.getPrePrepare())
                    .signatures(b.getSignatures())
                    .approved(b.isApproved())
                    .build();
        }

        @JsonIgnore
        public String getHash() throws NoSuchAlgorithmException {
            StringBuilder hash = new StringBuilder();
            hash.append(sequenceNumber)
                    .append(prePrepare.getRequestDigest());

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

}
