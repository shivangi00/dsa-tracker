package dev.shivangi.dsatracker.security;

import java.time.Duration;

/**
 * Every limit in one place. Per-IP rules slow down one client; per-account rules protect one
 * account or inbox even if an attacker spreads requests over many IPs (or fakes the IP header).
 */
public final class RateLimitRules {

    // Per IP address (RateLimitFilter)
    public static final RateLimiter.Rule SIGNIN_PER_IP = new RateLimiter.Rule("signin-ip", 10, Duration.ofMinutes(5));
    public static final RateLimiter.Rule SIGNUP_PER_IP = new RateLimiter.Rule("signup-ip", 5, Duration.ofHours(1));
    public static final RateLimiter.Rule RECOVER_PER_IP = new RateLimiter.Rule("recover-ip", 10, Duration.ofHours(1));
    public static final RateLimiter.Rule USERNAME_CHECK_PER_IP = new RateLimiter.Rule("username-ip", 60, Duration.ofMinutes(1));

    // Per account (AuthController)
    /** Stops password guessing against one account from many IPs. */
    public static final RateLimiter.Rule SIGNIN_PER_ACCOUNT = new RateLimiter.Rule("signin-account", 20, Duration.ofHours(1));
    /** Stops recovery-code guessing against one account from many IPs. */
    public static final RateLimiter.Rule RECOVER_PER_ACCOUNT = new RateLimiter.Rule("recover-account", 5, Duration.ofHours(1));
    /** Making a new recovery code checks the password, so it's limited like sign-in. */
    public static final RateLimiter.Rule NEW_CODE_PER_USER = new RateLimiter.Rule("new-code-user", 10, Duration.ofHours(1));

    // Per user (AnalysisService)
    /** Keeps the cost of the optional Claude analyser bounded; generous for normal use. */
    public static final RateLimiter.Rule ANALYSES_PER_USER = new RateLimiter.Rule("analyse-user", 50, Duration.ofDays(1));

    private RateLimitRules() {
    }
}
