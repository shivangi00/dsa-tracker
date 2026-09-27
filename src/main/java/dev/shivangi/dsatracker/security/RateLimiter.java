package dev.shivangi.dsatracker.security;

import java.time.Duration;
import java.time.Instant;

/**
 * Fixed-window rate limiting: at most {@code limit} hits per key in each window.
 * The counting happens in {@link DatabaseRateLimiter}; the decision itself is the pure
 * {@link #decide} below, so it can be unit-tested without a database.
 */
public interface RateLimiter {

    record Rule(String name, int limit, Duration window) {
    }

    record Decision(boolean allowed, long retryAfterSeconds) {
    }

    /** Counts one hit for {@code key} under {@code rule} and says whether it's allowed. */
    Decision tryAcquire(Rule rule, String key);

    /**
     * Given the current window (when it started, hits so far including this one), allow or refuse.
     * A refusal says how many seconds until the window ends (at least 1).
     */
    static Decision decide(Rule rule, Instant windowStart, int hits, Instant now) {
        if (hits <= rule.limit()) {
            return new Decision(true, 0);
        }
        long millisLeft = Duration.between(now, windowStart.plus(rule.window())).toMillis();
        return new Decision(false, Math.max(1, (millisLeft + 999) / 1000));
    }
}
