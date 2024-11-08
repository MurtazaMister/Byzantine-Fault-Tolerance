package com.lab.pbft.util.PbftUtil;

import com.lab.pbft.config.KeyConfig;
import com.lab.pbft.config.client.ApiConfig;
import com.lab.pbft.networkObjects.communique.ViewChange;
import com.lab.pbft.service.SocketService;
import com.lab.pbft.util.ConverterUtil.ByteStringConverter;
import com.lab.pbft.util.SocketMessageUtil;
import com.lab.pbft.wrapper.AckMessageWrapper;
import com.lab.pbft.wrapper.MessageWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class NewView {

    @Autowired
    @Lazy
    private ApiConfig apiConfig;

    @Value("${socket.view.change.timeout}")
    private int viewChangeTimeout;

    // Integer - Indicates the view for which the view_change messages are
    // List<ViewChange> - All the view change messages received to transition to view_change <Integer>

    Map<Integer, List<ViewChange>> listViewChange = new ConcurrentHashMap<>();
    Map<Integer, com.lab.pbft.networkObjects.acknowledgements.NewView> newViewMap = new ConcurrentHashMap<>();

    @Autowired
    @Lazy
    private SocketService socketService;
    @Autowired
    @Lazy
    private KeyConfig keyConfig;
    @Autowired
    @Lazy
    private SocketMessageUtil socketMessageUtil;

    public void newView(ObjectInputStream in, ObjectOutputStream out, MessageWrapper messageWrapper) throws Exception {

        // Received new view message
        // keep a count of messages
        // which concerns this server
        // to be the leader

        // Once the count reaches 2f+1
        // send acknowledgement to all the servers
        // that sent the view change message

        ViewChange viewChange = messageWrapper.getViewChange();

        if(!viewChange.verifyMessage(keyConfig.getPublicKeyStore().get(messageWrapper.getFromPort()))){
            log.info("Unverified message received, terminating");
            return;
        }

        int threshold2f = apiConfig.getServerPopulation() - (apiConfig.getServerPopulation()-1)/3 - 1;

        if(listViewChange.containsKey(viewChange.getView())) {
            if(listViewChange.get(viewChange.getView()).size() == threshold2f) {

                // construct a new new-view message
                // and broadcast to all.

                // by adding this, broadcast new view
                // as 2f+1 received

                // view change messages are signed individually!

                listViewChange.get(viewChange.getView()).add(viewChange);

                // 2f+1 complete
                // Prepare new view message

                List<com.lab.pbft.networkObjects.acknowledgements.NewView.Bundle> bundleList = new ArrayList<>();
                bundleList.add(null);
                int bundleListSize = 0;

                Map<Long, String> signatures = new HashMap<>();

                for(ViewChange vc : listViewChange.get(viewChange.getView())) {
                    for(ViewChange.Bundle b : vc.getBundles()) {
                        while(bundleListSize < b.getSequenceNumber()){
                            bundleList.add(null);
                            bundleListSize++;
                        }
                        if(bundleList.get((int)b.getSequenceNumber()) == null){
                            bundleList.set((int)b.getSequenceNumber(), com.lab.pbft.networkObjects.acknowledgements.NewView.Bundle.toBundle(b));
                        }
                    }

                    signatures.put(vc.getFromNode(), ByteStringConverter.byteArrayToBase64String(vc.getSignature()));

                }

                com.lab.pbft.networkObjects.acknowledgements.NewView newView = com.lab.pbft.networkObjects.acknowledgements.NewView.builder()
                        .view(viewChange.getView())
                        .bundles(bundleList)
                        .signatures(signatures)
                        .build();

                newViewMap.put(viewChange.getView(), newView);

                AckMessageWrapper ackMessageWrapper = AckMessageWrapper.builder()
                        .type(AckMessageWrapper.MessageType.NEW_VIEW)
                        .fromPort(socketService.getAssignedPort())
                        .toPort(messageWrapper.getFromPort())
                        .newView(newView)
                        .build();

                ackMessageWrapper.signMessage(keyConfig.getPrivateKey());

                out.writeObject(ackMessageWrapper);

                // Broadcasting the new view message
                log.info("Broadcasting the new view message");

                MessageWrapper mw = MessageWrapper.builder()
                        .type(MessageWrapper.MessageType.NEW_VIEW)
                        .newView(newView)
                        .fromPort(socketService.getAssignedPort())
                        .build();

                socketMessageUtil.broadcast(mw);

            }
            else if(listViewChange.get(viewChange.getView()).size() > threshold2f) {

                // This is after broadcasting, just
                // resend this particular node the new-view message again
                // I guess, it will handle 2 new views then?
                // Yes it will

                // Exceeded threshold, send new view directly

                // can we wait for some seconds before we receive every message??

                // don't append to hashmap now, just send
                // the new view message, just send it bro <3

                int tries = (viewChangeTimeout)/100;

                while(tries-->0){
                    if(!newViewMap.containsKey(viewChange.getView())) {
                        log.info("Sleeping until new view prepared");
                        Thread.sleep(100);
                    }
                    else{
                        break;
                    }
                }

                if(listViewChange.get(viewChange.getView()).size() > threshold2f && newViewMap.containsKey(viewChange.getView())) {
                    AckMessageWrapper ackMessageWrapper = AckMessageWrapper.builder()
                            .type(AckMessageWrapper.MessageType.NEW_VIEW)
                            .fromPort(socketService.getAssignedPort())
                            .toPort(messageWrapper.getFromPort())
                            .newView(newViewMap.get(viewChange.getView()))
                            .build();

                    ackMessageWrapper.signMessage(keyConfig.getPrivateKey());

                    out.writeObject(ackMessageWrapper);
                }

            }
            else {

                // just add and forget
                // how to send ack back?
                // as I don't have enough messages rn...

                // Okay so this is a different thread,
                // lets block it in a while loop until we
                // achieve 2f+1 messages!

                listViewChange.get(viewChange.getView()).add(viewChange);

                int tries = (viewChangeTimeout)/100;

                while(tries-->0){
                    if(listViewChange.get(viewChange.getView()).size() <= threshold2f) {
                        log.info("Sleeping until 2f+1 view change messages");
                        Thread.sleep(100);
                    }
                    else{
                        if(!newViewMap.containsKey(viewChange.getView())) {
                            log.info("Sleeping until new view prepared");
                            Thread.sleep(100);
                        }
                        else{
                            break;
                        }
                    }
                }

                if(listViewChange.get(viewChange.getView()).size() > threshold2f && newViewMap.containsKey(viewChange.getView())) {
                    AckMessageWrapper ackMessageWrapper = AckMessageWrapper.builder()
                                    .type(AckMessageWrapper.MessageType.NEW_VIEW)
                                    .fromPort(socketService.getAssignedPort())
                                    .toPort(messageWrapper.getFromPort())
                                    .newView(newViewMap.get(viewChange.getView()))
                                    .build();

                    ackMessageWrapper.signMessage(keyConfig.getPrivateKey());

                    out.writeObject(ackMessageWrapper);
                }

            }
        }
        else {

            // Okay so this is a different thread,
            // lets block it in a while loop until we
            // achieve 2f+1 messages!

            listViewChange.put(viewChange.getView(), new ArrayList<>());
            listViewChange.get(viewChange.getView()).add(viewChange);

            int tries = (viewChangeTimeout)/100;

            while(tries-->0){
                if(listViewChange.get(viewChange.getView()).size() <= threshold2f) {
                    log.info("Sleeping until 2f+1 view change messages");
                    Thread.sleep(100);
                }
                else{
                    if(!newViewMap.containsKey(viewChange.getView())) {
                        log.info("Sleeping until new view prepared");
                        Thread.sleep(100);
                    }
                    else{
                        break;
                    }
                }
            }

            if(listViewChange.get(viewChange.getView()).size() > threshold2f && newViewMap.containsKey(viewChange.getView())) {
                AckMessageWrapper ackMessageWrapper = AckMessageWrapper.builder()
                        .type(AckMessageWrapper.MessageType.NEW_VIEW)
                        .fromPort(socketService.getAssignedPort())
                        .toPort(messageWrapper.getFromPort())
                        .newView(newViewMap.get(viewChange.getView()))
                        .build();

                ackMessageWrapper.signMessage(keyConfig.getPrivateKey());

                out.writeObject(ackMessageWrapper);
            }

        }

    }

}
