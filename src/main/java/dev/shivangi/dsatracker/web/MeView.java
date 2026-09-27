package dev.shivangi.dsatracker.web;

import dev.shivangi.dsatracker.domain.AppUser;

import java.time.LocalDate;

/** The signed-in account, as the API returns it. Never includes the password hash. */
public record MeView(String username, LocalDate startDate, boolean hasRecoveryCode) {

    public static MeView of(AppUser user) {
        return new MeView(user.getUsername(), user.getStartDate(), user.hasRecoveryCode());
    }
}
