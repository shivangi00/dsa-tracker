package dev.shivangi.dsatracker.web;

import dev.shivangi.dsatracker.security.AuthUser;
import dev.shivangi.dsatracker.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** The signed-in user's own account. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final AuthService auth;

    public MeController(AuthService auth) {
        this.auth = auth;
    }

    public record SettingsRequest(@NotNull LocalDate startDate) {
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
}
