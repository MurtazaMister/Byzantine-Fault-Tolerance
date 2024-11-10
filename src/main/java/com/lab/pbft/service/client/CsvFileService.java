package com.lab.pbft.service.client;

import com.lab.pbft.config.KeyConfig;
import com.lab.pbft.config.client.ApiConfig;
import com.lab.pbft.model.primary.Log;
import com.lab.pbft.model.primary.NewView;
import com.lab.pbft.networkObjects.communique.Request;
import com.lab.pbft.repository.secondary.EncryptionKeyRepository;
import com.lab.pbft.service.DatabaseResetService;
import com.lab.pbft.service.ExitService;
import com.lab.pbft.util.ParseUtil;
import com.lab.pbft.util.PortUtil;
import com.lab.pbft.util.ServerStatusUtil;
import com.lab.pbft.util.Stopwatch;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class CsvFileService {

    @Value("${test.csv.file.path}")
    String filePath;
    @Value("${rest.server.url}")
    private String restServerUrl;
    @Value("${rest.server.offset}")
    private int offset;

    private final Map<Long, PrivateKey> clientPrivateKeyStore = new HashMap<>();
    @Autowired
    @Lazy
    private EncryptionKeyRepository encryptionKeyRepository;
    @Autowired
    @Lazy
    private ApiConfig apiConfig;

    @PostConstruct
    public void init() {
        // Getting private keys for all clients id = 1 to 10
        for(long i = 1;i<=10;i++){
            try {
                clientPrivateKeyStore.put(i, KeyConfig.extractPrivateKey(encryptionKeyRepository.findById(i).getPrivateKey()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

    }

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

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT)){

            int currentSetNumber = 0;
            List<Integer> activeServerIds;
            List<Integer> byzantineServerIds;

            log.info("Beginning file read, enter continue");

            for(CSVRecord record : csvParser) {
                Integer setNumber = tryParseSetNumber(record.get(0));
                if(setNumber != null) {
                    boolean exit = promptUser(inputReader);
                    log.info("Resetting tables");
                    apiService.resetDatabases();
                    apiConfig.setCurrentView(1);

                    if(exit){
                        System.out.println("Exit? (Y/n)");
                        String ans = inputReader.readLine();
                        if(ans.equals("Y")){
                            exitService.exitApplication(0);
                        }
                    }

                    currentSetNumber = setNumber;
                    log.warn("Beginning execution of set number: {}", currentSetNumber);
                    activeServerIds = parseUtil.parseActiveServerList(record.get(2));
                    byzantineServerIds = parseUtil.parseActiveServerList(record.get(3));

                    serverStatusUtil.setServerStatuses(activeServerIds, byzantineServerIds);

                }
                Request request = parseUtil.parseTransaction(record.get(1), clientPrivateKeyStore);

                System.out.printf("Sending request: $%d : %c -> %c\n", request.getAmount(), (int)(request.getClientId()+'A'-1), (int)(request.getReceiverId()+'A'-1));
                LocalDateTime startTime = LocalDateTime.now();

                performTransactionService.performTransaction(request);

                LocalDateTime currentTime = LocalDateTime.now();
                clientService.setCountOfTransactionsExecuted(clientService.getCountOfTransactionsExecuted()+1);
                clientService.setTotalTimeInProcessingTransactions(clientService.getTotalTimeInProcessingTransactions()+(Duration.between(startTime, currentTime).toNanos()/1000000000.0));
                log.info("{}", Stopwatch.getDuration(startTime, currentTime, "Request $"+request.getAmount()+" : "+(char)(request.getClientId()+(int)'A'-1)+" -> "+(char)(request.getReceiverId()+(int)'A'-1)+" executed in"));

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
                try {

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

                        case "printView":
                            printView();
                            break;

                        case "performance":

                            System.out.println("Average latency: "+Math.round((100.0*clientService.getTotalTimeInProcessingTransactions())/clientService.getCountOfTransactionsExecuted())/100.0+" seconds per transaction");
                            System.out.println("Average throughput: "+Math.round((100.0*clientService.getCountOfTransactionsExecuted())/clientService.getTotalTimeInProcessingTransactions())/100.0+" transactions per second");

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
                catch (Exception e) {
                    log.error("Error: {}", e.getMessage());
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
                    Transaction: $%d : %c -> %c
                    \n""", log.getSequenceNumber(),
                    log.getViewNumber(), log.getType()
            , log.isApproved(), log.getPrePrepare().getRequest().getAmount(),
                    ((int)log.getPrePrepare().getRequest().getClientId()+'A'-1),
                    ((int)log.getPrePrepare().getRequest().getReceiverId())+'A'-1);
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
        System.out.println(sb);
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
            sb.append(" " + getTypeString(status) + " |");
        }
        sb.append("\n");

        System.out.println(sb);

    }

    public String getTypeString(String status){
        return switch (status) {
            case "PRE_PREPARE" -> "PP";
            case "PREPARED" -> " P";
            case "COMMITED" -> " C";
            case "EXECUTED" -> " E";
            default -> "XX";
        };
    }

    public void printView(){
        List<NewView> newViewList = apiService.getNewViews();

        StringBuilder sb = new StringBuilder("\n");

        for(NewView newView : newViewList){

            sb.append(String.format("NEW_VIEW message for view: %d\n\n", newView.getView()));

            sb.append("Received logs from other servers:\n\n");

            for(NewView.Bundle bundle : newView.getBundles()){
                sb.append("\tPre-Prepare for:\n");
                sb.append(String.format("\t\tSequence number: %d\n", bundle.getSequenceNumber()));
                sb.append(String.format("\t\tView number: %d\n", bundle.getPrePrepare().getCurrentView()));
                sb.append(String.format("\t\tRequest digest: %s\n", bundle.getPrePrepare().getRequestDigest()));
                sb.append(String.format("\t\tApproved: %s\n", bundle.getApproved()));
                sb.append("\t\tSignatures on Prepared:\n");

                for(long id : bundle.getSignatures().keySet()){
                    sb.append(String.format("\t\t\t%d: %s\n", id, bundle.getSignatures().get(id)));
                }
                sb.append("\n");
            }

            sb.append("Signatures on VIEW_CHANGE:\n");

            for(long id : newView.getSignatures().keySet()){
                sb.append(String.format("\t%d: %s\n", id, newView.getSignatures().get(id)));
            }
            sb.append("\n");
        }
        System.out.println(sb);
    }
}
