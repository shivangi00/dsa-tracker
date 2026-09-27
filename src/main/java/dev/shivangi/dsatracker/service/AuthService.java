package dev.shivangi.dsatracker.service;

import dev.shivangi.dsatracker.domain.AppUser;
import dev.shivangi.dsatracker.domain.ProblemRepository;
import dev.shivangi.dsatracker.domain.UserRepository;
import dev.shivangi.dsatracker.security.RecoveryCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Accounts: sign up, username check, recovery codes, start date.
 *
 * <p>Forgotten passwords are handled with a one-time recovery code instead of an emailed link,
 * so the app needs no email service. The code is shown once; only its BCrypt hash is stored.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    public static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.-]{3,30}$");
    public static final int MIN_PASSWORD = 8;
    public static final int MAX_PASSWORD = 72;           // BCrypt ignores anything longer
    static final String WRONG_RECOVERY = "That username and recovery code don't match";

    private final UserRepository users;
    private final ProblemRepository problems;
    private final PasswordEncoder passwordEncoder;
    private final RecoveryCodes recoveryCodes;
    private final Clock clock;
    /** Compared against when the username doesn't exist, so both cases take the same time. */
    private final String dummyHash;

    public AuthService(UserRepository users, ProblemRepository problems, PasswordEncoder passwordEncoder,
                       RecoveryCodes recoveryCodes, Clock clock) {
        this.users = users;
        this.problems = problems;
        this.passwordEncoder = passwordEncoder;
        this.recoveryCodes = recoveryCodes;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("not-a-real-recovery-code");
    }

    public record SignUp(String username, String password, String confirmPassword, LocalDate startDate) {
    }

    /** A new account, or a recovered one, with the recovery code to show once. */
    public record WithCode(AppUser user, String recoveryCode) {
    }

    /** True if the username is well-formed and nobody has it (ignoring case). */
    public boolean isUsernameAvailable(String username) {
        String u = username == null ? "" : username.trim();
        return USERNAME.matcher(u).matches() && !users.existsByUsernameIgnoreCase(u);
    }

    @Transactional
    public WithCode signUp(SignUp req) {
        String username = req.username() == null ? "" : req.username().trim();
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

        AppUser user = new AppUser(username, passwordEncoder.encode(req.password()), start);
        String code = recoveryCodes.generate();
        user.replaceRecoveryCodeHash(passwordEncoder.encode(RecoveryCodes.normalise(code)));
        user = users.save(user);

        // The very first account takes over anything logged before accounts existed.
        if (users.count() == 1) {
            int claimed = problems.claimUnowned(user.getId());
            if (claimed > 0) {
                log.info("First account {} took over {} earlier problem(s)", username, claimed);
            }
        }
        return new WithCode(user, code);
    }

    /**
     * Forgot password: the username plus the recovery code set a new password. The code is used
     * up and a new one is issued. The error never says which of the two was wrong, and an
     * unknown username takes as long as a wrong code, so this can't reveal who has an account.
     */
    @Transactional
    public WithCode recover(String username, String code, String password, String confirmPassword) {
        Optional<AppUser> found = users.findByUsernameIgnoreCase(username == null ? "" : username.trim());
        String typed = RecoveryCodes.normalise(code);
        String hash = found.map(AppUser::getRecoveryCodeHash).orElse(null);
        boolean matches = passwordEncoder.matches(typed, hash != null ? hash : dummyHash);
        if (found.isEmpty() || hash == null || !matches || !RecoveryCodes.looksValid(code)) {
            throw new BadRequestException(WRONG_RECOVERY);
        }
        checkNewPassword(password, confirmPassword);

        AppUser user = found.get();
        user.changePasswordHash(passwordEncoder.encode(password));
        return new WithCode(user, issueCode(user));
    }

    /** Settings: a fresh recovery code (the old one stops working). Needs the current password. */
    @Transactional
    public String newRecoveryCode(Long userId, String currentPassword) {
        AppUser user = get(userId);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("That password isn't right");
        }
        return issueCode(user);
    }

    private String issueCode(AppUser user) {
        String code = recoveryCodes.generate();
        user.replaceRecoveryCodeHash(passwordEncoder.encode(RecoveryCodes.normalise(code)));
        return code;
    }

    @Transactional
    public AppUser changeStartDate(Long userId, LocalDate start) {
        AppUser user = get(userId);
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
}
