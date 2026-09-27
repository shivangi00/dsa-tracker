package dev.shivangi.dsatracker.service;

import dev.shivangi.dsatracker.config.AppProperties;
import dev.shivangi.dsatracker.domain.AppUser;
import dev.shivangi.dsatracker.domain.PasswordResetToken;
import dev.shivangi.dsatracker.domain.PasswordResetTokenRepository;
import dev.shivangi.dsatracker.domain.ProblemRepository;
import dev.shivangi.dsatracker.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Accounts: sign up, username check, forgot password, reset password, start date. */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    public static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.-]{3,30}$");
    public static final int MIN_PASSWORD = 8;
    public static final int MAX_PASSWORD = 72;           // BCrypt ignores anything longer
    public static final Duration RESET_LINK_LIFETIME = Duration.ofMinutes(30);

    private final UserRepository users;
    private final ProblemRepository problems;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final MailService mail;
    private final AppProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, ProblemRepository problems,
                       PasswordResetTokenRepository resetTokens, PasswordEncoder passwordEncoder,
                       MailService mail, AppProperties props, Clock clock) {
        this.users = users;
        this.problems = problems;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.mail = mail;
        this.props = props;
        this.clock = clock;
    }

    public record SignUp(String username, String email, String password, String confirmPassword,
                         LocalDate startDate) {
    }

    /** True if the username is well-formed and nobody has it (ignoring case). */
    public boolean isUsernameAvailable(String username) {
        String u = username == null ? "" : username.trim();
        return USERNAME.matcher(u).matches() && !users.existsByUsernameIgnoreCase(u);
    }

    @Transactional
    public AppUser signUp(SignUp req) {
        String username = req.username().trim();
        String email = req.email().trim();
        if (!USERNAME.matcher(username).matches()) {
            throw new BadRequestException("Usernames are 3–30 characters: letters, numbers, _ . or -");
        }
        checkNewPassword(req.password(), req.confirmPassword());

        LocalDate today = LocalDate.now(clock);
        LocalDate start = req.startDate() == null ? today : req.startDate();
        checkStartDate(start, today);

        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("That username is taken");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists. Try signing in or resetting your password.");
        }

        AppUser user = users.save(new AppUser(username, email, passwordEncoder.encode(req.password()), start));

        // The very first account takes over anything logged before accounts existed.
        if (users.count() == 1) {
            int claimed = problems.claimUnowned(user.getId());
            if (claimed > 0) {
                log.info("First account {} took over {} earlier problem(s)", username, claimed);
            }
        }
        return user;
    }

    /**
     * Sends a reset link if the email belongs to an account. It deliberately behaves the same
     * either way, so this form can't be used to find out who has an account.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        users.findByEmailIgnoreCase(email == null ? "" : email.trim()).ifPresent(user -> {
            resetTokens.deleteAllForUser(user.getId());          // only the newest link works
            String token = newToken();
            Instant expires = clock.instant().plus(RESET_LINK_LIFETIME);
            resetTokens.save(new PasswordResetToken(user.getId(), sha256(token), expires));
            String link = props.baseUrl() + "/auth.html?reset=" + token;
            mail.sendPasswordReset(user.getEmail(), user.getUsername(), link);
        });
    }

    @Transactional
    public void resetPassword(String token, String password, String confirmPassword) {
        PasswordResetToken stored = resetTokens.findByTokenHash(sha256(token == null ? "" : token))
                .filter(t -> t.isUsableAt(clock.instant()))
                .orElseThrow(() -> new BadRequestException("This reset link is invalid or has expired. Request a new one."));
        checkNewPassword(password, confirmPassword);

        AppUser user = users.findById(stored.getUserId())
                .orElseThrow(() -> new BadRequestException("This reset link is invalid or has expired. Request a new one."));
        user.changePasswordHash(passwordEncoder.encode(password));
        resetTokens.deleteAllForUser(user.getId());   // the link (and any older one) can't be used again
    }

    @Transactional
    public AppUser changeStartDate(Long userId, LocalDate start) {
        AppUser user = users.findById(userId).orElseThrow(() -> new NotFoundException("No such user"));
        checkStartDate(start, LocalDate.now(clock));
        user.changeStartDate(start);
        return user;
    }

    public AppUser get(Long userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("No such user"));
    }

    private static void checkNewPassword(String password, String confirm) {
        if (password == null || password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            throw new BadRequestException("Passwords are " + MIN_PASSWORD + "–" + MAX_PASSWORD + " characters");
        }
        if (!password.equals(confirm)) {
            throw new BadRequestException("The passwords don't match");
        }
    }

    private static void checkStartDate(LocalDate start, LocalDate today) {
        if (start.isBefore(today.minusYears(1)) || start.isAfter(today.plusYears(1))) {
            throw new BadRequestException("Pick a start date within a year of today");
        }
    }

    /** 32 random bytes, URL-safe: about 256 bits, far too many to guess. */
    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // every Java runtime has SHA-256
        }
    }
}
