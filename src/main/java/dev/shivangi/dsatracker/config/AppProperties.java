package dev.shivangi.dsatracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The "app:" block in application.yml.
 *
 * @param zone           time zone that decides what "today" is
 * @param planDays       length of the plan shown on the heatmap (from each user's start date)
 * @param targetProblems 150 for the NeetCode list
 * @param baseUrl        where the app is reachable, used to build password-reset links
 * @param mailFrom       sender address for emails
 * @param weeklyTargetDays study days per plan week to aim for (5 = two rest days built in)
 * @param rateLimitsEnabled limit sign-in / sign-up / reset attempts per IP (off only in tests)
 * @param logResetLinks  write reset links to the log if email fails (handy locally; off in production)
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String zone,
        int planDays,
        int targetProblems,
        String baseUrl,
        String mailFrom,
        int weeklyTargetDays,
        boolean rateLimitsEnabled,
        boolean logResetLinks) {
}
