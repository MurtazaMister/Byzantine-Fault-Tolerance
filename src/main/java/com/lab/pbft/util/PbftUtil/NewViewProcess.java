package com.lab.pbft.util.PbftUtil;

import com.lab.pbft.config.KeyConfig;
import com.lab.pbft.config.client.ApiConfig;
import com.lab.pbft.model.primary.Log;
import com.lab.pbft.model.primary.UserAccount;
import com.lab.pbft.networkObjects.acknowledgements.NewView;
import com.lab.pbft.repository.primary.LogRepository;
import com.lab.pbft.repository.primary.NewViewRepository;
import com.lab.pbft.repository.primary.UserAccountRepository;
import com.lab.pbft.service.SocketService;
import com.lab.pbft.util.ServerStatusUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@Slf4j
public class NewViewProcess {

    @Autowired
    @Lazy
    private ServerStatusUtil serverStatusUtil;

    boolean processing = false;
    @Autowired
    @Lazy
    private SocketService socketService;
    @Autowired
    @Lazy
    private KeyConfig keyConfig;
    @Autowired
    @Lazy
    private ApiConfig apiConfig;
    @Autowired
    @Lazy
    private LogRepository logRepository;
    @Autowired
    @Lazy
    private UserAccountRepository userAccountRepository;
    @Autowired
    @Lazy
    private NewViewRepository newViewRepository;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    private Log exists(List<Log> logs, int sequenceNumber) {
        for(Log log : logs) {
            if(log.getSequenceNumber() == sequenceNumber) return log;
        }
        return null;
    }

    private void replace(List<Log> logs, Log log) {
        for(int i = 0;i<logs.size();i++) {
            if(logs.get(i).getSequenceNumber() == log.getSequenceNumber()) {
                logs.set(i, log);
                return;
            }
        }
    }

    public void newViewProcess(NewView newView) {

        log.info("Processing NEW_VIEW message");

        if(processing){
            return;
        }

        int count = 0;
        for(long id : newView.getSignatures().keySet()) {
            try {
                if(newView.verifyMessage(newView.getSignatures().get(id), keyConfig.getPublicKeyStore().get(id))) {
                    count++;
                }
            } catch (Exception e) {
                log.error("Unable to verify signature for id: {}", id);
            }
        }

        int threshold = apiConfig.getServerPopulation() - (apiConfig.getServerPopulation() - 1)/3;

        if(count < threshold) return;

        log.info("Verfied 2f+1 view change messages");

        processing = true;

        executorService.submit(() -> {
            log.info("Persisting new_view for view: {}", newView.getView());
            newViewRepository.save(com.lab.pbft.model.primary.NewView.toNewView(newView));
        });

        socketService.setCurrentView(newView.getView());

        List<Log> allLogs = logRepository.findAllByOrderBySequenceNumberAsc();

        for(int i = 1; i< newView.getBundles().size(); i++) {
            if(newView.getBundles().get(i)==null) continue;

            Log dbLog = exists(allLogs, i);

            if(dbLog == null) {

                // Verifying bundle before adding
                int cnt = 0;
                for(long id : newView.getBundles().get(i).getSignatures().keySet()) {
                    try {
                        if(newView.getBundles().get(i).verifyMessage(newView.getBundles().get(i).getSignatures().get(id), keyConfig.getPublicKeyStore().get(id))){
                            cnt++;
                        }
                    } catch (Exception e) {
                        log.error("Unable to verify signature for id: {} in bundle for seq number: {}", id, newView.getBundles().get(i).getSequenceNumber());
                    }
                }
                int t = apiConfig.getServerPopulation() - (apiConfig.getServerPopulation()-1)/3;

                if(cnt < t){
                    log.warn("Invalid log message received in bundle for seq no: {}, skipping", newView.getBundles().get(i).getSequenceNumber());
                    continue;
                }

                Log newLog = Log.builder()
                        .sequenceNumber(i)
                        .viewNumber(socketService.getCurrentView())
                        .type(Log.Type.EXECUTED)
                        .approved(newView.getBundles().get(i).isApproved())
                        .prePrepare(newView.getBundles().get(i).getPrePrepare())
                        .signatures(newView.getBundles().get(i).getSignatures())
                        .build();

                if(newLog.isApproved()){

                    long senderId = newLog.getPrePrepare().getRequest().getClientId();
                    long receiverId = newLog.getPrePrepare().getRequest().getReceiverId();
                    long amount = newLog.getPrePrepare().getRequest().getAmount();

                    UserAccount sender = userAccountRepository.findById(senderId).orElse(null);
                    UserAccount receiver = userAccountRepository.findById(receiverId).orElse(null);

                    sender.setBalance(sender.getBalance() - amount);
                    receiver.setBalance(receiver.getBalance() + amount);

                    userAccountRepository.save(sender);
                    userAccountRepository.save(receiver);

                }

                allLogs.add(newLog);

            }
            else {
                dbLog.setViewNumber(socketService.getCurrentView());
                replace(allLogs, dbLog);
            }
        }

        logRepository.saveAll(allLogs);

        log.info("Logs updated, new view = {}", socketService.getCurrentView());

        processing = false;
        serverStatusUtil.setViewChangeTransition(false);

    }

}
