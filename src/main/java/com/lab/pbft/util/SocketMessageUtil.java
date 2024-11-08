package com.lab.pbft.util;

import com.lab.pbft.config.KeyConfig;
import com.lab.pbft.config.SocketConfig;
import com.lab.pbft.controller.UserAccountController;
import com.lab.pbft.networkObjects.acknowledgements.AckMessage;
import com.lab.pbft.networkObjects.acknowledgements.AckServerStatusUpdate;
import com.lab.pbft.networkObjects.acknowledgements.ClientReply;
import com.lab.pbft.networkObjects.acknowledgements.Reply;
import com.lab.pbft.networkObjects.communique.Message;
import com.lab.pbft.networkObjects.communique.Request;
import com.lab.pbft.networkObjects.communique.ServerStatusUpdate;
import com.lab.pbft.service.PbftService;
import com.lab.pbft.service.SocketService;
import com.lab.pbft.wrapper.AckMessageWrapper;
import com.lab.pbft.wrapper.MessageWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
@Slf4j
public class SocketMessageUtil {

    @Autowired
    @Lazy
    AckDisplayUtil ackDisplayUtil;

    @Autowired
    ServerStatusUtil serverStatusUtil;

    @Autowired
    PortUtil portUtil;

    @Autowired
    SocketConfig socketConfig;

    @Autowired
    @Lazy
    SocketService socketService;

    @Autowired
    @Lazy
    private KeyConfig keyConfig;
    @Autowired
    @Lazy
    private PbftService pbftService;
    @Autowired
    @Lazy
    private UserAccountController userAccountController;

    public AckMessageWrapper sendMessageToServer(int targetPort, MessageWrapper message, int connectionTimeout, int readTimeout) throws IOException {
        //        if(serverStatusUtil.isFailed()){
//            throw new IOException("Server Unavailable");
//        }

        AckMessageWrapper ackMessageWrapper = null;
        try(Socket socket = new Socket()){

            // setting connection timeout
            socket.connect(new InetSocketAddress("localhost", targetPort), connectionTimeout);
            // setting timeout for receiving ack
            socket.setSoTimeout(readTimeout);

            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            // Signing message before sending

            message.signMessage(keyConfig.getPrivateKey());

            out.writeObject(message);
            out.flush();

            try{
                // Receiving acknowledgement from the respective server
                ackMessageWrapper = (AckMessageWrapper) in.readObject();
                ackDisplayUtil.displayAcknowledgement(ackMessageWrapper);
            } catch (SocketTimeoutException e) {
                log.error("Timeout after {} ms waiting for port {}", socketConfig.getReadTimeout(), targetPort);
            } catch (EOFException e) {
                log.error("Connection closed by the server without sending ack for port {}: {}", targetPort, e.toString());
            } catch (IOException e) {
                log.error("IO Error receiving ack message from port {}: {}", targetPort, e.toString());
            } catch (Exception e) {
                log.error("Unexpected error receiving ack message from port {}: {}", targetPort, e.toString());
            }

        }
        catch (IOException e){
            log.error("Failed to send message to port {}: {}", targetPort, e.getMessage());
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
        }
        if(ackMessageWrapper == null) throw new IOException("Service Unavailable");
        return ackMessageWrapper;
    }

    public AckMessageWrapper sendMessageToServer(int targetPort, MessageWrapper message) throws IOException {
        return sendMessageToServer(targetPort, message, socketConfig.getConnectionTimeout(), socketConfig.getReadTimeout());
    }

