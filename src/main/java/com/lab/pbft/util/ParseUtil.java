package com.lab.pbft.util;

import com.lab.pbft.config.KeyConfig;
import com.lab.pbft.config.client.ApiConfig;
import com.lab.pbft.networkObjects.communique.Request;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class ParseUtil {

    @Autowired
    @Lazy
    private ApiConfig apiConfig;
    @Autowired
    @Lazy
    private KeyConfig keyConfig;

    public Request parseTransaction(String cellValue) {
        Pattern pattern = Pattern.compile("\\(([A-Z]), ([A-Z]), (\\d+)\\)");
        Matcher matcher = pattern.matcher(cellValue);

        if (matcher.matches()) {
            long senderId = matcher.group(1).charAt(0) - 'A' + 1;
            long receiverId = matcher.group(2).charAt(0) - 'A' + 1;
            long amount = Long.parseLong(matcher.group(3));

            Request req = Request.builder()
                    .currentView(apiConfig.getCurrentView())
                    .clientId(senderId)
                    .receiverId(receiverId)
                    .amount(amount)
                    .timestamp(LocalDateTime.now().toString())
                    .build();
            try {
                req.signMessage(keyConfig.getPrivateKey());
            } catch (Exception e) {
                log.error("Unable to sign req: {}, error: {}", req, e.getMessage());
            }
            return req;
        } else {
            throw new IllegalArgumentException("Invalid transaction format: " + cellValue);
        }
    }

    public List<Integer> parseActiveServerList(String cellValue) {
        Pattern pattern = Pattern.compile("\\[S(\\d+)(?:, S(\\d+))*\\]");
        Matcher matcher = pattern.matcher(cellValue);

        List<Integer> ids = new ArrayList<>();
        if (matcher.find()) {
            for (String part : cellValue.replaceAll("[\\[\\]S]", "").split(", ")) {
                ids.add(Integer.parseInt(part));
            }
        } else {
            throw new IllegalArgumentException("Invalid list format: " + cellValue);
        }

        return ids;
    }

}
