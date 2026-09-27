package dev.shivangi.dsatracker.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure parts of rate limiting. The SQL counting is covered by RateLimitIntegrationTest. */
class RateLimiterTest {

    private final RateLimiter.Rule signin = new RateLimiter.Rule("signin", 3, Duration.ofMinutes(5));
    private final Instant start = Instant.parse("2026-10-01T10:00:00Z");

    @Test
    void allowsUpToTheLimit() {
        assertTrue(RateLimiter.decide(signin, start, 1, start).allowed());
        assertTrue(RateLimiter.decide(signin, start, 3, start.plusSeconds(10)).allowed());
    }

    @Test
    void refusesPastTheLimitAndSaysWhenTheWindowEnds() {
        RateLimiter.Decision fourth = RateLimiter.decide(signin, start, 4, start);
        assertFalse(fourth.allowed());
        assertEquals(300, fourth.retryAfterSeconds());

        RateLimiter.Decision later = RateLimiter.decide(signin, start, 9, start.plus(Duration.ofMinutes(2)));
        assertEquals(180, later.retryAfterSeconds());
    }

    @Test
    void retryAfterRoundsUpAndIsNeverZero() {
        assertEquals(1, RateLimiter.decide(signin, start, 4, start.plusMillis(299_500)).retryAfterSeconds());
        assertEquals(2, RateLimiter.decide(signin, start, 4, start.plusMillis(298_500)).retryAfterSeconds());
    }

    @Test
    void bucketsAreHashedAndSeparatePerRuleAndKey() {
        String a = DatabaseRateLimiter.bucket(signin, "1.2.3.4");
        assertEquals(64, a.length());
        assertFalse(a.contains("1.2.3.4"));
        assertEquals(a, DatabaseRateLimiter.bucket(signin, "1.2.3.4"));
        assertNotEquals(a, DatabaseRateLimiter.bucket(signin, "5.6.7.8"));
        assertNotEquals(a, DatabaseRateLimiter.bucket(new RateLimiter.Rule("forgot", 3, Duration.ofHours(1)), "1.2.3.4"));
    }

    @Test
    void messagesRoundUpToWholeMinutes() {
        assertEquals("Too many attempts. Try again in 1 minute.", TooManyRequestsException.message(20));
        assertEquals("Too many attempts. Try again in 5 minutes.", TooManyRequestsException.message(300));
    }
}