    public CompletableFuture<List<AckMessageWrapper>> broadcast(MessageWrapper messageWrapper, List<Integer> PORT_POOL) throws IOException{
//        if(serverStatusUtil.isFailed()){
//            return CompletableFuture.failedFuture(new IOException("Server Unavailable"));
//        }

        long assignedPort = socketService.getAssignedPort();

        List<CompletableFuture<AckMessageWrapper>> futures = PORT_POOL.stream()
                .filter(port -> port != assignedPort)
                .map(port -> CompletableFuture.supplyAsync(() -> {
                    try{
                        MessageWrapper smw = MessageWrapper.from(messageWrapper);
                        smw.setToPort(port);
                        return sendMessageToServer(port, smw);
                    }
                    catch(IOException e){
                        log.error("IOException {}: {}", port, e.getMessage());
                        return null;
                    }
                    catch(Exception e){
                        log.error("Failed to send message to port {}: {}", port, e.getMessage());
                        return null;
                    }
                }))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(ack -> ack!=null)
                        .collect(Collectors.toList()));


    }

    public CompletableFuture<List<AckMessageWrapper>> broadcast(MessageWrapper messageWrapper) throws IOException {
        List<Integer> PORT_POOL = portUtil.portPoolGenerator();
        return broadcast(messageWrapper, PORT_POOL);
    }

    public void listenForIncomingMessages(@NotNull ServerSocket serverSocket) {
        try {
            while (true) {
                Socket incoming = serverSocket.accept();
                new Thread(() -> handleIncomingMessage(incoming)).start();
            }
        } catch (IOException e) {
            log.trace("Error while listening for incoming messages\n{}", e.getMessage());
        }
    }

    private void handleIncomingMessage(@NotNull Socket incoming) {
        try {

            ObjectOutputStream out = new ObjectOutputStream(incoming.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(incoming.getInputStream());

            MessageWrapper message;
            while ((message = (MessageWrapper) in.readObject()) != null) { // incoming message from another server


                if(!message.verifyMessage(keyConfig.getPublicKeyStore().get(message.getFromPort()))){
                    log.error("Invalid signature for message: {}", message);
                    return;
                }

                switch (message.getType()) {
                    case SERVER_STATUS_UPDATE:
                        ServerStatusUpdate serverStatusUpdate = message.getServerStatusUpdate();
                        if (message.getToPort() == socketService.getAssignedPort()) {
                            log.info("Received verified message from port {}: {}", message.getFromPort(), serverStatusUpdate);

                            serverStatusUtil.setFailed(serverStatusUpdate.isFailServer());

                            AckServerStatusUpdate ackServerStatusUpdate = AckServerStatusUpdate.builder()
                                    .serverFailed(serverStatusUtil.isFailed())
                                    .build();

                            AckMessageWrapper ackMessageWrapper = AckMessageWrapper.builder()
                                    .type(AckMessageWrapper.MessageType.ACK_SERVER_STATUS_UPDATE)
                                    .ackServerStatusUpdate(ackServerStatusUpdate)
                                    .fromPort(socketService.getAssignedPort())
                                    .toPort(message.getFromPort())
                                    .build();

                            ackMessageWrapper.signMessage(keyConfig.getPrivateKey());

                            out.writeObject(ackMessageWrapper);

                            log.info("Sent signed ACK to server {}: {}", ackMessageWrapper.getToPort(), ackMessageWrapper.getAckServerStatusUpdate());

                            out.flush();
                        } else {
                            log.info("Target port does not match port of current server");

                            AckServerStatusUpdate ackServerStatusUpdate = AckServerStatusUpdate.builder()
                                    .serverFailed(serverStatusUtil.isFailed())
                                    .build();

                            AckMessageWrapper ackMessageWrapper = AckMessageWrapper.builder()
                                    .type(AckMessageWrapper.MessageType.ACK_SERVER_STATUS_UPDATE)
                                    .ackServerStatusUpdate(ackServerStatusUpdate)
                                    .fromPort(socketService.getAssignedPort())
                                    .toPort(message.getFromPort())
                                    .build();

                            ackMessageWrapper.signMessage(keyConfig.getPrivateKey());

                            out.writeObject(ackMessageWrapper);

                            log.info("Sent signed ACK to server {}: {}", ackMessageWrapper.getToPort(), ackMessageWrapper.getAckServerStatusUpdate());

                            out.flush();
                        }
                        break;
                    case MESSAGE:
                    {
                        Message mess = message.getMessage();
                        log.info("Received verified message from port {}: {}", message.getFromPort(), mess);

                        AckMessage ackMessage = AckMessage.builder()
                                .message(mess.getMessage())
                                .build();

                        AckMessageWrapper ackMessageWrapper = AckMessageWrapper.builder()
                                .type(AckMessageWrapper.MessageType.ACK_MESSAGE)
                                .ackMessage(ackMessage)
                                .fromPort(message.getToPort())
                                .toPort(message.getFromPort())
                                .build();

                        ackMessageWrapper.signMessage(keyConfig.getPrivateKey());

                        out.writeObject(ackMessageWrapper);

                        log.info("Sent signed ACK to server {}: {}", ackMessageWrapper.getToPort(), ackMessage);

                        out.flush();
                    }
                    break;
                    case REQUEST: {
                        if(serverStatusUtil.isFailed() || serverStatusUtil.isViewChangeTransition()) return;

                        Request request = message.getRequest();
                        log.info("Received verified request from port {}: {}", message.getFromPort(), request);
                        if(!request.verifyMessage(keyConfig.getPublicKeyStore().get(request.getClientId()))){
                            log.error("Request cannot be verified, rejecting: {}", request);

                            AckMessageWrapper ackMessageWrapper = AckMessageWrapper.builder()
                                    .type(AckMessageWrapper.MessageType.CLIENT_REPLY)
                                    .clientReply(null)
                                    .fromPort(message.getToPort())
                                    .toPort(message.getFromPort())
                                    .build();

                            ackMessageWrapper.signMessage(keyConfig.getPrivateKey());

                            out.writeObject(ackMessageWrapper);
                        }

                        // IF I'M THE LEADER
                        // RUN PBFT
                        // ELSE FORWARD
                        // TO LEADER

                        if(request.getCurrentView() != socketService.getCurrentView()) {

                            log.error("Views don't match: current {}, request {}", socketService.getCurrentView(), request.getCurrentView());

//                          ClientReply clientReply = ClientReply.builder()
//                                    .currentView(socketService.getCurrentView())
//                                    .timestamp(request.getTimestamp())
//                                    .requestDigest(request.getHash())
//                                    .approved(false)
//                                    .finalBalance(-1)
//                                    .build();

                            AckMessageWrapper ackMessageWrapper = AckMessageWrapper.builder()
                                    .type(AckMessageWrapper.MessageType.CLIENT_REPLY)
                                    .clientReply(null)
                                    .fromPort(message.getToPort())
                                    .toPort(message.getFromPort())
                                    .build();

                            ackMessageWrapper.signMessage(keyConfig.getPrivateKey());

                            out.writeObject(ackMessageWrapper);

                        }

                        else if(socketService.getAssignedPort() == socketService.getLeader(request.getCurrentView())) {
                            ClientReply clientReply = userAccountController.transact(request).getBody();

                            AckMessageWrapper ackMessageWrapper = AckMessageWrapper.builder()
                                    .type(AckMessageWrapper.MessageType.CLIENT_REPLY)
                                    .clientReply(clientReply)
                                    .fromPort(message.getToPort())
                                    .toPort(message.getFromPort())
                                    .build();

                            ackMessageWrapper.signMessage(keyConfig.getPrivateKey());

                            out.writeObject(ackMessageWrapper);

                            log.info("Sent signed clientReply to server {}", ackMessageWrapper.getToPort());

                            out.flush();
                        }
                        else {
                            log.error("Request: {} forwarded to wrong leader, rejecting", request);

                            AckMessageWrapper ackMessageWrapper = AckMessageWrapper.builder()
                                    .type(AckMessageWrapper.MessageType.CLIENT_REPLY)
                                    .clientReply(null)
                                    .fromPort(message.getToPort())
                                    .toPort(message.getFromPort())
                                    .build();

                            ackMessageWrapper.signMessage(keyConfig.getPrivateKey());

                            out.writeObject(ackMessageWrapper);
                        }

                    }
                    break;
                    case PRE_PREPARE:{
                        if(serverStatusUtil.isFailed() || serverStatusUtil.isViewChangeTransition()) return;
                        log.info("Received verified pre-prepare from port {}: {}", message.getFromPort(), message.getPrePrepare());
                        pbftService.prepare(in, out, message);
                    }
                    break;
                    case COMMIT:{
                        if(serverStatusUtil.isFailed() || serverStatusUtil.isViewChangeTransition()) return;
                        log.info("Received verified commit message from port {}: {}", message.getFromPort(), message.getCommit());
                        pbftService.ackCommit(in, out, message);
                    }
                    break;
                    case EXECUTE:{
                        if(serverStatusUtil.isFailed() || serverStatusUtil.isViewChangeTransition()) return;
                        log.info("Received verified execute message from port {}: {}", message.getFromPort(), message.getExecute());
                        pbftService.ackExecute(in, out, message);
                    }
                    break;
                    case VIEW_CHANGE: {
                        log.info("Received verified view_change message from port {}: {}", message.getFromPort(), message.getViewChange());
                        if(serverStatusUtil.isFailed()) return;

                        pbftService.newView(in, out, message);

                    }
                    break;
                    case NEW_VIEW: {
                        log.info("Received verified new_view message from port {}: {}", message.getFromPort(), message.getNewView());
                        if(serverStatusUtil.isFailed()) return;

                        AckMessage ackMessage = AckMessage.builder()
                                .message("Received NEW_VIEW")
                                .build();

                        AckMessageWrapper ackMessageWrapper = AckMessageWrapper.builder()
                                .type(AckMessageWrapper.MessageType.ACK_MESSAGE)
                                .ackMessage(ackMessage)
                                .fromPort(message.getToPort())
                                .toPort(message.getFromPort())
                                .build();

                        ackMessageWrapper.signMessage(keyConfig.getPrivateKey());

                        out.writeObject(ackMessageWrapper);

                        log.info("Sent signed ACK to server {}: {}", ackMessageWrapper.getToPort(), ackMessage);

                        out.flush();

                        pbftService.newViewProcess(message.getNewView());
                    }
                    break;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            // log.error("IOException | ClassNotFoundException: {}", e.toString());
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
        } finally {
            try {
                incoming.close();
            } catch (IOException e) {
                log.error("IOException: {}", e.getMessage());
            }
        }
    }
}
