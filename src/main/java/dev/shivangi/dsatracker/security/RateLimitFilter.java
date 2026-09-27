package dev.shivangi.dsatracker.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Limits how often one client (IP address) can hit the public account endpoints. This slows
 * down password guessing, mass sign-ups, and using "forgot password" to spam someone's inbox.
 *
 * <p>The client IP comes from {@code request.getRemoteAddr()}. Behind a hosting proxy,
 * {@code server.forward-headers-strategy: framework} makes that the visitor's address from
 * X-Forwarded-For. Vercel overwrites that header itself, so it can't be faked there; on hosts
 * that don't, the per-account limits in AuthController still protect each account.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Map<String, RateLimiter.Rule> RULES = Map.of(
            "POST /api/auth/signin", RateLimitRules.SIGNIN_PER_IP,
            "POST /api/auth/signup", RateLimitRules.SIGNUP_PER_IP,
            "POST /api/auth/forgot", RateLimitRules.FORGOT_PER_IP,
            "POST /api/auth/reset", RateLimitRules.RESET_PER_IP,
            "GET /api/auth/username-available", RateLimitRules.USERNAME_CHECK_PER_IP);

    private final RateLimiter limiter;

    public RateLimitFilter(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !RULES.containsKey(request.getMethod() + " " + request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimiter.Rule rule = RULES.get(request.getMethod() + " " + request.getRequestURI());
        RateLimiter.Decision decision = limiter.tryAcquire(rule, request.getRemoteAddr());
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"status\":429,\"title\":\"Too Many Requests\",\"detail\":\""
                + TooManyRequestsException.message(decision.retryAfterSeconds()) + "\"}");
    }
}
