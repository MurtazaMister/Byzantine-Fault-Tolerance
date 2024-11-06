//package com.lab.pbft.service.client;
//
//import com.lab.pbft.service.ExitService;
//import com.lab.pbft.util.PortUtil;
//import com.lab.pbft.util.ServerStatusUtil;
//import com.lab.pbft.util.Stopwatch;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.csv.CSVFormat;
//import org.apache.commons.csv.CSVParser;
//import org.apache.commons.csv.CSVRecord;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Lazy;
//import org.springframework.http.ResponseEntity;
//import org.springframework.stereotype.Service;
//
//import java.io.*;
//import java.nio.charset.StandardCharsets;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.time.Duration;
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.CompletableFuture;
//
//@Service
//@Slf4j
//public class CsvFileService {
//
//    @Value("${test.csv.file.path}")
//    String filePath;
//    @Value("${rest.server.url}")
//    private String restServerUrl;
//    @Value("${rest.server.offset}")
//    private int offset;
//
//    @Autowired
//    ApiService apiService;
//
//    @Autowired
//    ParseUtil parseUtil;
//    @Autowired
//    @Lazy
//    private ServerStatusUtil serverStatusUtil;
//    @Autowired
//    @Lazy
//    private PortUtil portUtil;
//    @Autowired
//    @Lazy
//    private ExitService exitService;
//    @Autowired
//    @Lazy
//    private ClientService clientService;
//
//    public void readAndExecuteCsvFile(BufferedReader inputReader) {
//        readAndExecuteCsvFile(filePath, inputReader);
//    }
//
//    public void readAndExecuteCsvFile(String filePath, BufferedReader inputReader) {
//        Path path = Paths.get(filePath);
//        char baseUname = 'a';
//        String url;
//        List<Integer> ports = portUtil.portPoolGenerator();
//
//        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
//             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT)){
//
//            int currentSetNumber = 0;
//            List<Integer> activeServerIds;
//            Transaction currentTransaction;
//
//            log.info("Beginning file read, enter continue");
//            char sender_name , receiver_name;
//
//            for(CSVRecord record : csvParser) {
//                Integer setNumber = tryParseSetNumber(record.get(0));
//                if(setNumber != null) {
//                    boolean exit = promptUser(inputReader);
//
//                    if(exit){
//                        log.warn("If you exit, you will lose data and might have to re-run the file again");
//                        log.warn("Transactions upto this point has been committed, if you exit, those transactions will run again and data will become inconsistent");
//                        log.warn("To reset the database, run the servers once again with \"app.developer-mode=true\" in application.properties");
//                        System.out.println("Exit? (Y/n)");
//                        String ans = inputReader.readLine();
//                        if(ans.equals("Y")){
//                            exitService.exitApplication(0);
//                        }
//                    }
//
//                    currentSetNumber = setNumber;
//                    log.warn("Beginning execution of set number: {}", currentSetNumber);
//                    currentTransaction = parseUtil.parseTransaction(record.get(1));
//                    activeServerIds = parseUtil.parseActiveServerList(record.get(2));
//
//                    serverStatusUtil.setServerStatuses(activeServerIds);
//
//                }
//                else {
//                    currentTransaction = parseUtil.parseTransaction(record.get(1));
//                }
//
//                url = restServerUrl+":"+(ports.get(0) - 1 + offset + currentTransaction.getSenderId())+"/transaction";
//
//                sender_name = (char)(baseUname + currentTransaction.getSenderId() - 1);
//                receiver_name = (char)(baseUname + currentTransaction.getReceiverId() - 1);
//
//                LocalDateTime startTime = LocalDateTime.now();
//
//                CompletableFuture<Transaction> futureTransaction = apiService.asyncTransact(Character.toString(sender_name),
//                        Character.toString(receiver_name),
//                        currentTransaction.getAmount(),
//                        url);
//
//                log.info("Set {}: Executing transaction - {}:{}->{}", currentSetNumber, currentTransaction.getAmount(), sender_name, receiver_name);
//
//                futureTransaction.thenAccept(transaction -> {
//                    LocalDateTime currentTime = LocalDateTime.now();
//                    if(transaction!=null) {
//                        clientService.setCountOfTransactionsExecuted(clientService.getCountOfTransactionsExecuted()+1);
//                        clientService.setTotalTimeInProcessingTransactions(clientService.getTotalTimeInProcessingTransactions()+(Duration.between(startTime, currentTime).toNanos()/1000000000.0));
//                        log.info("{}", Stopwatch.getDuration(startTime, currentTime, "Transaction $"+transaction.getAmount()+" : "+transaction.getSenderId()+" -> "+transaction.getReceiverId()+" executed in"));
//                    }
//                    else
//                        log.error("Transaction failed");
//                });
//
//            }
//
//            do {
//                boolean exit = promptUser(inputReader);
//                if(exit){
//                    return;
//                }
//            } while(true);
//        }
//        catch (IOException e) {
//            log.error("Error reading csv file: {}", e.getMessage());
//        }
//        catch (Exception e) {
//            log.error("Error: {}", e.getMessage());
//        }
//    }
//
//    public boolean promptUser(BufferedReader reader) {
//        boolean cont = false, exit = false;
//        try {
//            String input;
//            while(true){
//                if(cont || exit) break;
//                System.out.println("Enter commands for user "+clientService.getUsername());
//                System.out.println("""
//                        - printBalance <username (optional)> # Gets the balance of the client = {username} from its own server
//                        - printLog <username (optional)> # Gets the logs of the server handling client = {username}
//                        - printDB <username (optional)> # Gets the db at the server handling client = {username}
//                        - performance
//                        - continue | c
//                        - exit | e
//                        """);
//                input = reader.readLine();
//
//                if(input != null){
//                    String[] parts = input.split(" ");
//
//                    switch(parts[0]){
//                        case "printBalance":
//                            if(parts.length == 1){
//                                System.out.println("Balance: $"+apiService.balanceCheck(clientService.getUserId()));
//                            }
//                            else if(parts.length == 2){
//                                System.out.println("Balance: $"+apiService.balanceCheck(parts[1]));
//                            }
//                            else{
//                                log.warn("Invalid command or username");
//                            }
//                            break;
//                        case "printLog":
//                            List<Transaction> transactions;
//                            if(parts.length == 1){
//                                transactions = apiService.printLocalLog(clientService.getUsername());
//                            }
//                            else if(parts.length == 2){
//                                transactions = apiService.printLocalLog(parts[1]);
//                            }
//                            else{
//                                log.warn("Invalid command or username");
//                                break;
//                            }
//
//                            if(!transactions.isEmpty()){
//                                printTransactions(transactions);
//                            } else {
//                                log.info("Empty local log");
//                            }
//
//                            break;
//                        case "printDB":
//
//                            List<TransactionBlock> transactionBlocks;
//                            if(parts.length == 1){
//                                transactionBlocks = apiService.printDB(clientService.getUsername());
//                            }
//                            else if(parts.length == 2){
//                                transactionBlocks = apiService.printDB(parts[1]);
//                            }
//                            else{
//                                log.warn("Invalid command or username");
//                                break;
//                            }
//
//                            if(!transactionBlocks.isEmpty()){
//                                for(TransactionBlock transactionBlock : transactionBlocks){
//                                    System.out.println("Block id: "+ transactionBlock.getIdx());
//                                    printTransactions(transactionBlock.getTransactions());
//                                }
//                            } else {
//                                log.info("Empty DB");
//                            }
//
//                            break;
//
//                        case "performance":
//
//                            System.out.println("Average latency: "+Math.round((100.0*clientService.getTotalTimeInProcessingTransactions())/clientService.getCountOfTransactionsExecuted())/100.0+" seconds per transaction");
//                            System.out.println("Average throughput: "+Math.round((100.0*clientService.getCountOfTransactionsExecuted())/clientService.getTotalTimeInProcessingTransactions())/100.0+" transactions per second");
//
//                            break;
//                        case "continue":
//                            cont = true;
//                            break;
//                        case "c":
//                            cont = true;
//                            break;
//                        case "exit":
//                            exit = true;
//                            break;
//                        case "e":
//                            exit = true;
//                            break;
//                        default:
//                            log.warn("Unknown command: {}", parts[0]);
//                            break;
//                    }
//                }
//            }
//        }
//        catch(Exception e){
//            log.trace("Error reading input: {}", e.getMessage());
//        }
//        return exit;
//    }
//
//    public Integer tryParseSetNumber(String value){
//        try{
//            return Integer.parseInt(value);
//        }
//        catch (NumberFormatException e){
//            return null;
//        }
//    }
//
//    public void printTransactions(List<Transaction> transactions){
//        for(Transaction transaction : transactions){
//            System.out.printf("$%d : %d -> %d\n", transaction.getAmount(), transaction.getSenderId(), transaction.getReceiverId());
//        }
//    }
//}
