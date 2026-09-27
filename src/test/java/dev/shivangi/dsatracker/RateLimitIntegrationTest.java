package dev.shivangi.dsatracker;

import dev.shivangi.dsatracker.config.AppProperties;
import dev.shivangi.dsatracker.security.DatabaseRateLimiter;
import dev.shivangi.dsatracker.security.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rate limits switched ON, against a real Postgres. Needs Docker (like ApiIntegrationTest). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "app.rate-limits-enabled=true")
class RateLimitIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AppProperties props;

    @Test
    void theEleventhSignInFromOneAddressWithinFiveMinutesIsRefused() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/auth/signin").with(csrf()).contentType(APPLICATION_JSON)
                            .with(r -> { r.setRemoteAddr("203.0.113.7"); return r; })
                            .content("{\"username\":\"nobody" + i + "\",\"password\":\"wrong-password\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/signin").with(csrf()).contentType(APPLICATION_JSON)
                        .with(r -> { r.setRemoteAddr("203.0.113.7"); return r; })
                        .content("{\"username\":\"nobody\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail").value("Too many attempts. Try again in 5 minutes."));
    }

    @Test
    void oneAccountGetsAtMostFiveRecoveryAttemptsAnHourWhateverTheAddress() throws Exception {
        String body = "{\"username\":\"victim\",\"recoveryCode\":\"AAAA-BBBB-CCCC-DDDD\","
                + "\"password\":\"password123\",\"confirmPassword\":\"password123\"}";
        for (int i = 0; i < 5; i++) {
            String ip = "198.51.100." + i;   // a different address every time
            mvc.perform(post("/api/auth/recover").with(csrf()).contentType(APPLICATION_JSON)
                            .with(r -> { r.setRemoteAddr(ip); return r; })
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/recover").with(csrf()).contentType(APPLICATION_JSON)
                        .with(r -> { r.setRemoteAddr("198.51.100.99"); return r; })
                        .content(body.replace("victim", "Victim")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void twoCopiesOfTheAppShareOneCount() {
        // Two limiter objects = two app instances pointing at the same database.
        RateLimiter first = new DatabaseRateLimiter(jdbc, Clock.systemUTC(), props);
        RateLimiter second = new DatabaseRateLimiter(jdbc, Clock.systemUTC(), props);
        RateLimiter.Rule rule = new RateLimiter.Rule("shared-test", 3, Duration.ofMinutes(5));

        assertTrue(first.tryAcquire(rule, "client").allowed());
        assertTrue(second.tryAcquire(rule, "client").allowed());
        assertTrue(first.tryAcquire(rule, "client").allowed());
        RateLimiter.Decision fourth = second.tryAcquire(rule, "client");
        assertFalse(fourth.allowed());
        assertTrue(fourth.retryAfterSeconds() > 0 && fourth.retryAfterSeconds() <= 300);

        Integer rows = jdbc.queryForObject("SELECT count(*) FROM rate_limits WHERE bucket LIKE '%client%'", Integer.class);
        assertEquals(0, rows, "raw keys are never stored");
    }
}
