package dev.shivangi.dsatracker.service;

import dev.shivangi.dsatracker.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends emails over SMTP. Locally they go to Mailpit (http://localhost:8025), a fake inbox;
 * in production, to a real provider such as Resend (see DEPLOY.md). If sending fails locally,
 * the link is written to the log so you're never locked out while developing.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mail;
    private final AppProperties props;

    public MailService(JavaMailSender mail, AppProperties props) {
        this.mail = mail;
        this.props = props;
    }

    /**
     * Runs on a background thread. The "forgot password" request therefore answers just as fast
     * whether or not the email has an account, so response times can't reveal who has one;
     * and a slow mail server never holds up the request.
     */
    @Async
    public void sendPasswordReset(String to, String username, String link) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(props.mailFrom());
        message.setTo(to);
        message.setSubject("Reset your DSA Tracker password");
        message.setText("""
                Hi %s,

                Someone asked to reset the password for your DSA Tracker account.
                Open this link within 30 minutes to choose a new one:

                %s

                If it wasn't you, ignore this email; your password won't change.
                """.formatted(username, link));
        try {
            mail.send(message);
        } catch (MailException e) {
            if (props.logResetLinks()) {
                log.warn("Couldn't send the reset email ({}). Reset link for {}: {}", e.getMessage(), username, link);
            } else {
                // Never log the link in production: anyone reading logs could use it.
                log.error("Couldn't send the reset email to user {}: {}", username, e.getMessage());
            }
        }
    }
}
