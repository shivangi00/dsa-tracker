package dev.shivangi.dsatracker.web;

import dev.shivangi.dsatracker.security.AuthUser;
import dev.shivangi.dsatracker.security.RateLimitRules;
import dev.shivangi.dsatracker.security.RateLimiter;
import dev.shivangi.dsatracker.security.TooManyRequestsException;
import dev.shivangi.dsatracker.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/** The signed-in user's own account. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final AuthService auth;
    private final RateLimiter limiter;

    public MeController(AuthService auth, RateLimiter limiter) {
        this.auth = auth;
        this.limiter = limiter;
    }

    public record SettingsRequest(@NotNull LocalDate startDate) {
    }

    public record NewCodeRequest(@NotBlank String password) {
    }

    @GetMapping
    public MeView me(@AuthenticationPrincipal AuthUser me) {
        return MeView.of(auth.get(me.id()));
    }

    /** Change your start date. */
    @PatchMapping
    public MeView update(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody SettingsRequest req) {
        return MeView.of(auth.changeStartDate(me.id(), req.startDate()));
    }

    /** A new recovery code (the old one stops working). Asks for the password again. */
    @PostMapping("/recovery-code")
    public Map<String, String> newRecoveryCode(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody NewCodeRequest req) {
        RateLimiter.Decision decision = limiter.tryAcquire(RateLimitRules.NEW_CODE_PER_USER, String.valueOf(me.id()));
        if (!decision.allowed()) {
            throw new TooManyRequestsException(decision.retryAfterSeconds());
        }
        return Map.of("recoveryCode", auth.newRecoveryCode(me.id(), req.password()));
    }
}
