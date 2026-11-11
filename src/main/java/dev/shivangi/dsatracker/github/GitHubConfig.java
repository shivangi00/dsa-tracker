package dev.shivangi.dsatracker.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GitHubConfig {

    /** The API address is configurable only so tests can point it at a stand-in. */
    @Bean
    public GitHubClient gitHubClient(ObjectMapper json, @Value("${app.github.api-url:https://api.github.com}") String apiUrl) {
        return new GitHubClient(json, apiUrl);
    }
}
