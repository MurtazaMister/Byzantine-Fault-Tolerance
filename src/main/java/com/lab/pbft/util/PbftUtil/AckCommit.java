package com.lab.pbft.util.PbftUtil;

import com.lab.pbft.config.KeyConfig;
import com.lab.pbft.config.client.ApiConfig;
import com.lab.pbft.model.primary.Log;
import com.lab.pbft.networkObjects.acknowledgements.Prepare;
import com.lab.pbft.networkObjects.communique.Commit;
import com.lab.pbft.repository.primary.LogRepository;
import com.lab.pbft.service.SocketService;
import com.lab.pbft.util.ConverterUtil.ByteStringConverter;
import com.lab.pbft.util.SignatureVerificationUtil;
import com.lab.pbft.wrapper.AckMessageWrapper;
import com.lab.pbft.wrapper.MessageWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

@Component
@Slf4j
public class AckCommit {

    @Autowired
    @Lazy
    private SocketService socketService;
    @Autowired
    @Lazy
    private SignatureVerificationUtil signatureVerificationUtil;
    @Autowired
    @Lazy
    private ApiConfig apiConfig;
    @Autowired
    @Lazy
    private KeyConfig keyConfig;
    @Autowired
    @Lazy
    private LogRepository logRepository;

    public void ackCommit(ObjectInputStream in, ObjectOutputStream out, MessageWrapper messageWrapper) throws Exception {

        // Verify the received commit message
        // It has the signatures of 2f+1 prepare messages
        // If its correct, then commit it
        // and send committed message back

        // Steps

        // 1. Verify commit is from the same view

        Commit commit = messageWrapper.getCommit();
        if(socketService.getCurrentView() < commit.getCurrentView()){
            log.info("Commit from a previous view {}, current {}, rejecting", commit.getCurrentView(), socketService.getCurrentView());
            return;
        }
        else if(socketService.getCurrentView() > commit.getCurrentView()){
            log.info("Server lagging behind, initiating SYNC");

            // INITIATE SYNC
            // HERE SO THAT
            // SERVER RECOVERS

            return;
        }

        // 2. Verify all signatures of prepare messages

        Prepare temporary = Prepare.builder()
                .currentView(commit.getCurrentView())
                .sequenceNumber(commit.getSequenceNumber())
                .requestDigest(commit.getRequestDigest())
                .build();

        String hash = temporary.getHash();

        int verificationCount = 0;

        for (long id : commit.getSignatures().keySet()) {
            if(signatureVerificationUtil.verifySignature(id, hash, ByteStringConverter.base64StringToByteArray(commit.getSignatures().get(id)))) {
                verificationCount++;
            }
        }

        int threshold = apiConfig.getServerPopulation() - (apiConfig.getServerPopulation()-1)/3;

        if(verificationCount < threshold){
            log.error("Invalid signatures provided for prepare messages, rejecting");
            return;
        }

        // 3. Sequence number is between h and H

        if(commit.getSequenceNumber() <= socketService.getLowWaterMark() && commit.getSequenceNumber() > (socketService.getLowWaterMark() + socketService.getWaterMarkThreshold())){
            log.error("PrePrepare does not satisfy watermark: {}", messageWrapper);

            return;
        }

        log.info("Verified threshold signatures of prepare messages, moving to commit");

        // Everything verified
        // Lets move forward to send ackCommit
        // so that collector can collect signatures
        // and execute and send replies

        com.lab.pbft.networkObjects.acknowledgements.AckCommit ackCommit = com.lab.pbft.networkObjects.acknowledgements.AckCommit.builder()
                .currentView(commit.getCurrentView())
                .sequenceNumber(commit.getSequenceNumber())
                .requestDigest(commit.getRequestDigest())
                .build();

        ackCommit.setAckCommitDigest(ackCommit.getHash());
        ackCommit.signMessage(keyConfig.getPrivateKey());

        Log dbLog = logRepository.findBySequenceNumber(commit.getSequenceNumber()).orElse(null);

        if(dbLog != null){
            dbLog.setSignatures(commit.getSignatures());
            dbLog.setType(Log.Type.PREPARED);
            logRepository.save(dbLog);
        }
        else{
            dbLog = Log.builder()
                    .sequenceNumber(commit.getSequenceNumber())
                    .viewNumber(commit.getCurrentView())
                    .type(Log.Type.PREPARED)
                    .prePrepare(commit.getPrePrepare())
                    .signatures(commit.getSignatures())
                    .build();

            dbLog = logRepository.save(dbLog);
        }

        AckMessageWrapper ackMessageWrapper = AckMessageWrapper.builder()
                .type(AckMessageWrapper.MessageType.ACK_COMMIT)
                .fromPort(socketService.getAssignedPort())
                .toPort(messageWrapper.getFromPort())
                .ackCommit(ackCommit)
                .build();

        ackMessageWrapper.signMessage(keyConfig.getPrivateKey());

        out.writeObject(ackMessageWrapper);

    }

}
