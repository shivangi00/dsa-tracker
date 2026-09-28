package dev.shivangi.dsatracker;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests of the real app against a real Postgres (started in Docker by Testcontainers).
 * Every migration runs, so these also prove V1–V6 apply cleanly to an empty database.
 *
 * <p>Needs Docker running. GitHub Actions provides it; locally, start Docker Desktop first.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "app.rate-limits-enabled=false")
class ApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    // ---------- helpers

    /** A signed-up user: their session cookie and the recovery code shown at sign-up. */
    private record NewUser(Cookie session, String recoveryCode) {
    }

    private NewUser signUpWithCode(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/signup").with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"password123","confirmPassword":"password123"}
                                """.formatted(username)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.me.hasRecoveryCode").value(true))
                .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session, "sign-up should start a session");
        String code = JsonPath.read(result.getResponse().getContentAsString(), "$.recoveryCode");
        return new NewUser(session, code);
    }

    /** Signs up a new user and returns their session cookie. */
    private Cookie signUp(String username) throws Exception {
        return signUpWithCode(username).session();
    }

    private static String recoverBody(String username, String code, String newPassword) {
        return """
                {"username":"%s","recoveryCode":"%s","password":"%s","confirmPassword":"%s"}
                """.formatted(username, code, newPassword, newPassword);
    }

    private static String signInBody(String username, String password) {
        return "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
    }

    /** Marks NeetCode problem {@code catalogId} done (no rating, so Medium) and returns the new problem's id. */
    private long markDone(Cookie session, int catalogId) throws Exception {
        String body = mvc.perform(post("/api/catalog/" + catalogId + "/done").with(csrf()).cookie(session)
                        .contentType(APPLICATION_JSON)
                        .content("{\"learnings\":\"Use a hash map of value to index.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.intervalDays").value(3))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    // ---------- security

    @Test
    void apiNeedsASignedInSession() throws Exception {
        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void writesWithoutTheCsrfTokenAreRejected() throws Exception {
        mvc.perform(post("/api/auth/signin").contentType(APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"password\":\"y\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongPasswordGets401WithoutSayingWhichPartWasWrong() throws Exception {
        signUp("frank");
        mvc.perform(post("/api/auth/signin").with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"username\":\"frank\",\"password\":\"not-the-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Wrong username or password"));
    }

    @Test
    void usernamesAreUniqueIgnoringCase() throws Exception {
        signUp("erin");
        mvc.perform(post("/api/auth/signup").with(csrf()).contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"ERIN","password":"password123","confirmPassword":"password123"}
                                """))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/auth/username-available").param("username", "Erin"))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void usersCanNeverSeeOrChangeEachOthersProblems() throws Exception {
        Cookie carol = signUp("carol");
        Cookie dave = signUp("dave");
        long carolsProblem = markDone(carol, 3);

        mvc.perform(delete("/api/problems/" + carolsProblem).with(csrf()).cookie(dave))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/dashboard").cookie(dave))
                .andExpect(jsonPath("$.counts.done").value(0));
        mvc.perform(get("/api/dashboard").cookie(carol))
                .andExpect(jsonPath("$.counts.done").value(1));
    }

    // ---------- the tracker

    @Test
    void signUpThenSeeAllOneHundredFiftyProblems() throws Exception {
        Cookie alice = signUp("alice");
        mvc.perform(get("/api/dashboard").cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.catalog.length()").value(150))
                .andExpect(jsonPath("$.consistency.totalStudyDays").value(0));
    }

    @Test
    void aProblemCanBeMarkedDoneOnlyOnceAndIsntReviewableUntilDue() throws Exception {
        Cookie bob = signUp("bob");
        long id = markDone(bob, 3);

        mvc.perform(post("/api/catalog/3/done").with(csrf()).cookie(bob).contentType(APPLICATION_JSON)
                        .content("{\"learnings\":\"again\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/problems/" + id + "/reviews").with(csrf()).cookie(bob).contentType(APPLICATION_JSON)
                        .content("{\"remembered\":true}"))
                .andExpect(status().isConflict());   // not due for 3 days
        mvc.perform(get("/api/dashboard").cookie(bob))
                .andExpect(jsonPath("$.consistency.activeToday").value(true))
                .andExpect(jsonPath("$.consistency.totalStudyDays").value(1));
    }

    @Test
    void yourRatingsDecideTheSchedule() throws Exception {
        Cookie tara = signUp("tara");

        // Rated when marking done: Forgot → tomorrow, Easy → 5 days
        String forgot = mvc.perform(post("/api/catalog/4/done").with(csrf()).cookie(tara).contentType(APPLICATION_JSON)
                        .content("{\"learnings\":\"Needed the hint.\",\"rating\":\"AGAIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.intervalDays").value(1))
                .andExpect(jsonPath("$.firstRating").value("AGAIN"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/catalog/2/done").with(csrf()).cookie(tara).contentType(APPLICATION_JSON)
                        .content("{\"learnings\":\"Counting letters.\",\"rating\":\"EASY\"}"))
                .andExpect(jsonPath("$.intervalDays").value(5));
        mvc.perform(get("/api/dashboard").cookie(tara))
                .andExpect(jsonPath("$.firstGaps.AGAIN").value(1))
                .andExpect(jsonPath("$.firstGaps.EASY").value(5));

        // Make the Forgot one due today: the buttons get each rating's gap
        long id = ((Number) JsonPath.read(forgot, "$.id")).longValue();
        jdbc.update("UPDATE problems SET solved_on = solved_on - 1, last_reviewed_on = last_reviewed_on - 1, "
                + "next_due_on = next_due_on - 1 WHERE id = ?", id);
        mvc.perform(get("/api/dashboard").cookie(tara))
                .andExpect(jsonPath("$.due[0].reviewGaps.AGAIN").value(1))
                .andExpect(jsonPath("$.due[0].reviewGaps.HARD").value(2))
                .andExpect(jsonPath("$.due[0].reviewGaps.GOOD").value(3))
                .andExpect(jsonPath("$.due[0].reviewGaps.EASY").value(4));

        mvc.perform(post("/api/problems/" + id + "/reviews").with(csrf()).cookie(tara).contentType(APPLICATION_JSON)
                        .content("{\"rating\":\"EASY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intervalDays").value(4))
                .andExpect(jsonPath("$.reps").value(1))
                .andExpect(jsonPath("$.reviewGaps").isEmpty());   // not due any more

        // A review needs a rating
        jdbc.update("UPDATE problems SET next_due_on = next_due_on - 4 WHERE id = ?", id);
        mvc.perform(post("/api/problems/" + id + "/reviews").with(csrf()).cookie(tara).contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        // The older {remembered: false} still works, as Again
        mvc.perform(post("/api/problems/" + id + "/reviews").with(csrf()).cookie(tara).contentType(APPLICATION_JSON)
                        .content("{\"remembered\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intervalDays").value(1))
                .andExpect(jsonPath("$.lapses").value(1));
    }

    @Test
    void thisWeeksTestStaysLockedUntilTheLastDayOfTheWeek() throws Exception {
        Cookie gina = signUp("gina");
        markDone(gina, 3);
        mvc.perform(post("/api/tests/week/1").with(csrf()).cookie(gina))
                .andExpect(status().isConflict());
    }

    // ---------- recovery codes (forgot password without email)

    @Test
    void theRecoveryCodeSetsANewPasswordAndIsReplaced() throws Exception {
        NewUser lena = signUpWithCode("lena");
        assertTrue(lena.recoveryCode().matches("[0-9A-Z]{4}(-[0-9A-Z]{4}){3}"));

        // Typed in lower case with spaces: still accepted
        String typed = lena.recoveryCode().toLowerCase().replace("-", " ");
        String body = mvc.perform(post("/api/auth/recover").with(csrf()).contentType(APPLICATION_JSON)
                        .content(recoverBody("LENA", typed, "brand-new-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me.username").value("lena"))
                .andReturn().getResponse().getContentAsString();
        String newCode = JsonPath.read(body, "$.recoveryCode");
        assertNotEquals(lena.recoveryCode(), newCode);

        // The old code is used up; the new password works; the old one doesn't
        mvc.perform(post("/api/auth/recover").with(csrf()).contentType(APPLICATION_JSON)
                        .content(recoverBody("lena", lena.recoveryCode(), "another-pass-1")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/signin").with(csrf()).contentType(APPLICATION_JSON)
                        .content(signInBody("lena", "brand-new-pass")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/signin").with(csrf()).contentType(APPLICATION_JSON)
                        .content(signInBody("lena", "password123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recoverySignsOutEveryOtherSession() throws Exception {
        NewUser moe = signUpWithCode("moe");   // usernames are 3–30 characters
        mvc.perform(get("/api/dashboard").cookie(moe.session())).andExpect(status().isOk());

        mvc.perform(post("/api/auth/recover").with(csrf()).contentType(APPLICATION_JSON)
                        .content(recoverBody("moe", moe.recoveryCode(), "brand-new-pass")))
                .andExpect(status().isOk());

        // Whoever was signed in with the old password (maybe someone who stole it) is out
        mvc.perform(get("/api/dashboard").cookie(moe.session())).andExpect(status().isUnauthorized());
    }

    @Test
    void aWrongCodeAndAnUnknownUserGetTheSameAnswer() throws Exception {
        signUp("nia");
        String wrongCode = mvc.perform(post("/api/auth/recover").with(csrf()).contentType(APPLICATION_JSON)
                        .content(recoverBody("nia", "AAAA-BBBB-CCCC-DDDD", "brand-new-pass")))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        String unknownUser = mvc.perform(post("/api/auth/recover").with(csrf()).contentType(APPLICATION_JSON)
                        .content(recoverBody("nobody-here", "AAAA-BBBB-CCCC-DDDD", "brand-new-pass")))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        String wrongCodeMessage = JsonPath.read(wrongCode, "$.detail");
        String unknownUserMessage = JsonPath.read(unknownUser, "$.detail");
        assertEquals(wrongCodeMessage, unknownUserMessage);
    }

    @Test
    void aNewCodeFromSettingsNeedsThePasswordAndReplacesTheOldOne() throws Exception {
        NewUser omar = signUpWithCode("omar");
        mvc.perform(post("/api/me/recovery-code").with(csrf()).cookie(omar.session()).contentType(APPLICATION_JSON)
                        .content("{\"password\":\"wrong-password\"}"))
                .andExpect(status().isBadRequest());
        String body = mvc.perform(post("/api/me/recovery-code").with(csrf()).cookie(omar.session()).contentType(APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String fresh = JsonPath.read(body, "$.recoveryCode");

        mvc.perform(post("/api/auth/recover").with(csrf()).contentType(APPLICATION_JSON)
                        .content(recoverBody("omar", omar.recoveryCode(), "brand-new-pass")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/recover").with(csrf()).contentType(APPLICATION_JSON)
                        .content(recoverBody("omar", fresh, "brand-new-pass")))
                .andExpect(status().isOk());
    }

    // ---------- attempts: notes, saved versions and analysis

    private static final String TWO_SUM_CODE =
            "def twoSum(nums, target):\\n    seen = {}\\n    for i, n in enumerate(nums):\\n        if target - n in seen:\\n"
            + "            return [seen[target - n], i]\\n        seen[n] = i\\n";
    private static final String TWO_SUM_BRUTE =
            "def twoSum(nums, target):\\n    for i in range(len(nums)):\\n        for j in range(i + 1, len(nums)):\\n"
            + "            if nums[i] + nums[j] == target:\\n                return [i, j]\\n";

    private static long idAt(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    private String body(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    @Test
    void theSolveDayKeepsEveryVersionYouSave() throws Exception {
        Cookie hana = signUp("hana");
        // Mark done with brute-force code: saved as version 1 and analysed
        String done = body(mvc.perform(post("/api/catalog/3/done").with(csrf()).cookie(hana).contentType(APPLICATION_JSON)
                        .content("{\"learnings\":\"Brute force first.\",\"code\":\"" + TWO_SUM_BRUTE + "\",\"codeLanguage\":\"PYTHON\",\"rating\":\"HARD\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attempts.length()").value(1))
                .andExpect(jsonPath("$.attempts[0].revision").value(0))
                .andExpect(jsonPath("$.attempts[0].editable").value(true))
                .andExpect(jsonPath("$.attempts[0].versions[0].versionNo").value(1))
                .andExpect(jsonPath("$.attempts[0].versions[0].analysis.time").value("O(n²)"))
                .andExpect(jsonPath("$.attempts[0].versions[0].analysis.recommendation.verdict").value("faster")));
        long attempt = idAt(done, "$.attempts[0].id");

        // Optimised: "Save as new version" keeps version 1 as it was
        mvc.perform(post("/api/attempts/" + attempt + "/versions").with(csrf()).cookie(hana).contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + TWO_SUM_CODE + "\",\"codeLanguage\":\"PYTHON\",\"analyse\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attempts[0].versions.length()").value(2))
                .andExpect(jsonPath("$.attempts[0].versions[0].analysis.time").value("O(n²)"))
                .andExpect(jsonPath("$.attempts[0].versions[1].versionNo").value(2))
                .andExpect(jsonPath("$.attempts[0].versions[1].analysis").doesNotExist());

        // Analysis is separate: analyse version 2 when you like (on the day)
        String after = body(mvc.perform(get("/api/dashboard").cookie(hana)));
        long v2 = idAt(after, "$.catalog[2].progress.attempts[0].versions[1].id");
        mvc.perform(post("/api/versions/" + v2 + "/analysis").with(csrf()).cookie(hana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts[0].versions[1].analysis.time").value("O(n)"))
                .andExpect(jsonPath("$.attempts[0].versions[1].analysis.recommendation.verdict").value("optimal"))
                .andExpect(jsonPath("$.attempts[0].versions[0].improved").value(false))
                .andExpect(jsonPath("$.attempts[0].versions[1].improved").value(true));   // O(n) beats O(n²)
        mvc.perform(get("/api/dashboard").cookie(hana)).andExpect(jsonPath("$.counts.improved").value(1));

        // Notes can be edited on the day
        mvc.perform(patch("/api/attempts/" + attempt).with(csrf()).cookie(hana).contentType(APPLICATION_JSON)
                        .content("{\"learnings\":\"Brute force, then a hash map.\",\"excalidrawUrl\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts[0].learnings").value("Brute force, then a hash map."));
        // …but the solve day needs notes
        mvc.perform(patch("/api/attempts/" + attempt).with(csrf()).cookie(hana).contentType(APPLICATION_JSON)
                        .content("{\"learnings\":\" \"}"))
                .andExpect(status().isBadRequest());

        // A version can be deleted on the day
        mvc.perform(delete("/api/versions/" + v2).with(csrf()).cookie(hana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts[0].versions.length()").value(1));
    }

    @Test
    void atMostFiveVersionsPerAttempt() throws Exception {
        Cookie lia = signUp("lia");
        String done = body(mvc.perform(post("/api/catalog/3/done").with(csrf()).cookie(lia).contentType(APPLICATION_JSON)
                .content("{\"learnings\":\"x\",\"code\":\"" + TWO_SUM_CODE + "\",\"codeLanguage\":\"PYTHON\"}")));
        long attempt = idAt(done, "$.attempts[0].id");
        for (int i = 2; i <= 5; i++) {
            mvc.perform(post("/api/attempts/" + attempt + "/versions").with(csrf()).cookie(lia).contentType(APPLICATION_JSON)
                            .content("{\"code\":\"" + TWO_SUM_CODE + "\",\"codeLanguage\":\"PYTHON\"}"))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/attempts/" + attempt + "/versions").with(csrf()).cookie(lia).contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + TWO_SUM_CODE + "\",\"codeLanguage\":\"PYTHON\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void codeCanBeAnalysedBeforeMarkingDone() throws Exception {
        Cookie pia = signUp("pia");
        String code = "def containsDuplicate(nums):\\n    for x in nums:\\n        for y in nums:\\n            pass\\n";
        mvc.perform(post("/api/analysis/preview").with(csrf()).cookie(pia).contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"codeLanguage\":\"PYTHON\",\"catalogId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.time").value("O(n²)"))
                .andExpect(jsonPath("$.recommendation.approach.name").value("Hash set"));
        mvc.perform(post("/api/analysis/preview").with(csrf()).cookie(pia).contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"codeLanguage\":\"PYTHON\"}"))
                .andExpect(jsonPath("$.recommendation").doesNotExist());   // no problem given: nothing to compare
        mvc.perform(get("/api/dashboard").cookie(pia)).andExpect(jsonPath("$.counts.done").value(0));
    }

    @Test
    void aRevisionIsAnAttemptWithOptionalNotesAndCode() throws Exception {
        Cookie ria = signUp("ria");
        long id = markDone(ria, 3);
        jdbc.update("UPDATE problems SET next_due_on = CURRENT_DATE - 1 WHERE id = ?", id);   // due (a day late)

        // Revision 1 with notes and code
        mvc.perform(post("/api/problems/" + id + "/reviews").with(csrf()).cookie(ria).contentType(APPLICATION_JSON)
                        .content("{\"rating\":\"GOOD\",\"learnings\":\"Remembered the hash map.\",\"code\":\"" + TWO_SUM_CODE
                                + "\",\"codeLanguage\":\"PYTHON\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisionsDone").value(1))
                .andExpect(jsonPath("$.attempts.length()").value(2))
                .andExpect(jsonPath("$.attempts[1].revision").value(1))
                .andExpect(jsonPath("$.attempts[1].tryNo").value(1))
                .andExpect(jsonPath("$.attempts[1].rating").value("GOOD"))
                .andExpect(jsonPath("$.attempts[1].learnings").value("Remembered the hash map."))
                .andExpect(jsonPath("$.attempts[1].versions[0].analysis.time").value("O(n)"));

        // Revision 2 with nothing but a rating: Again repeats it as try 2 of revision 2
        jdbc.update("UPDATE problems SET next_due_on = CURRENT_DATE WHERE id = ?", id);
        mvc.perform(post("/api/problems/" + id + "/reviews").with(csrf()).cookie(ria).contentType(APPLICATION_JSON)
                        .content("{\"rating\":\"AGAIN\"}"))
                .andExpect(jsonPath("$.revisionsDone").value(1))
                .andExpect(jsonPath("$.attempts[2].revision").value(2))
                .andExpect(jsonPath("$.attempts[2].learnings").doesNotExist());
        jdbc.update("UPDATE problems SET next_due_on = CURRENT_DATE WHERE id = ?", id);
        mvc.perform(post("/api/problems/" + id + "/reviews").with(csrf()).cookie(ria).contentType(APPLICATION_JSON)
                        .content("{\"rating\":\"HARD\"}"))
                .andExpect(jsonPath("$.revisionsDone").value(2))
                .andExpect(jsonPath("$.attempts[3].revision").value(2))
                .andExpect(jsonPath("$.attempts[3].tryNo").value(2));

        // Revision 3 completes it: fully revised, nothing more due
        jdbc.update("UPDATE problems SET next_due_on = CURRENT_DATE WHERE id = ?", id);
        mvc.perform(get("/api/dashboard").cookie(ria))
                .andExpect(jsonPath("$.due[0].reviewGaps.GOOD").value(0));   // 0 = completes
        mvc.perform(post("/api/problems/" + id + "/reviews").with(csrf()).cookie(ria).contentType(APPLICATION_JSON)
                        .content("{\"rating\":\"EASY\"}"))
                .andExpect(jsonPath("$.revisionsDone").value(3))
                .andExpect(jsonPath("$.fullyRevised").value(true))
                .andExpect(jsonPath("$.nextDueOn").doesNotExist());
        mvc.perform(get("/api/dashboard").cookie(ria))
                .andExpect(jsonPath("$.counts.fullyRevised").value(1))
                .andExpect(jsonPath("$.due.length()").value(0));
        mvc.perform(post("/api/problems/" + id + "/reviews").with(csrf()).cookie(ria).contentType(APPLICATION_JSON)
                        .content("{\"rating\":\"GOOD\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void attemptsAreFrozenOnceTheirDayIsOver() throws Exception {
        Cookie ivan = signUp("ivan");
        String done = body(mvc.perform(post("/api/catalog/3/done").with(csrf()).cookie(ivan).contentType(APPLICATION_JSON)
                .content("{\"learnings\":\"x\",\"code\":\"" + TWO_SUM_CODE + "\",\"codeLanguage\":\"PYTHON\"}")));
        long attempt = idAt(done, "$.attempts[0].id");
        long version = idAt(done, "$.attempts[0].versions[0].id");
        jdbc.update("UPDATE attempts SET attempted_on = attempted_on - 1 WHERE id = ?", attempt);   // yesterday

        mvc.perform(patch("/api/attempts/" + attempt).with(csrf()).cookie(ivan).contentType(APPLICATION_JSON)
                        .content("{\"learnings\":\"changed\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Frozen")));
        mvc.perform(post("/api/attempts/" + attempt + "/versions").with(csrf()).cookie(ivan).contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + TWO_SUM_CODE + "\",\"codeLanguage\":\"PYTHON\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/versions/" + version + "/analysis").with(csrf()).cookie(ivan))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/versions/" + version).with(csrf()).cookie(ivan))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/dashboard").cookie(ivan))
                .andExpect(jsonPath("$.catalog[2].progress.attempts[0].editable").value(false));
    }

    @Test
    void nobodyCanTouchSomeoneElsesAttemptsOrVersions() throws Exception {
        Cookie joan = signUp("joan");   // usernames are 3–30 characters
        Cookie kim = signUp("kim");
        String done = body(mvc.perform(post("/api/catalog/3/done").with(csrf()).cookie(joan).contentType(APPLICATION_JSON)
                .content("{\"learnings\":\"x\",\"code\":\"" + TWO_SUM_CODE + "\",\"codeLanguage\":\"PYTHON\"}")));
        long attempt = idAt(done, "$.attempts[0].id");
        long version = idAt(done, "$.attempts[0].versions[0].id");

        mvc.perform(patch("/api/attempts/" + attempt).with(csrf()).cookie(kim).contentType(APPLICATION_JSON)
                .content("{\"learnings\":\"mine now\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/attempts/" + attempt + "/versions").with(csrf()).cookie(kim).contentType(APPLICATION_JSON)
                .content("{\"code\":\"" + TWO_SUM_CODE + "\",\"codeLanguage\":\"PYTHON\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/versions/" + version + "/analysis").with(csrf()).cookie(kim)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/versions/" + version).with(csrf()).cookie(kim)).andExpect(status().isNotFound());
    }

    @Test
    void newProblemsStopOnDay85AndThePlanShowsThePace() throws Exception {
        Cookie nell = signUp("nell");
        markDone(nell, 1);
        mvc.perform(get("/api/dashboard").cookie(nell))
                .andExpect(jsonPath("$.plan.lastNewDayNumber").value(85))
                .andExpect(jsonPath("$.plan.revisions").value(3))
                .andExpect(jsonPath("$.plan.newProblemsOpen").value(true))
                .andExpect(jsonPath("$.pace.left").value(149))
                .andExpect(jsonPath("$.workload.length()").value(14))
                .andExpect(jsonPath("$.workload[3].reviews").value(1));   // Medium: first revision in 3 days

        jdbc.update("UPDATE users SET start_date = CURRENT_DATE - 90 WHERE username = 'nell'");   // now day 91
        mvc.perform(post("/api/catalog/2/done").with(csrf()).cookie(nell).contentType(APPLICATION_JSON)
                        .content("{\"learnings\":\"too late\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("day 85")));
        mvc.perform(get("/api/dashboard").cookie(nell))
                .andExpect(jsonPath("$.plan.newProblemsOpen").value(false));
    }

    @Test
    void codeForAnotherProblemIsRefused() throws Exception {
        Cookie uma = signUp("uma");
        String containsDuplicate = "def containsDuplicate(nums):\\n    return len(set(nums)) < len(nums)\\n";

        // Contains Duplicate code under Two Sum (catalog 3): refused, and no analysis used up
        mvc.perform(post("/api/analysis/preview").with(csrf()).cookie(uma).contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + containsDuplicate + "\",\"codeLanguage\":\"PYTHON\",\"catalogId\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("twoSum")));
        mvc.perform(post("/api/catalog/3/done").with(csrf()).cookie(uma).contentType(APPLICATION_JSON)
                        .content("{\"learnings\":\"Oops.\",\"code\":\"" + containsDuplicate + "\",\"codeLanguage\":\"PYTHON\"}"))
                .andExpect(status().isBadRequest());

        // Under its own problem it's fine
        mvc.perform(post("/api/catalog/1/done").with(csrf()).cookie(uma).contentType(APPLICATION_JSON)
                        .content("{\"learnings\":\"A set.\",\"code\":\"" + containsDuplicate + "\",\"codeLanguage\":\"PYTHON\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void healthCheckIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
