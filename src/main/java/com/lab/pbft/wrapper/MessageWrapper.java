package com.lab.pbft.wrapper;

import com.lab.pbft.networkObjects.acknowledgements.NewView;
import com.lab.pbft.networkObjects.communique.*;
import lombok.*;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import java.util.Objects;

@Getter
@Builder
@Setter
public class MessageWrapper implements Serializable {

    public enum MessageType {
        MESSAGE,
        SERVER_STATUS_UPDATE,
        REQUEST,
        PRE_PREPARE,
        COMMIT,
        EXECUTE,
        VIEW_CHANGE,
        NEW_VIEW
    }

    public static MessageWrapper from(MessageWrapper messageWrapper){
        return MessageWrapper.builder()
                .type(messageWrapper.getType())
                .fromPort(messageWrapper.getFromPort())
                .toPort(messageWrapper.getToPort())
                .message(messageWrapper.getMessage())
                .serverStatusUpdate(messageWrapper.getServerStatusUpdate())
                .request(messageWrapper.getRequest())
                .prePrepare(messageWrapper.getPrePrepare())
                .commit(messageWrapper.getCommit())
                .execute(messageWrapper.getExecute())
                .viewChange(messageWrapper.getViewChange())
                .newView(messageWrapper.getNewView())
                .build();
    }
    public String getHash() throws NoSuchAlgorithmException {
        StringBuilder hash = new StringBuilder();
        hash.append(type)
                .append(fromPort)
                .append(toPort)
                .append(message)
                .append(serverStatusUpdate)
                .append(request)
                .append(prePrepare)
                .append(commit)
                .append(execute)
                .append(viewChange)
                .append(newView);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        byte[] hashBytes = digest.digest(hash.toString().getBytes(StandardCharsets.UTF_8));

        return Base64.getEncoder().encodeToString(hashBytes);
    }

    private static final long serialVersionUID = 1L;

    private MessageType type;

    private long fromPort;
    private long toPort;

    private Message message;
    private ServerStatusUpdate serverStatusUpdate;
    private Request request;
    private PrePrepare prePrepare;
    private Commit commit;
    private Execute execute;
    private ViewChange viewChange;
    private NewView newView;

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