package com.lab.pbft.util;

import com.lab.pbft.networkObjects.communique.Message;
import com.lab.pbft.wrapper.AckMessageWrapper;
import com.lab.pbft.wrapper.MessageWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Component
@Slf4j
public class CommandUtil {

    @Autowired
    SocketMessageUtil socketMessageUtil;
    @Autowired
    private ServerStatusUtil serverStatusUtil;

    public void listenForCommands(long assignedPort) {
        try {
//            if(serverStatusUtil.isFailed()){
//                log.error("Rejecting incoming command, current server down");
//                return;
//            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String input;
            while (true) {
                System.out.printf("""
                        Enter commands for server %d: 
                        b (broadcast) <message>
                        s (send) <port> <message>
                        """, assignedPort);
                input = reader.readLine();

                if (input != null) {
                    String[] parts = input.split(" ");

                    switch (parts[0]) {
                        case "b":
                            try{
                                Message message = Message.builder()
                                        .message(parts[1])
                                        .build();
                                MessageWrapper messageWrapper = MessageWrapper.builder()
                                        .type(MessageWrapper.MessageType.MESSAGE)
                                        .message(message)
                                        .fromPort(assignedPort)
                                        .build();
                                try{
                                    List<AckMessageWrapper> ackMessageWrapperList = socketMessageUtil.broadcast(messageWrapper).get();
                                    log.info("Received acknowledgements from {} servers", ackMessageWrapperList.size());
                                }
                                catch(InterruptedException | ExecutionException e){
                                    log.error("Error while broadcasting messages: {}", e.getMessage());
                                }
                            }
                            catch(IOException e){
                                log.error("IOException {}", e.getMessage());
                            }
                            catch (Exception e){
                                log.error(e.getMessage());
                            }
                            break;
                        case "s":
                            int targetPort = -1;
                            try {
                                targetPort = Integer.parseInt(parts[1]);
                                Message message = Message.builder()
                                        .message(parts[2])
                                        .build();

                                MessageWrapper messageWrapper = MessageWrapper.builder()
                                        .type(MessageWrapper.MessageType.MESSAGE)
                                        .message(message)
                                        .fromPort(assignedPort)
                                        .toPort(targetPort)
                                        .build();

                                socketMessageUtil.sendMessageToServer(targetPort, messageWrapper);

                            } catch (NumberFormatException e) {
                                log.warn("Invalid port number: {}", targetPort);
                            }
                            catch (IOException e){
                                log.error("Server unavailable");
                            } catch (Exception e) {
                                log.error(e.getMessage());
                            }
                            break;
                        default:
                            log.warn("Unknown command: {}", parts[0]);
                            break;
                    }
                }
            }
        } catch (Exception e) {
            log.trace("Error reading input: {}", e.getMessage());
        }
    }
}
