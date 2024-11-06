package com.lab.pbft.wrapper;

import com.lab.pbft.networkObjects.acknowledgements.*;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

@Getter
@Builder
@Setter
@Data
public class AckMessageWrapper implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum MessageType {
        ACK_MESSAGE,
        ACK_SERVER_STATUS_UPDATE,
        REPLY,
        PREPARE,
        ACK_COMMIT,
        CLIENT_REPLY
    }
    public String getHash() throws NoSuchAlgorithmException {
        StringBuilder hash = new StringBuilder();
        hash.append(type)
                .append(fromPort)
                .append(toPort)
                .append(ackMessage)
                .append(ackServerStatusUpdate)
                .append(reply)
                .append(prepare)
                .append(ackCommit)
                .append(clientReply);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        byte[] hashBytes = digest.digest(hash.toString().getBytes(StandardCharsets.UTF_8));

        return Base64.getEncoder().encodeToString(hashBytes);
    }

    private MessageType type;

    private long fromPort;
    private long toPort;

    private AckMessage ackMessage;
    private AckServerStatusUpdate ackServerStatusUpdate;
    private Reply reply;
    private Prepare prepare;
    private AckCommit ackCommit;
    private ClientReply clientReply;

    private byte[] signature;

    public void signMessage(PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(getHash().getBytes("UTF-8"));

        this.signature = signature.sign();
    }

    public boolean verifyMessage(PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(getHash().getBytes("UTF-8"));

        return signature.verify(this.signature);
    }
}
