package com.lab.pbft.util.PbftUtil;

import com.lab.pbft.config.KeyConfig;
import com.lab.pbft.config.client.ApiConfig;
import com.lab.pbft.model.primary.Log;
import com.lab.pbft.model.primary.ReplyLog;
import com.lab.pbft.model.primary.UserAccount;
import com.lab.pbft.networkObjects.acknowledgements.AckCommit;
import com.lab.pbft.networkObjects.acknowledgements.ClientReply;
import com.lab.pbft.networkObjects.acknowledgements.Reply;
import com.lab.pbft.networkObjects.communique.PrePrepare;
import com.lab.pbft.networkObjects.communique.Request;
import com.lab.pbft.repository.primary.LogRepository;
import com.lab.pbft.repository.primary.ReplyLogRepository;
import com.lab.pbft.repository.primary.UserAccountRepository;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@Slf4j
public class Execute {

    @Autowired
    @Lazy
    private SocketService socketService;
    @Autowired
    @Lazy
    private LogRepository logRepository;
    @Autowired
    @Lazy
    private KeyConfig keyConfig;
    @Autowired
    @Lazy
    private SocketMessageUtil socketMessageUtil;
    @Autowired
    @Lazy
    private UserAccountRepository userAccountRepository;

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    @Autowired
    @Lazy
    private ApiConfig apiConfig;
    @Autowired
    @Lazy
    private ReplyLogRepository replyLogRepository;

    public ClientReply execute(Log dbLog, com.lab.pbft.networkObjects.communique.PrePrepare prePrepare, Map<Long, String> signatures) {

        LocalDateTime startTime = LocalDateTime.now();

        try{
            log.info("Pbft : execute initiated on port {}", socketService.getAssignedPort());

            // 2f+1 commits collected in commit phase

            dbLog.setSignatures(signatures);
            dbLog.setType(Log.Type.COMMITED);

            dbLog = logRepository.save(dbLog);

            com.lab.pbft.networkObjects.communique.Execute execute = com.lab.pbft.networkObjects.communique.Execute.builder()
                    .currentView(prePrepare.getCurrentView())
                    .sequenceNumber(prePrepare.getSequenceNumber())
                    .requestDigest(prePrepare.getRequestDigest())
                    .prePrepare(prePrepare)
                    .signatures(signatures)
                    .build();

            MessageWrapper messageWrapper = MessageWrapper.builder()
                    .type(MessageWrapper.MessageType.EXECUTE)
                    .execute(execute)
                    .fromPort(socketService.getAssignedPort())
                    .build();

            try{
                List<AckMessageWrapper> ackMessageWrapperList = socketMessageUtil.broadcast(messageWrapper).get();

                Map<String, Integer> receipts = new HashMap<>();

                log.info("Received reply from {} servers", ackMessageWrapperList.size());
                LocalDateTime currentTime = LocalDateTime.now();
                log.info("{}", Stopwatch.getDuration(startTime, currentTime, "Execute"));

                log.info("Beginning execution of the request");

                Reply selfReply = executeRequest(prePrepare);

                if(selfReply == null) return null;

                dbLog.setApproved(selfReply.isApproved());
                dbLog.setType(Log.Type.EXECUTED);
                dbLog = logRepository.save(dbLog);

                ReplyLog replyLog = ReplyLog.builder()
                        .timestamp(selfReply.getTimestamp())
                        .clientId(prePrepare.getRequest().getClientId())
                        .reply(selfReply)
                        .build();

                replyLogRepository.save(replyLog);

                AckMessageWrapper selfAck = AckMessageWrapper.builder()
                        .type(AckMessageWrapper.MessageType.REPLY)
                        .fromPort(socketService.getAssignedPort())
                        .toPort(socketService.getAssignedPort())
                        .reply(selfReply)
                        .build();

                selfAck.signMessage(keyConfig.getPrivateKey());

                ackMessageWrapperList.add(selfAck);

                for(AckMessageWrapper ackMessageWrapper : ackMessageWrapperList) {
                    if(!ackMessageWrapper.verifyMessage(keyConfig.getPublicKeyStore().get(ackMessageWrapper.getFromPort()))) {
                        log.error("Invalid signature for prepare message: {}", ackMessageWrapper);
                        continue;
                    }

                    Reply reply = ackMessageWrapper.getReply();

                    receipts.put(reply.getReplyDigest(), receipts.getOrDefault(reply.getReplyDigest(), 0) + 1);

                }

                int threshold = apiConfig.getServerPopulation() - (apiConfig.getServerPopulation()-1)/3;
                String reply = null;
                Map<Long, String> signaturesList = new HashMap<>();

                for(String x : receipts.keySet()) {
                    if(receipts.get(x) >= threshold && x.equals(selfReply.getReplyDigest())) {
                        reply = x;

                        for(AckMessageWrapper ack : ackMessageWrapperList){
                            if(ack.getReply().getReplyDigest().equals(reply)) {
                                signaturesList.put(ack.getFromPort(), ByteStringConverter.byteArrayToBase64String(ack.getReply().getSignature()));
                            }
                        }

                        break;
                    }
                }

                if(reply == null) {
                    log.error("Execute operation failed, didn't receive majority signatures");

                    // PERFORM CATCH UP OPERATION
                    // AND THEN REINITIATE PBFT
                    // AGAIN

                    return null;
                }
                else{
                    log.info("Received at least {} signatures, sending reply back to client", threshold);
                }

                return ClientReply.builder()
                        .currentView(selfReply.getCurrentView())
                        .timestamp(selfReply.getTimestamp())
                        .requestDigest(selfReply.getRequestDigest())
                        .finalBalance(selfReply.getFinalBalance())
                        .replyDigest(selfReply.getReplyDigest())
                        .approved(selfReply.isApproved())
                        .signatures(signaturesList)
                        .build();

            }
            catch(InterruptedException | ExecutionException e){
                log.error("Error while broadcasting messages: {}", e.getMessage());
                LocalDateTime currentTime = LocalDateTime.now();
                log.info("{}", Stopwatch.getDuration(startTime, currentTime, "Execute"));
            }
        }
        catch(IOException e){
            log.error("IOException {}", e.getMessage());
            LocalDateTime currentTime = LocalDateTime.now();
            log.info("{}", Stopwatch.getDuration(startTime, currentTime, "Execute"));
        }
        catch (Exception e){
            log.error(e.getMessage());
            LocalDateTime currentTime = LocalDateTime.now();
            log.info("{}", Stopwatch.getDuration(startTime, currentTime, "Execute"));
        }

        return null;
    }

