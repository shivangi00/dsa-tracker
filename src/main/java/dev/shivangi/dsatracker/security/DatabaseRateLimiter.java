package dev.shivangi.dsatracker.security;

import dev.shivangi.dsatracker.config.AppProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate-limit counters kept in Postgres (table from V7), so every copy of the app shares them.
 *
 * <p>One SQL statement does the whole job atomically: insert the counter, or, if it exists,
 * either start a new window (the old one ended) or add one hit. Postgres locks the row for the
 * statement, so two simultaneous requests, even on different app copies, can't both be counted
 * as "the last allowed one".
 */
@Component
public class DatabaseRateLimiter implements RateLimiter {

    private static final String HIT = """
            INSERT INTO rate_limits (bucket, window_start, hits) VALUES (?, ?, 1)
            ON CONFLICT (bucket) DO UPDATE SET
                hits         = CASE WHEN rate_limits.window_start <= ? THEN 1 ELSE rate_limits.hits + 1 END,
                window_start = CASE WHEN rate_limits.window_start <= ? THEN EXCLUDED.window_start
                                    ELSE rate_limits.window_start END
            RETURNING window_start, hits
            """;

    /** Old rows are deleted now and then, rather than by a scheduled job that could keep the database awake. */
    private static final int CLEANUP_EVERY = 500;
    private static final Duration KEEP_FOR = Duration.ofDays(1);

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean enabled;
    private final AtomicInteger calls = new AtomicInteger();

    public DatabaseRateLimiter(JdbcTemplate jdbc, Clock clock, AppProperties props) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.enabled = props.rateLimitsEnabled();
    }

    @Override
    public Decision tryAcquire(Rule rule, String key) {
        if (!enabled) {
            return new Decision(true, 0);
        }
        Instant now = clock.instant();
        OffsetDateTime nowUtc = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        OffsetDateTime windowEndedBefore = nowUtc.minus(rule.window());

        Decision decision = jdbc.queryForObject(HIT,
                (rs, i) -> RateLimiter.decide(rule,
                        rs.getObject("window_start", OffsetDateTime.class).toInstant(),
                        rs.getInt("hits"), now),
                bucket(rule, key), nowUtc, windowEndedBefore, windowEndedBefore);

        if (calls.incrementAndGet() % CLEANUP_EVERY == 0) {
            jdbc.update("DELETE FROM rate_limits WHERE window_start < ?", nowUtc.minus(KEEP_FOR));
        }
        return decision;
    }

    /** SHA-256 of "rule:key", so the table never holds a raw IP address or email. */
    static String bucket(Rule rule, String key) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((rule.name() + ":" + key).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
