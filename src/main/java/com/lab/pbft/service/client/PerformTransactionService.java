package com.lab.pbft.service.client;

import com.lab.pbft.config.KeyConfig;
import com.lab.pbft.config.client.ApiConfig;
import com.lab.pbft.networkObjects.acknowledgements.ClientReply;
import com.lab.pbft.networkObjects.acknowledgements.Reply;
import com.lab.pbft.networkObjects.communique.Request;
import com.lab.pbft.wrapper.AckMessageWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class PerformTransactionService {

    @Autowired
    @Lazy
    private ApiService apiService;
    @Value("${client.broadcast.wait}")
    private int broadcastWait;
    @Autowired
    @Lazy
    private KeyConfig keyConfig;
    @Autowired
    @Lazy
    private ApiConfig apiConfig;

    public void performTransaction(Request request) throws Exception {
        ClientReply clientReply = apiService.transact(request);

        if(clientReply == null) {
            System.out.println("Transaction failed");
        }
        else if(clientReply.getCurrentView() == -1){
            try {
                System.out.println("Didn't receive reply, broadcasting request after "+broadcastWait+" ms");
                Thread.sleep(broadcastWait);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("Broadcasting");

            List<AckMessageWrapper> acks = apiService.retransact(request);

            int signatureCount = 0;
            Map<String, Integer> receipts = new HashMap<>();

            for(AckMessageWrapper ack : acks) {
                Reply reply = ack.getReply();

                if (reply.verifyMessage(keyConfig.getPublicKeyStore().get(ack.getFromPort()))){
                    receipts.put(reply.getReplyDigest(), receipts.getOrDefault(reply.getReplyDigest(), 0) + 1);
                }
            }

            int threshold = (apiConfig.getServerPopulation() - 1) / 3 + 1;
            String finalDigest = null;

            for(String x : receipts.keySet()) {
                if(receipts.get(x) >= threshold) {
                    finalDigest = x;
                    signatureCount = receipts.get(x);
                    threshold = signatureCount;
                }
            }

            Reply finalReply = null;
            for(AckMessageWrapper ack : acks) {
                if(ack.getReply().getReplyDigest().equals(finalDigest)) {
                    finalReply = ack.getReply();
                    break;
                }
            }

            if (finalReply != null && signatureCount != 0) {
                System.out.println("Verified at least f+1 signatures in re-transact, received " + signatureCount + " signatures");
                System.out.println("Transaction status: "+((finalReply.isApproved())?"Approved":"Failed"));
                System.out.println("Final balance = $" + finalReply.getFinalBalance() + "\nAfter executing "+((finalReply.isApproved())?"approved":"failed")+" transaction $" + request.getAmount() + " : " + (char)(request.getClientId()+(int)'A'-1) + " -> " + (char)(request.getReceiverId()+(int)'A'-1));
                if(apiConfig.getCurrentView() != finalReply.getCurrentView()){
                    System.out.println("View changed, current view: "+ finalReply.getCurrentView());
                }
                apiConfig.setCurrentView((int)finalReply.getCurrentView());
            } else {
                System.out.println("Received unverified reply with only "+signatureCount+" signatures for transaction " + request.getAmount() + " : " + request.getClientId() + " -> " + request.getReceiverId());
            }

        }
        else{
            if(!clientReply.getTimestamp().equals(request.getTimestamp())){
                System.out.println("Timestamps don't match, invalid reply received");
            }
            else {

                // Verify the client reply signatures
                int signatureCount = 0;

                for (long id : clientReply.getSignatures().keySet()) {
                    if (clientReply.verifyMessage(clientReply.getSignatures().get(id), keyConfig.getPublicKeyStore().get(id)))
                        signatureCount++;
                }

                int threshold = (apiConfig.getServerPopulation() - 1) / 3 + 1;

                if (signatureCount >= threshold) {
                    System.out.println("Verified at least f+1 signatures, received " + signatureCount + " signatures");
                    System.out.println("Transaction status: "+((clientReply.isApproved())?"Approved":"Failed"));
                    System.out.println("Final balance = $" + clientReply.getFinalBalance() + "\nAfter executing "+ ((clientReply.isApproved())?"approved":"failed") +" transaction $" + request.getAmount() + " : " + request.getClientId() + " -> " + request.getReceiverId());
                    if(apiConfig.getCurrentView() != clientReply.getCurrentView()){
                        System.out.println("View changed, current view: "+ clientReply.getCurrentView());
                    }
                    apiConfig.setCurrentView((int)clientReply.getCurrentView());
                } else {
                    System.out.println("Received unverified reply for transaction $" + request.getAmount() + " : " + request.getClientId() + " -> " + request.getReceiverId()+ " with signature count = "+signatureCount);
                }
            }

        }
    }

}
