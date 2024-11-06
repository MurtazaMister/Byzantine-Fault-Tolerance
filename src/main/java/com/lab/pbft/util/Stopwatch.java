package com.lab.pbft.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Random;

@Slf4j
@Component
public class Stopwatch {

    public static String getDuration(LocalDateTime startTime, LocalDateTime endTime, String event) {
        Duration duration = Duration.between(startTime, endTime);
        return event+" time: "+duration.toNanos()/1000000000.0 + "s";
    }

    public static void randomSleep(int startRange, int endRange) throws InterruptedException {
        Random random = new Random();
        int offset = endRange - startRange + 1;
        int sleepTime = startRange + random.nextInt(offset);
        log.info("Sleeping "+sleepTime);
        Thread.sleep(sleepTime);
    }

}