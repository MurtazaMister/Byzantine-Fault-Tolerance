package com.lab.pbft.util.PbftUtil;

import com.lab.pbft.config.KeyConfig;
import com.lab.pbft.config.client.ApiConfig;
import com.lab.pbft.model.primary.Log;
import com.lab.pbft.model.primary.ReplyLog;
import com.lab.pbft.networkObjects.acknowledgements.AckCommit;
import com.lab.pbft.networkObjects.acknowledgements.Reply;
import com.lab.pbft.networkObjects.communique.Commit;
import com.lab.pbft.networkObjects.communique.Execute;
import com.lab.pbft.repository.primary.LogRepository;
import com.lab.pbft.repository.primary.ReplyLogRepository;
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
public class AckExecute {

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
    private LogRepository logRepository;
    @Autowired
    @Lazy
    private ReplyLogRepository replyLogRepository;
    @Autowired
    @Lazy
    private com.lab.pbft.util.PbftUtil.Execute executeUtil;
    @Autowired
    @Lazy
    private KeyConfig keyConfig;

    public void ackExecute(ObjectInputStream in, ObjectOutputStream out, MessageWrapper messageWrapper) throws Exception {
        Execute execute = messageWrapper.getExecute();

        // Verify all the signatures of commit message

        String ackCommitDigest = AckCommit.builder()
                .currentView(execute.getCurrentView())
                .sequenceNumber(execute.getSequenceNumber())
                .requestDigest(execute.getRequestDigest())
                .build().getHash();

        int verificationCount = 0;

        for (long id : execute.getSignatures().keySet()) {
            if(signatureVerificationUtil.verifySignature(id, ackCommitDigest, ByteStringConverter.base64StringToByteArray(execute.getSignatures().get(id)))) {
                verificationCount++;
            }
        }

        int threshold = apiConfig.getServerPopulation() - (apiConfig.getServerPopulation()-1)/3;;

        if(verificationCount < threshold){
            log.error("Invalid signatures provided for commit messages, rejecting");
            return;
        }

        log.info("Verified threshold signatures of commit messages, moving to execute");

        Log dbLog = logRepository.findBySequenceNumber(execute.getSequenceNumber()).orElse(null);

        if(dbLog == null) {
            dbLog = Log.builder()
                    .sequenceNumber(execute.getSequenceNumber())
                    .viewNumber(execute.getCurrentView())
                    .type(Log.Type.COMMITED)
                    .signatures(execute.getSignatures())
                    .prePrepare(execute.getPrePrepare())
                    .build();

            dbLog = logRepository.save(dbLog);
        }
        else {
            dbLog.setType(Log.Type.COMMITED);
            dbLog.setSignatures(execute.getSignatures());

            dbLog = logRepository.save(dbLog);
        }

        Reply reply = executeUtil.executeRequest(execute.getPrePrepare());

        if(reply == null){
            // If previous transactions are not executed

            AckMessageWrapper ackMessageWrapper = AckMessageWrapper.builder()
                    .type(AckMessageWrapper.MessageType.REPLY)
                    .fromPort(socketService.getAssignedPort())
                    .toPort(messageWrapper.getFromPort())
                    .reply(null)
                    .build();

            ackMessageWrapper.signMessage(keyConfig.getPrivateKey());

            log.info("Sending null reply, did not execute request");

            out.writeObject(ackMessageWrapper);

            return;
        }

        dbLog.setApproved(reply.isApproved());
        dbLog.setType(Log.Type.EXECUTED);
        dbLog = logRepository.save(dbLog);

        ReplyLog replyLog = ReplyLog.builder()
                .timestamp(reply.getTimestamp())
                .clientId(execute.getPrePrepare().getRequest().getClientId())
                .reply(reply)
                .build();

        replyLogRepository.save(replyLog);

        AckMessageWrapper ackMessageWrapper = AckMessageWrapper.builder()
                .type(AckMessageWrapper.MessageType.REPLY)
                .fromPort(socketService.getAssignedPort())
                .toPort(messageWrapper.getFromPort())
                .reply(reply)
                .build();

        ackMessageWrapper.signMessage(keyConfig.getPrivateKey());

        log.info("Sending reply after executing");

        out.writeObject(ackMessageWrapper);

    }

}
