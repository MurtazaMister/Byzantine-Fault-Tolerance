package com.lab.pbft.util.PbftUtil;

import com.lab.pbft.config.KeyConfig;
import com.lab.pbft.config.client.ApiConfig;
import com.lab.pbft.model.primary.Log;
import com.lab.pbft.networkObjects.acknowledgements.ClientReply;
import com.lab.pbft.networkObjects.acknowledgements.Prepare;
import com.lab.pbft.networkObjects.acknowledgements.Reply;
import com.lab.pbft.networkObjects.communique.Request;
import com.lab.pbft.repository.primary.LogRepository;
import com.lab.pbft.service.PbftService;
import com.lab.pbft.service.SocketService;
import com.lab.pbft.util.ConverterUtil.ByteStringConverter;
import com.lab.pbft.util.ServerStatusUtil;
import com.lab.pbft.util.SocketMessageUtil;
import com.lab.pbft.util.Stopwatch;
import com.lab.pbft.wrapper.AckMessageWrapper;
import com.lab.pbft.wrapper.MessageWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
public class PrePrepare {
    @Autowired
    @Lazy
    private ServerStatusUtil serverStatusUtil;
    @Value("${server.resume.timeout}")
    private int serverResumeDelay;
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
    @Autowired
    @Lazy
    private LogRepository logRepository;

    public ClientReply prePrepare(Request request) {
        if(serverStatusUtil.isFailed() || serverStatusUtil.isViewChangeTransition()) {
            return null;
        }

        LocalDateTime startTime = LocalDateTime.now();
        log.info("Pbft : pre-prepare initiated on port {}", socketService.getAssignedPort());

        try{

            Log lastLog = logRepository.findTopByOrderBySequenceNumberDesc().orElse(null);
            long sequenceNumber = ((lastLog == null) ? 0 : lastLog.getSequenceNumber()) + 1;

            // check if this sequence number is not occupied in the DB

            com.lab.pbft.networkObjects.communique.PrePrepare prePrepare = com.lab.pbft.networkObjects.communique.PrePrepare.builder()
                    .currentView(socketService.getCurrentView())
                    .sequenceNumber(sequenceNumber)
                    .requestDigest(request.getHash())
                    .request(request)
                    .build();

            Log dbLog = Log.builder()
                        .sequenceNumber(sequenceNumber)
                        .viewNumber(socketService.getCurrentView())
                        .type(Log.Type.PRE_PREPARE)
                        .prePrepare(prePrepare)
                        .build();

            dbLog = logRepository.save(dbLog);

            log.info("Sending pre-prepare message: {}", prePrepare);

            MessageWrapper socketMessageWrapper = MessageWrapper.builder()
                    .type(MessageWrapper.MessageType.PRE_PREPARE)
                    .prePrepare(prePrepare)
                    .fromPort(socketService.getAssignedPort())
                    .build();

            try{
                List<AckMessageWrapper> ackMessageWrapperList = socketMessageUtil.broadcast(socketMessageWrapper).get();

                log.info("Received prepare from {} servers", ackMessageWrapperList.size());
                LocalDateTime currentTime = LocalDateTime.now();
                log.info("{}", Stopwatch.getDuration(startTime, currentTime, "Pre-prepare"));

                Map<String, Integer> receipts = new HashMap<>();

                // Appending collectors own prepare message in the map

                Prepare selfPrepare = Prepare.builder()
                        .currentView(prePrepare.getCurrentView())
                        .sequenceNumber(prePrepare.getSequenceNumber())
                        .requestDigest(prePrepare.getRequestDigest())
                        .build();

                selfPrepare.setPrepareDigest(selfPrepare.getHash());
                selfPrepare.signMessage(keyConfig.getPrivateKey());

                AckMessageWrapper selfAck = AckMessageWrapper.builder()
                        .type(AckMessageWrapper.MessageType.PREPARE)
                        .fromPort(socketService.getAssignedPort())
                        .toPort(socketService.getAssignedPort())
                        .prepare(selfPrepare)
                        .build();

                selfAck.signMessage(keyConfig.getPrivateKey());

                ackMessageWrapperList.add(selfAck);


                for(AckMessageWrapper ackMessageWrapper : ackMessageWrapperList){

                    // Verifying the prepare messages from all servers

                    if(!ackMessageWrapper.verifyMessage(keyConfig.getPublicKeyStore().get(ackMessageWrapper.getFromPort()))) {
                        log.error("Invalid signature for prepare message: {}", ackMessageWrapper);
                        continue;
                    }

                    Prepare prepare = ackMessageWrapper.getPrepare();

                    receipts.put(prepare.getPrepareDigest(), receipts.getOrDefault(prepare.getPrepareDigest(), 0) + 1);

                }

                // Verify which prepare in map has 2f+1 signatures

                int threshold = apiConfig.getServerPopulation() - (apiConfig.getServerPopulation()-1)/3;
                String prepared = null;
                Map<Long, String> signatures = new HashMap<>();

                for(String x : receipts.keySet()) {
                    if(receipts.get(x) >= threshold && x.equals(selfPrepare.getPrepareDigest())) {
                        prepared = x;

                        for(AckMessageWrapper ack : ackMessageWrapperList){
                            if(ack.getPrepare().getPrepareDigest().equals(prepared)) {
                                signatures.put(ack.getFromPort(), ByteStringConverter.byteArrayToBase64String(ack.getPrepare().getSignature()));
                            }
                        }

                        break;
                    }
                }

                if(prepared == null) {
                    log.error("Operation failed, didn't receive majority signatures");

                    // PERFORM CATCH UP OPERATION
                    // AND THEN REINITIATE PBFT
                    // AGAIN

                    return null;
                }
                else{
                    log.info("Received at least {} signatures, moving to commit", threshold);
                }

                return pbftService.commit(dbLog, prePrepare, signatures);

            }
            catch(InterruptedException | ExecutionException e){
                log.error("Error while broadcasting messages: {}", e.getMessage());
                LocalDateTime currentTime = LocalDateTime.now();
                log.info("{}", Stopwatch.getDuration(startTime, currentTime, "Pre-Prepare"));
            }
        }
        catch(IOException e){
            log.error("IOException {}", e.getMessage());
            LocalDateTime currentTime = LocalDateTime.now();
            log.info("{}", Stopwatch.getDuration(startTime, currentTime, "Pre-Prepare"));
        }
        catch (Exception e){
            log.error(e.getMessage());
            LocalDateTime currentTime = LocalDateTime.now();
            log.info("{}", Stopwatch.getDuration(startTime, currentTime, "Pre-Prepare"));
        }
        return null;
    }
}