package com.lab.pbft.model.primary;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lab.pbft.networkObjects.communique.PrePrepare;
import com.lab.pbft.networkObjects.communique.ViewChange;
import com.lab.pbft.util.ConverterUtil.BundleConverter;
import com.lab.pbft.util.ConverterUtil.ByteStringConverter;
import com.lab.pbft.util.ConverterUtil.MapConverter;
import com.lab.pbft.util.ConverterUtil.PrePrepareConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "new_view") // Optional: Specify the table name if needed
@Slf4j
public class NewView implements Serializable {
    private static final long serialVersionUID = 1L;

    public static NewView toNewView(com.lab.pbft.networkObjects.acknowledgements.NewView newView){
        List<Bundle> bundles = new ArrayList<>();
        for(com.lab.pbft.networkObjects.acknowledgements.NewView.Bundle bundle : newView.getBundles()){
            if(bundle==null) continue;

            bundles.add(
                    Bundle.builder()
                            .sequenceNumber(bundle.getSequenceNumber())
                            .prePrepare(bundle.getPrePrepare())
                            .signatures(bundle.getSignatures())
                            .approved(bundle.isApproved())
                            .build()
            );
        }

        return NewView.builder()
                .view(newView.getView())
                .bundles(bundles)
                .signatures(newView.getSignatures())
                .build();
    }

    @Id
    private Integer view;

    @Convert(converter = BundleConverter.class)
    @ElementCollection
    @CollectionTable(name = "new_view_bundle", joinColumns = @JoinColumn(name = "new_view_view"))
    private List<Bundle> bundles = new ArrayList<>();

    @Convert(converter = MapConverter.class) // Convert the Map<Long, String> using MapConverter
    @Column(columnDefinition = "MEDIUMTEXT")
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
    @Embeddable
    public static class Bundle implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long sequenceNumber;

        @Convert(converter = PrePrepareConverter.class)
        @Column(columnDefinition = "MEDIUMTEXT")
        private PrePrepare prePrepare;

        @Convert(converter = MapConverter.class) // Convert the Map<Long, String> using MapConverter
        @Column(columnDefinition = "MEDIUMTEXT")
        private Map<Long, String> signatures;

        private Boolean approved;

        @JsonIgnore
        public static com.lab.pbft.networkObjects.acknowledgements.NewView.Bundle toBundle(ViewChange.Bundle b){
            return com.lab.pbft.networkObjects.acknowledgements.NewView.Bundle.builder()
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
