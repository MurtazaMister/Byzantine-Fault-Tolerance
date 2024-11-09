package com.lab.pbft.service.client;

import com.lab.pbft.model.primary.Log;
import com.lab.pbft.networkObjects.communique.Request;
import com.lab.pbft.service.DatabaseResetService;
import com.lab.pbft.service.ExitService;
import com.lab.pbft.util.ParseUtil;
import com.lab.pbft.util.PortUtil;
import com.lab.pbft.util.ServerStatusUtil;
import com.lab.pbft.util.Stopwatch;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class CsvFileService {

    @Value("${test.csv.file.path}")
    String filePath;
    @Value("${rest.server.url}")
    private String restServerUrl;
    @Value("${rest.server.offset}")
    private int offset;

    @Autowired
    ApiService apiService;

    @Autowired
    ParseUtil parseUtil;
    @Autowired
    @Lazy
    private ServerStatusUtil serverStatusUtil;
    @Autowired
    @Lazy
    private PortUtil portUtil;
    @Autowired
    @Lazy
    private ExitService exitService;
    @Autowired
    @Lazy
    private ClientService clientService;
    @Autowired
    @Lazy
    private PerformTransactionService performTransactionService;
    @Autowired
    private DatabaseResetService databaseResetService;

    public void readAndExecuteCsvFile(BufferedReader inputReader) {
        readAndExecuteCsvFile(filePath, inputReader);
    }

    public void readAndExecuteCsvFile(String filePath, BufferedReader inputReader) {
        Path path = Paths.get(filePath);
        char baseUname = 'A';
        String url;
        List<Integer> ports = portUtil.portPoolGenerator();

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT)){

            int currentSetNumber = 0;
            List<Integer> activeServerIds;
            List<Integer> byzantineServerIds;
            Request request;

            log.info("Beginning file read, enter continue");
            char sender_name , receiver_name;

            for(CSVRecord record : csvParser) {
                Integer setNumber = tryParseSetNumber(record.get(0));
                if(setNumber != null) {
                    databaseResetService.init();
                    boolean exit = promptUser(inputReader);

                    if(exit){
                        log.warn("If you exit, you will lose data and might have to re-run the file again");
                        log.warn("Transactions upto this point has been committed, if you exit, those transactions will run again and data will become inconsistent");
                        log.warn("To reset the database, run the servers once again with \"app.developer-mode=true\" in application.properties");
                        System.out.println("Exit? (Y/n)");
                        String ans = inputReader.readLine();
                        if(ans.equals("Y")){
                            exitService.exitApplication(0);
                        }
                    }

                    currentSetNumber = setNumber;
                    log.warn("Beginning execution of set number: {}", currentSetNumber);
                    request = parseUtil.parseTransaction(record.get(1));
                    activeServerIds = parseUtil.parseActiveServerList(record.get(2));
                    byzantineServerIds = parseUtil.parseActiveServerList(record.get(3));

                    serverStatusUtil.setServerStatuses(activeServerIds, byzantineServerIds);

                }
                else {
                    request = parseUtil.parseTransaction(record.get(1));
                }

                performTransactionService.performTransaction(request);

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

            }

            do {
                boolean exit = promptUser(inputReader);
                if(exit){
                    return;
                }
            } while(true);
        }
        catch (IOException e) {
            log.error("Error reading csv file: {}", e.getMessage());
        }
        catch (Exception e) {
            log.error("Error: {}", e.getMessage());
        }
    }

    public boolean promptUser(BufferedReader reader) {
        boolean cont = false, exit = false;
        try {
            String input;
            while(true){
                if(cont || exit) break;
                System.out.println("Enter commands for user "+clientService.getUsername());
                System.out.println("""
                        - printLog <server_id>(S1/S2/..) # Prints the log of server, request and its metadata
                        - printDB # Balances of all users across all servers
                        - printStatus <sequence_no>(1/2/..) # Prints the status of log across all servers
                        - printView # Prints all the new_view messages exchanged
                        - performance
                        - continue | c
                        - exit | e
                        """);

                input = reader.readLine();

                if(input != null){
                    String[] parts = input.split(" ");

                    switch(parts[0]){

                        case "printLog":
                            if(parts.length == 1) printLog(null);
                            else if (parts.length == 2) {
                                printLog(parts[1]);
                            }
                            break;

                        case "printDB":
                            printDB();
                            break;

                        case "printStatus":
                            if(parts.length==2) printStatus(Integer.parseInt(parts[1]));
                            break;

                        case "continue":
                            cont = true;
                            break;

                        case "c":
                            cont = true;
                            break;

                        case "exit":
                            exit = true;
                            break;

                        case "e":
                            exit = true;
                            break;

                        default:
                            log.warn("Unknown command: {}", parts[0]);
                            break;
                    }
                }
            }
        }
        catch(Exception e){
            log.trace("Error reading input: {}", e.getMessage());
        }
        return exit;
    }

    public Integer tryParseSetNumber(String value){
        try{
            return Integer.parseInt(value);
        }
        catch (NumberFormatException e){
            return null;
        }
    }

    public void printLog(String port){
        List<Log> logs = (port!=null)?apiService.getLogs(portUtil.basePort()+Integer.parseInt(Character.toString(port.charAt(1)))-1+offset):apiService.getLogs(null);

        for(Log log : logs){
            System.out.printf("""
                    Sequence number: %d
                    View number: %d
                    Status: %s
                    Approved: %s
                    Transaction: $%d : %d -> %d
                    \n""", log.getSequenceNumber(),
                    log.getViewNumber(), log.getType()
            , log.isApproved(), log.getPrePrepare().getRequest().getAmount(),
                    log.getPrePrepare().getRequest().getClientId(),
                    log.getPrePrepare().getRequest().getReceiverId());
        }

    }

    public void printDB(){
        List<List<Long>> listBalances = apiService.getAllBalances();

        StringBuilder sb = new StringBuilder("");

        sb.append("    |");
        char c = 'A';
        for(int i = 0;i<10;i++){
            sb.append("  " + (c++) + " |");
        }
        sb.append("\n");

        for(int i = 1;i<=7;i++){
            sb.append(" S"+i+" |");

            for(Long balance : listBalances.get(i-1)){
                sb.append(String.format(" %2d |", balance));
            }
            sb.append("\n");
        }
        System.out.println(sb.toString());
    }

    public void printStatus(long sequenceNumber){
        List<String> statuses = apiService.getStatus(sequenceNumber);

        StringBuilder sb = new StringBuilder("");
        sb.append(" Server |");
        for(int i = 1;i<=7;i++){
            sb.append(" S" + i + " |");
        }
        sb.append("\n");
        sb.append(" Status |");
        for(String status : statuses){
            sb.append(" " + status + " |");
        }
        sb.append("\n");

        System.out.println(sb.toString());

    }
}
