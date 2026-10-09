package com.ureclive.urec_live_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} jobs (currently the help request expiry job). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
