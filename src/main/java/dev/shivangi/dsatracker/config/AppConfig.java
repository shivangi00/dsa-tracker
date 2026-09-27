package dev.shivangi.dsatracker.config;

import dev.shivangi.dsatracker.consistency.ConsistencyCalculator;
import dev.shivangi.dsatracker.repetition.SpacedRepetitionPolicy;
import dev.shivangi.dsatracker.weekly.TestBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Random;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
@EnableAsync   // lets MailService send emails in the background
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
    public TestBuilder testBuilder() {
        return new TestBuilder(new Random());
    }
}
