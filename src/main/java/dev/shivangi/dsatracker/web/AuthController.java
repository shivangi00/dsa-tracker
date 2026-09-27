package dev.shivangi.dsatracker.web;

import dev.shivangi.dsatracker.security.AuthUser;
import dev.shivangi.dsatracker.security.RateLimitRules;
import dev.shivangi.dsatracker.security.RateLimiter;
import dev.shivangi.dsatracker.security.TooManyRequestsException;
import dev.shivangi.dsatracker.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

/**
 * Public account endpoints (no sign-in needed): sign up, sign in, recover with a recovery code. Sign-out is handled by Spring Security at
 * POST /api/auth/signout (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final RateLimiter limiter;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public AuthController(AuthService auth, AuthenticationManager authenticationManager,
                          SecurityContextRepository contextRepository, RateLimiter limiter,
                          FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.auth = auth;
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.limiter = limiter;
        this.sessions = sessions;
    }

    /** Per-account limits: they hold even if an attacker uses many IP addresses. */
    private void limit(RateLimiter.Rule rule, String account) {
        RateLimiter.Decision decision = limiter.tryAcquire(rule, account.trim().toLowerCase(Locale.ROOT));
        if (!decision.allowed()) {
            throw new TooManyRequestsException(decision.retryAfterSeconds());
        }
    }

    public record SignUpRequest(
            @NotBlank @Size(max = 30) String username,
            @NotBlank @Size(max = 72) String password,
            @NotBlank @Size(max = 72) String confirmPassword,
            LocalDate startDate) {
    }

    public record SignInRequest(@NotBlank String username, @NotBlank String password) {
    }

    /** Forgot password: username + recovery code + the new password twice. */
    public record RecoverRequest(
            @NotBlank @Size(max = 30) String username,
            @NotBlank @Size(max = 40) String recoveryCode,
            @NotBlank @Size(max = 72) String password,
            @NotBlank @Size(max = 72) String confirmPassword) {
    }

    /** The account plus its recovery code, which is shown once and never again. */
    public record WithCodeView(MeView me, String recoveryCode) {
    }

    /** Live check while typing on the sign-up form. */
    @GetMapping("/username-available")
    public Map<String, Boolean> usernameAvailable(@RequestParam String username) {
        return Map.of("available", auth.isUsernameAvailable(username));
    }

    /** Creates the account, signs straight in, and returns the recovery code to show once. */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public WithCodeView signUp(@Valid @RequestBody SignUpRequest req,
                               HttpServletRequest request, HttpServletResponse response) {
        AuthService.WithCode created = auth.signUp(new AuthService.SignUp(
                req.username(), req.password(), req.confirmPassword(), req.startDate()));
        startSession(created.user().getUsername(), req.password(), request, response);
        return new WithCodeView(MeView.of(created.user()), created.recoveryCode());
    }

    /** Wrong username or password → 401 (see ApiExceptionHandler). */
    @PostMapping("/signin")
    public MeView signIn(@Valid @RequestBody SignInRequest req,
                         HttpServletRequest request, HttpServletResponse response) {
        limit(RateLimitRules.SIGNIN_PER_ACCOUNT, req.username());
        Authentication authentication = startSession(req.username().trim(), req.password(), request, response);
        Long id = ((AuthUser) authentication.getPrincipal()).id();
        return MeView.of(auth.get(id));
    }

    /**
     * Forgot password. On success: the password is changed, the code is replaced by a new one,
     * every existing sign-in for this account is ended (in case someone else was signed in),
     * and this browser is signed in with the new password.
     */
    @PostMapping("/recover")
    public WithCodeView recover(@Valid @RequestBody RecoverRequest req,
                                HttpServletRequest request, HttpServletResponse response) {
        limit(RateLimitRules.RECOVER_PER_ACCOUNT, req.username());
        AuthService.WithCode recovered = auth.recover(
                req.username(), req.recoveryCode(), req.password(), req.confirmPassword());
        String username = recovered.user().getUsername();
        sessions.findByPrincipalName(username).keySet().forEach(sessions::deleteById);
        startSession(username, req.password(), request, response);
        return new WithCodeView(MeView.of(recovered.user()), recovered.recoveryCode());
    }

    /**
     * Checks the password, then remembers the user in the HTTP session. The session id is
     * changed first so an id planted before sign-in can't be reused (session fixation).
     */
    private Authentication startSession(String username, String password,
                                        HttpServletRequest request, HttpServletResponse response) {
        Authentication checked = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(username, password));

        // Keep only the id and username in the session (which is stored in the database):
        // no password, no password hash.
        AuthUser user = (AuthUser) checked.getPrincipal();
        AuthUser slim = new AuthUser(user.id(), user.username(), "");
        Authentication authentication =
                UsernamePasswordAuthenticationToken.authenticated(slim, null, slim.getAuthorities());

        request.getSession(true);
        request.changeSessionId();
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return authentication;
    }
}
