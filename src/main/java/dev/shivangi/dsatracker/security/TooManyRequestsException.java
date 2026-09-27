package dev.shivangi.dsatracker.security;

/** A rate limit refused the request. ApiExceptionHandler turns it into 429 with Retry-After. */
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds) {
        super(message(retryAfterSeconds));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    /** "Too many attempts. Try again in 5 minutes." (rounded up to whole minutes) */
    static String message(long retryAfterSeconds) {
        long minutes = Math.max(1, (retryAfterSeconds + 59) / 60);
        return "Too many attempts. Try again in " + minutes + (minutes == 1 ? " minute." : " minutes.");
    }
}
