package com.amerritt.tradejournal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes refresh tokens that are past their expiry.
 *
 * <p>Purely housekeeping — an expired row already grants nothing, since {@code rotate} checks
 * the expiry. Without this the table grows without bound at one row per login and per refresh.
 */
@Component
public class RefreshTokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

    private final RefreshTokenService refreshTokenService;

    public RefreshTokenCleanupJob(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @Scheduled(cron = "${tradejournal.jwt.refresh-cleanup-cron:0 30 3 * * *}")
    public void purgeExpired() {
        int deleted = refreshTokenService.purgeExpired();
        if (deleted > 0) {
            log.info("Purged {} expired refresh token(s)", deleted);
        }
    }
}
