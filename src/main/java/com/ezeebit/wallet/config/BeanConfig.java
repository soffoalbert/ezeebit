package com.ezeebit.wallet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Clock;

@Configuration
@EnableAsync
class BeanConfig {

    /** A single Clock so time-dependent logic (quote TTLs, timestamps) is testable. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Drives the stubbed payout rail's simulated asynchronous settlement callbacks. */
    @Bean
    TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("payout-rail-");
        scheduler.initialize();
        return scheduler;
    }
}