    public Reply executeRequest(PrePrepare prePrepare) throws Exception {


        long currentSequenceNumber = prePrepare.getSequenceNumber();
        long previousSequenceNumber = currentSequenceNumber - 1;

        Log previousLog = (previousSequenceNumber>0)?(logRepository.findBySequenceNumber(previousSequenceNumber).orElse(null)):null;

        while (previousSequenceNumber>0 && (previousLog == null || (previousLog!=null && previousLog.getPrePrepare()==null))) {
            previousSequenceNumber--;
            previousLog = (previousSequenceNumber > 0) ? (logRepository.findBySequenceNumber(previousSequenceNumber).orElse(null)) : null;
        }

            if(previousLog == null || (previousLog!=null && previousLog.getType().equals(Log.Type.EXECUTED))) {


                long senderId = prePrepare.getRequest().getClientId();
                long receiverId = prePrepare.getRequest().getReceiverId();
                long amount = prePrepare.getRequest().getAmount();

                UserAccount sender = userAccountRepository.findById(senderId).orElse(null);
                UserAccount receiver = userAccountRepository.findById(receiverId).orElse(null);

                if(sender.getBalance() >= amount) {
                    sender.setBalance(sender.getBalance() - amount);
                    receiver.setBalance(receiver.getBalance() + amount);

                    userAccountRepository.save(sender);
                    userAccountRepository.save(receiver);

                    Reply selfReply = Reply.builder()
                            .currentView(prePrepare.getCurrentView())
                            .timestamp(prePrepare.getRequest().getTimestamp())
                            .requestDigest(prePrepare.getRequestDigest())
                            .finalBalance(sender.getBalance())
                            .approved(true)
                            .build();

                    selfReply.setReplyDigest(selfReply.getHash());
                    selfReply.signMessage(keyConfig.getPrivateKey());

                    return selfReply;

                }
                else {
                    log.error("Insufficient balance with sender: {} to perform: {}", prePrepare.getRequest().getClientId(), prePrepare.getRequest());
                    log.error("Rejecting transaction");

                    Reply selfReply = Reply.builder()
                            .currentView(prePrepare.getCurrentView())
                            .timestamp(prePrepare.getRequest().getTimestamp())
                            .requestDigest(prePrepare.getRequestDigest())
                            .finalBalance(sender.getBalance())
                            .approved(false)
                            .build();

                    selfReply.setReplyDigest(selfReply.getHash());
                    selfReply.signMessage(keyConfig.getPrivateKey());

                    return selfReply;
                }

            }

            else {

                log.error("Previous logs not executed");
                // executorService.execute(() -> executeAllRequests(prePrepare));

                // UNLIKELY CASE BUT HANDLE THIS
                // IF WE LAND ON ANY SEQUENCE NUMBER
                // WHOSE PREVIOUS REQUESTS HAVE NOT BEEN
                // EXECUTED, SO FIRST GO AND EXECUTE THEM
                // THEN COME SEQUENTIALLY TO THIS TO EXECUTE
                // IF THOSE REQUESTS HAVE STILL NOT COMMITTED
                // THEN RESTART PHASES FOR THOSE

                return null;
            }
        }


    }

