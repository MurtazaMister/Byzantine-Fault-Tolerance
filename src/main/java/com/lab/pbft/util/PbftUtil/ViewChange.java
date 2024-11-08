package com.lab.pbft.util.PbftUtil;

import com.lab.pbft.config.KeyConfig;
import com.lab.pbft.config.client.ApiConfig;
import com.lab.pbft.model.primary.Log;
import com.lab.pbft.repository.primary.LogRepository;
import com.lab.pbft.service.PbftService;
import com.lab.pbft.service.SocketService;
import com.lab.pbft.util.ConverterUtil.ByteStringConverter;
import com.lab.pbft.util.ServerStatusUtil;
import com.lab.pbft.util.SocketMessageUtil;
import com.lab.pbft.wrapper.AckMessageWrapper;
import com.lab.pbft.wrapper.MessageWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ViewChange {

    @Autowired
    @Lazy
    private LogRepository logRepository;
    @Autowired
    @Lazy
    private SocketService socketService;
    @Autowired
    @Lazy
    private ServerStatusUtil serverStatusUtil;
    @Autowired
    private SocketMessageUtil socketMessageUtil;
    @Value("${socket.view.change.timeout}")
    private int viewChangeTimeout;
    @Value("${socket.pbft.connection.timeout}")
    private int pbftConnectionTimeout;
    @Autowired
    @Lazy
    private ApiConfig apiConfig;

    @Autowired
    private NewView newView;
    @Autowired
    @Lazy
    private KeyConfig keyConfig;
    @Autowired
    @Lazy
    private PbftService pbftService;

    public void viewChange() {

        List<Log> logList = logRepository.findAllByOrderBySequenceNumberAsc();

        List<com.lab.pbft.networkObjects.communique.ViewChange.Bundle> bundles = new ArrayList<>();

        for(Log log : logList){
            if(log.getType() != Log.Type.PRE_PREPARE) {
                bundles.add(com.lab.pbft.networkObjects.communique.ViewChange.Bundle.builder()
                        .sequenceNumber(log.getSequenceNumber())
                        .prePrepare(log.getPrePrepare())
                        .signatures(log.getSignatures())
                        .approved(log.isApproved())
                        .build());
            }
        }

        com.lab.pbft.networkObjects.communique.ViewChange viewChange = com.lab.pbft.networkObjects.communique.ViewChange.builder()
                .bundles(bundles)
                .fromNode(socketService.getAssignedPort())
                .build();

        MessageWrapper messageWrapper = MessageWrapper.builder()
                .type(MessageWrapper.MessageType.VIEW_CHANGE)
                .fromPort(socketService.getAssignedPort())
                .build();

        // Introducing a new variable view change transition
        // No server will accept any messages except new view
        // and view-change

        serverStatusUtil.setViewChangeTransition(true);
        int tries = (apiConfig.getServerPopulation() - 1) / 3 + 1;
        int toView = socketService.getCurrentView()+1;

        AckMessageWrapper ackMessageWrapper = null;

        while(tries-->0){
            viewChange.setView(toView);
            log.info("Attempting view change: change view to {}", toView);
            try {
                viewChange.signMessage(keyConfig.getPrivateKey());
            } catch (Exception e) {
                log.error("Unable to sign message, error: {}", e.getMessage());
            }

            messageWrapper.setViewChange(viewChange);
            messageWrapper.setToPort(socketService.getLeader(toView));

            int threshold2f = apiConfig.getServerPopulation() - (apiConfig.getServerPopulation()-1)/3 - 1;

            if(socketService.getLeader(toView) == socketService.getAssignedPort()) {

                log.info("This node ({}) is the leader for the next view", socketService.getAssignedPort());

                if( newView.listViewChange.containsKey(toView) && newView.listViewChange.get(toView).size() == threshold2f ) {
                    // Equals 2f exactly, 1 will be contributed by this server
                    // itself, add and get new view
                    // from its stored variable

                    newView.listViewChange.get(viewChange.getView()).add(viewChange);

                    // 2f+1 complete
                    // Prepare new view message

                    List<com.lab.pbft.networkObjects.acknowledgements.NewView.Bundle> bundleList = new ArrayList<>();
                    bundleList.add(null);
                    int bundleListSize = 0;

                    Map<Long, String> signatures = new HashMap<>();

                    for(com.lab.pbft.networkObjects.communique.ViewChange vc : newView.listViewChange.get(viewChange.getView())) {
                        for(com.lab.pbft.networkObjects.communique.ViewChange.Bundle b : vc.getBundles()) {
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


                    com.lab.pbft.networkObjects.acknowledgements.NewView newViewMsg = com.lab.pbft.networkObjects.acknowledgements.NewView.builder()
                            .view(viewChange.getView())
                            .bundles(bundleList)
                            .signatures(signatures)
                            .build();

                    newView.newViewMap.put(viewChange.getView(), newViewMsg);

                    ackMessageWrapper = AckMessageWrapper.builder()
                            .type(AckMessageWrapper.MessageType.NEW_VIEW)
                            .fromPort(socketService.getAssignedPort())
                            .toPort(socketService.getAssignedPort())
                            .newView(newViewMsg)
                            .build();

                    try {
                        ackMessageWrapper.signMessage(keyConfig.getPrivateKey());
                    } catch (Exception e) {
                        log.error("Unable to sign message, error: {}", e.getMessage());
                    }


                    // Broadcasting the new view message
                    log.info("Broadcasting the new view message");

                    MessageWrapper mw = MessageWrapper.builder()
                            .type(MessageWrapper.MessageType.NEW_VIEW)
                            .newView(newViewMsg)
                            .fromPort(socketService.getAssignedPort())
                            .build();

                    try {
                        socketMessageUtil.broadcast(mw);
                    } catch (IOException e) {
                        log.error("Unable to broadcast");
                    }

                }
                else if(newView.listViewChange.containsKey(toView) && newView.listViewChange.get(toView).size() > threshold2f) {

                    int tries2 = (viewChangeTimeout)/100;

                    while(tries2-->0){
                        if(!newView.newViewMap.containsKey(viewChange.getView())) {
                            log.info("Sleeping until new view prepared");
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                log.error("Unable to sleep, error: {}", e.getMessage());
                            }
                        }
                        else{
                            break;
                        }
                    }

                    if(newView.listViewChange.get(viewChange.getView()).size() > threshold2f && newView.newViewMap.containsKey(viewChange.getView())) {
                        ackMessageWrapper = AckMessageWrapper.builder()
                                .type(AckMessageWrapper.MessageType.NEW_VIEW)
                                .fromPort(socketService.getAssignedPort())
                                .toPort(messageWrapper.getFromPort())
                                .newView(newView.newViewMap.get(viewChange.getView()))
                                .build();

                        try {
                            ackMessageWrapper.signMessage(keyConfig.getPrivateKey());
                        } catch (Exception e) {
                            log.error("Unable to sign message, error: {}", e.getMessage());
                        }
                    }

                }
                else{

                    if(!newView.listViewChange.containsKey(viewChange.getView())) newView.listViewChange.put(viewChange.getView(), new ArrayList<>());
                    newView.listViewChange.get(viewChange.getView()).add(viewChange);

                    int tries2 = (viewChangeTimeout)/100;

                    while(tries2-->0){
                        if(newView.listViewChange.get(viewChange.getView()).size() <= threshold2f) {
                            log.info("Sleeping until 2f+1 view change messages");
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                log.error("Unable to sleep, error: {}", e.getMessage());
                            }
                        }
                        else{
                            if(!newView.newViewMap.containsKey(viewChange.getView())) {
                                log.info("Sleeping until new view prepared");
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    log.error("Unable to sleep, error: {}", e.getMessage());
                                }
                            }
                            else{
                                break;
                            }
                        }
                    }

                    if(newView.listViewChange.get(viewChange.getView()).size() > threshold2f && newView.newViewMap.containsKey(viewChange.getView())) {
                        ackMessageWrapper = AckMessageWrapper.builder()
                                .type(AckMessageWrapper.MessageType.NEW_VIEW)
                                .fromPort(socketService.getAssignedPort())
                                .toPort(messageWrapper.getFromPort())
                                .newView(newView.newViewMap.get(viewChange.getView()))
                                .build();

                        try {
                            ackMessageWrapper.signMessage(keyConfig.getPrivateKey());
                        } catch (Exception e) {
                            log.error("Unable to sign ack, error: {}", e.getMessage());
                        }
                    }

                }

                if(ackMessageWrapper != null) break;

            }
            else {
                log.info("Sending view change for view: {}", toView);
                try {
                    ackMessageWrapper = socketMessageUtil.sendMessageToServer(socketService.getLeader(toView), messageWrapper, pbftConnectionTimeout, viewChangeTimeout);

                    // Receive new-view as ack of any kind after a set timer
                    // if not, increase the view change
                    // number and go for another view change
                    // this will continue until f+1 nodes
                    // if no new view is received
                    // reset to the original view and continue processing

                    if (ackMessageWrapper != null) break;

                } catch (IOException e) {
                    log.error("View proposal failed for {}, trying next view {}", toView, toView+1);
                }
            }
            toView++;
        }

        if(ackMessageWrapper != null) {

            // DO THE SETTINGS
            // RESET VIEW NUMBER
            // RESET VIEW CHANGE TRANSITION TO FALSE

            pbftService.newViewProcess(ackMessageWrapper.getNewView());

        }
        else {

            // reset to original view number
            // set view change transition to false

            // send a SYNC to leader and
            // recover your logs

            serverStatusUtil.setViewChangeTransition(false);

            // SEND A SYNC
            // TO THE SERVER
            // TO RECOVER YOUR
            // LOGS

        }

    }

}
