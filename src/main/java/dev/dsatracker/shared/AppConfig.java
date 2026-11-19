package dev.dsatracker.shared;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dsatracker.dsa.analysis.ApproachRecommender;
import dev.dsatracker.dsa.analysis.BestApproaches;
import dev.dsatracker.dsa.analysis.ClaudeComplexityAnalyser;
import dev.dsatracker.dsa.analysis.ComplexityAnalyser;
import dev.dsatracker.dsa.analysis.FallbackComplexityAnalyser;
import dev.dsatracker.dsa.analysis.HeuristicComplexityAnalyser;
import dev.dsatracker.dsa.consistency.ConsistencyCalculator;
import dev.dsatracker.account.RecoveryCodes;
import dev.dsatracker.shared.repetition.SpacedRepetitionPolicy;
import dev.dsatracker.dsa.weekly.TestBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Random;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

    /**
     * Every class asks this clock for "today" instead of calling LocalDate.now(). Tests
     * can swap in Clock.fixed(...) to pretend it's any date.
     */
    @Bean
    public Clock clock(AppProperties props) {
        return Clock.system(ZoneId.of(props.zone()));
    }

    @Bean
    public SpacedRepetitionPolicy spacedRepetitionPolicy() {
        return new SpacedRepetitionPolicy();
    }

    @Bean
    public ConsistencyCalculator consistencyCalculator() {
        return new ConsistencyCalculator();
    }

    @Bean
    public RecoveryCodes recoveryCodes() {
        return new RecoveryCodes();
    }

    @Bean
    public TestBuilder testBuilder() {
        return new TestBuilder(new Random());
    }

    /**
     * "Analyse": the built-in estimate always works. With ANTHROPIC_API_KEY set, Claude answers
     * first and the estimate is the fallback if it can't be reached.
     */
    @Bean
    public ComplexityAnalyser complexityAnalyser(AppProperties props, ObjectMapper json) {
        ComplexityAnalyser estimate = new HeuristicComplexityAnalyser();
        String key = props.anthropicApiKey();
        if (key == null || key.isBlank()) {
            return estimate;
        }
        return new FallbackComplexityAnalyser(new ClaudeComplexityAnalyser(json, key, props.analysisModel()), estimate);
    }

    /** Best known approaches for each NeetCode 150 problem, to suggest a better one after an analysis. */
    @Bean
    public ApproachRecommender approachRecommender(ObjectMapper json) {
        return new ApproachRecommender(BestApproaches.load(json));
    }
}
