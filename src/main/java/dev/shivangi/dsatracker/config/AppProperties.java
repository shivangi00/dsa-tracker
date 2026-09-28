package dev.shivangi.dsatracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The "app:" block in application.yml.
 *
 * @param zone           time zone that decides what "today" is
 * @param planDays       length of the plan shown on the heatmap (from each user's start date)
 * @param targetProblems 150 for the NeetCode list
 * @param lastNewProblemDay the last plan day for marking new problems done (85 of 100), so every
 *                       problem's three revisions fit before the plan ends
 * @param weeklyTargetDays study days per plan week to aim for (5 = two rest days built in)
 * @param rateLimitsEnabled limit sign-in / sign-up / recovery attempts (off only in tests)
 * @param anthropicApiKey optional: when set, "Analyse" asks Claude instead of the built-in estimate
 * @param analysisModel  the Claude model used for analysis
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String zone,
        int planDays,
        int targetProblems,
        int lastNewProblemDay,
        int weeklyTargetDays,
        boolean rateLimitsEnabled,
        String anthropicApiKey,
        String analysisModel) {
}
