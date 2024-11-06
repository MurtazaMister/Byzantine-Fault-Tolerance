package com.lab.pbft.util;

import com.lab.pbft.config.KeyConfig;
import com.lab.pbft.wrapper.AckMessageWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AckDisplayUtil {

    @Autowired
    private KeyConfig keyConfig;

    public void displayAcknowledgement(AckMessageWrapper ackMessageWrapper){

        try {
            if(!ackMessageWrapper.verifyMessage(keyConfig.getPublicKeyStore().get(ackMessageWrapper.getFromPort()))){
                log.error("Invalid signature for message: {}", ackMessageWrapper);
                return;
            }

            switch (ackMessageWrapper.getType()){

                case ACK_SERVER_STATUS_UPDATE:
                    log.info("Received verified ACK from server {}: {}", ackMessageWrapper.getFromPort(), ackMessageWrapper.getAckServerStatusUpdate());
                    break;

                case ACK_MESSAGE:
                    log.info("Received verified ACK from server {}: {}", ackMessageWrapper.getFromPort(), ackMessageWrapper.getAckMessage());
                    break;

                case PREPARE:
                    log.info("Received verified PREPARE from server {}", ackMessageWrapper.getFromPort());
                    break;

                case ACK_COMMIT:
                    log.info("Received verified ACK_COMMIT from server {}", ackMessageWrapper.getFromPort());
                    break;

                case REPLY:
                    log.info("Received verified REPLY from server {}", ackMessageWrapper.getFromPort());
                    break;

                case CLIENT_REPLY:
                    log.info("Received verified CLIENT_REPLY from server {}", ackMessageWrapper.getFromPort());
                    break;

                default:
                    log.error("Received verified, but unexpected ACK from server {}", ackMessageWrapper);
            }
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
        }
    }
}
