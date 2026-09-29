package dev.shivangi.dsatracker.sd;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Random;

@Configuration
public class SdConfig {

    @Bean
    public SdCatalog sdCatalog(ObjectMapper json) {
        return SdCatalog.load(json);
    }

    @Bean
    public Quiz quiz() {
        return new Quiz(new Random());
    }

    @Bean
    public DesignScorer designScorer() {
        return new DesignScorer();
    }
}
