package com.ureclive.urec_live_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/** Every minute, closes help requests that nobody has updated for 30 minutes (both configurable). */
@Component
public class HelpRequestExpiryJob {

    private static final Logger logger = LoggerFactory.getLogger(HelpRequestExpiryJob.class);

    private final AdminHelpRequestService adminHelpRequestService;
    private final Duration expireAfter;

    public HelpRequestExpiryJob(AdminHelpRequestService adminHelpRequestService,
                                @Value("${app.help-requests.expire-after-minutes:30}") long expireAfterMinutes) {
        this.adminHelpRequestService = adminHelpRequestService;
        this.expireAfter = Duration.ofMinutes(expireAfterMinutes);
    }

    @Scheduled(fixedDelayString = "${app.help-requests.expiry-check-ms:60000}",
               initialDelayString = "${app.help-requests.expiry-check-ms:60000}")
    public void expireIdleRequests() {
        int expired = adminHelpRequestService.expireIdleBefore(Instant.now().minus(expireAfter));
        if (expired > 0) {
            logger.info("Expired {} idle help request(s)", expired);
        }
    }
}
