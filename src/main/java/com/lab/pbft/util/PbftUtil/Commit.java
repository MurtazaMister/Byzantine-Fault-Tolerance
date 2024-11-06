package com.lab.pbft.util.PbftUtil;

import com.lab.pbft.config.KeyConfig;
import com.lab.pbft.config.client.ApiConfig;
import com.lab.pbft.model.primary.Log;
import com.lab.pbft.networkObjects.acknowledgements.AckCommit;
import com.lab.pbft.networkObjects.acknowledgements.ClientReply;
import com.lab.pbft.networkObjects.acknowledgements.Reply;
import com.lab.pbft.repository.primary.LogRepository;
import com.lab.pbft.service.PbftService;
import com.lab.pbft.service.SocketService;
import com.lab.pbft.util.ConverterUtil.ByteStringConverter;
import com.lab.pbft.util.SocketMessageUtil;
import com.lab.pbft.util.Stopwatch;
import com.lab.pbft.wrapper.AckMessageWrapper;
import com.lab.pbft.wrapper.MessageWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Component
@Slf4j
public class Commit {
    @Autowired
    @Lazy
    private LogRepository logRepository;
    @Autowired
    @Lazy
    private SocketService socketService;
    @Autowired
    @Lazy
    private SocketMessageUtil socketMessageUtil;
    @Autowired
    @Lazy
    private KeyConfig keyConfig;
    @Autowired
    @Lazy
    private ApiConfig apiConfig;
    @Autowired
    @Lazy
    private PbftService pbftService;

    public ClientReply commit(Log dbLog, com.lab.pbft.networkObjects.communique.PrePrepare prePrepare, Map<Long, String> signatures) {

        LocalDateTime startTime = LocalDateTime.now();
        try{
            log.info("Pbft : commit initiated on port {}", socketService.getAssignedPort());

            // 2f+1 prepares collected in pre-prepare phase

            dbLog.setSignatures(signatures);
            dbLog.setType(Log.Type.PREPARED);

            dbLog = logRepository.save(dbLog);

            com.lab.pbft.networkObjects.communique.Commit commit = com.lab.pbft.networkObjects.communique.Commit.builder()
                    .currentView(prePrepare.getCurrentView())
                    .sequenceNumber(prePrepare.getSequenceNumber())
                    .requestDigest(prePrepare.getRequestDigest())
                    .prePrepare(prePrepare)
                    .signatures(signatures)
                    .build();

            MessageWrapper messageWrapper = MessageWrapper.builder()
                    .type(MessageWrapper.MessageType.COMMIT)
                    .commit(commit)
                    .fromPort(socketService.getAssignedPort())
                    .build();

            try{
                List<AckMessageWrapper> ackMessageWrapperList = socketMessageUtil.broadcast(messageWrapper).get();

                log.info("Received committed from {} servers", ackMessageWrapperList.size());
                LocalDateTime currentTime = LocalDateTime.now();
                log.info("{}", Stopwatch.getDuration(startTime, currentTime, "Commit"));

                Map<String, Integer> receipts = new HashMap<>();

                AckCommit selfAckCommit = AckCommit.builder()
                        .currentView(prePrepare.getCurrentView())
                        .sequenceNumber(prePrepare.getSequenceNumber())
                        .requestDigest(prePrepare.getRequestDigest())
                        .build();

                selfAckCommit.setAckCommitDigest(selfAckCommit.getHash());
                selfAckCommit.signMessage(keyConfig.getPrivateKey());

                AckMessageWrapper selfAck = AckMessageWrapper.builder()
                        .type(AckMessageWrapper.MessageType.ACK_COMMIT)
                        .fromPort(socketService.getAssignedPort())
                        .toPort(socketService.getAssignedPort())
                        .ackCommit(selfAckCommit)
                        .build();

                selfAck.signMessage(keyConfig.getPrivateKey());

                ackMessageWrapperList.add(selfAck);

                for(AckMessageWrapper ackMessageWrapper : ackMessageWrapperList){

                    // Verifying the prepare messages from all servers

                    if(!ackMessageWrapper.verifyMessage(keyConfig.getPublicKeyStore().get(ackMessageWrapper.getFromPort()))) {
                        log.error("Invalid signature for prepare message: {}", ackMessageWrapper);
                        continue;
                    }

                    AckCommit ackCommit = ackMessageWrapper.getAckCommit();

                    receipts.put(ackCommit.getAckCommitDigest(), receipts.getOrDefault(ackCommit.getAckCommitDigest(), 0) + 1);

                }

                int threshold = apiConfig.getServerPopulation() - (apiConfig.getServerPopulation()-1)/3;
                String ackCommit = null;
                Map<Long, String> signaturesList = new HashMap<>();

                for(String x : receipts.keySet()) {
                    if(receipts.get(x) >= threshold && x.equals(selfAckCommit.getAckCommitDigest())) {
                        ackCommit = x;

                        for(AckMessageWrapper ack : ackMessageWrapperList){
                            if(ack.getAckCommit().getAckCommitDigest().equals(ackCommit)) {
                                signaturesList.put(ack.getFromPort(), ByteStringConverter.byteArrayToBase64String(ack.getAckCommit().getSignature()));
                            }
                        }

                        break;
                    }
                }

                if(ackCommit == null) {
                    log.error("Commit operation failed, didn't receive majority signatures");

                    // PERFORM CATCH UP OPERATION
                    // AND THEN REINITIATE PBFT
                    // AGAIN

                    return null;
                }
                else{
                    log.info("Received at least {} signatures, moving to committed", threshold);
                }

                return pbftService.execute(dbLog, prePrepare, signaturesList);

            }
            catch(InterruptedException | ExecutionException e){
                log.error("Error while broadcasting messages: {}", e.getMessage());
                LocalDateTime currentTime = LocalDateTime.now();
                log.info("{}", Stopwatch.getDuration(startTime, currentTime, "Commit"));
            }
        }
        catch(IOException e){
            log.error("IOException {}", e.getMessage());
            LocalDateTime currentTime = LocalDateTime.now();
            log.info("{}", Stopwatch.getDuration(startTime, currentTime, "Commit"));
        }
        catch (Exception e){
            log.error(e.getMessage());
            LocalDateTime currentTime = LocalDateTime.now();
            log.info("{}", Stopwatch.getDuration(startTime, currentTime, "Commit"));
        }

        return null;
    }
}
