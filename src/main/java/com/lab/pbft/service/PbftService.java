package com.lab.pbft.service;

import com.lab.pbft.model.primary.Log;
import com.lab.pbft.networkObjects.acknowledgements.ClientReply;
import com.lab.pbft.networkObjects.acknowledgements.Reply;
import com.lab.pbft.networkObjects.communique.Request;
import com.lab.pbft.util.PbftUtil.*;
import com.lab.pbft.wrapper.MessageWrapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

@Service
@Slf4j
@Getter
@Setter
public class PbftService {

    @Autowired
    PrePrepare prePrepare;
    @Autowired
    private Prepare prepare;
    @Autowired
    private Commit commit;
    @Autowired
    private AckCommit ackCommit;
    @Autowired
    private Execute execute;
    @Autowired
    private AckExecute ackExecute;

    public ClientReply prePrepare(Request request) { // Send prepare message to be signed
        return prePrepare.prePrepare(request);
    }

    public void prepare(ObjectInputStream in, ObjectOutputStream out, MessageWrapper messageWrapper) throws Exception { // Send commit message to be signed piggybacked with signed prepare message
        prepare.prepare(in, out, messageWrapper);
    }

    public ClientReply commit(Log dbLog, com.lab.pbft.networkObjects.communique.PrePrepare prePrepare, Map<Long, String> signatures) { // Send reply message to be signed piggybacked with signed commit message
        return commit.commit(dbLog, prePrepare, signatures);
    }

    public void ackCommit(ObjectInputStream in, ObjectOutputStream out, MessageWrapper messageWrapper) throws Exception {
        ackCommit.ackCommit(in, out, messageWrapper);
    }

    public ClientReply execute(Log dbLog, com.lab.pbft.networkObjects.communique.PrePrepare prePrepare, Map<Long, String> signatures) {
        return execute.execute(dbLog, prePrepare, signatures);
    }

    public void ackExecute(ObjectInputStream in, ObjectOutputStream out, MessageWrapper messageWrapper) throws Exception {
        ackExecute.ackExecute(in, out, messageWrapper);
    }

}
