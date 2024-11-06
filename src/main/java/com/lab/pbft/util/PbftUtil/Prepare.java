package com.lab.pbft.util.PbftUtil;

import com.lab.pbft.config.KeyConfig;
import com.lab.pbft.model.primary.Log;
import com.lab.pbft.repository.primary.LogRepository;
import com.lab.pbft.service.SocketService;
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
public class Prepare {

    @Autowired
    @Lazy
    private KeyConfig keyConfig;
    @Autowired
    @Lazy
    private SocketService socketService;
    @Autowired
    @Lazy
    private LogRepository logRepository;

    public void prepare(ObjectInputStream in, ObjectOutputStream out, MessageWrapper messageWrapper) throws Exception {

        // Steps:

        // 5. It has not accepted a request previously with (v,n)

        boolean exists = logRepository.existsById(messageWrapper.getPrePrepare().getSequenceNumber());

        if(exists){
            log.error("PrePrepare message for an already existing sequence number {}, rejecting", messageWrapper.getPrePrepare().getSequenceNumber());

            return;
        }

        // 1. Signature verified for pre-prepare

        // 2. Signature verification for request
        try {
            if(!messageWrapper.getPrePrepare().getRequest().verifyMessage(keyConfig.getPublicKeyStore().get(messageWrapper.getPrePrepare().getRequest().getClientId()))){
                log.error("Invalid client signature: {}", messageWrapper);
                return;
            }
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
        }

        // 3. d is the digest for m
        if(!messageWrapper.getPrePrepare().getRequestDigest().equals(messageWrapper.getPrePrepare().getRequest().getHash())){
            log.error("Digest mismatch for requests: {}", messageWrapper);
            return;
        }

        // 4. Verify if the view is correct
        if(messageWrapper.getPrePrepare().getCurrentView()>socketService.getCurrentView()){
            log.warn("Detected node lag, beginning SYNC: {}", messageWrapper);

            // SYNC THIS
            // NODE IT IS
            // LAGGING BEHIND

            return;
        }
        else if(messageWrapper.getPrePrepare().getCurrentView()<socketService.getCurrentView()){
            log.error("PrePrepare from a previous view, rejecting: {}", messageWrapper);

            return;
        }

        // 6. n is between a low watermark and high watermark

        if(messageWrapper.getPrePrepare().getSequenceNumber() <= socketService.getLowWaterMark() && messageWrapper.getPrePrepare().getSequenceNumber() > (socketService.getLowWaterMark() + socketService.getWaterMarkThreshold())){
            log.error("PrePrepare does not satisfy watermark: {}", messageWrapper);

            return;
        }

        log.info("Preparing received pre-prepare message");

        // Time to prepare
        com.lab.pbft.networkObjects.acknowledgements.Prepare prepare = com.lab.pbft.networkObjects.acknowledgements.Prepare.builder()
                .currentView(messageWrapper.getPrePrepare().getCurrentView())
                .sequenceNumber(messageWrapper.getPrePrepare().getSequenceNumber())
                .requestDigest(messageWrapper.getPrePrepare().getRequestDigest())
                .build();

        Log dbLog = Log.builder()
                .sequenceNumber(messageWrapper.getPrePrepare().getSequenceNumber())
                .viewNumber(messageWrapper.getPrePrepare().getCurrentView())
                .type(Log.Type.PRE_PREPARE)
                .prePrepare(messageWrapper.getPrePrepare())
                .build();

        logRepository.save(dbLog);

        prepare.setPrepareDigest(prepare.getHash());
        prepare.signMessage(keyConfig.getPrivateKey());

        AckMessageWrapper ackMessageWrapper = AckMessageWrapper.builder()
                .type(AckMessageWrapper.MessageType.PREPARE)
                .fromPort(socketService.getAssignedPort())
                .toPort(messageWrapper.getFromPort())
                .prepare(prepare)
                .build();

        ackMessageWrapper.signMessage(keyConfig.getPrivateKey());

        out.writeObject(ackMessageWrapper);

    }
}
