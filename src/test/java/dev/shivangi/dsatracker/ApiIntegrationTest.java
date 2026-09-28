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

    /** Marks NeetCode problem {@code catalogId} done and returns the new problem's id. */
    private long markDone(Cookie session, int catalogId) throws Exception {
        String body = mvc.perform(post("/api/catalog/" + catalogId + "/done").with(csrf()).cookie(session)
                        .contentType(APPLICATION_JSON)
                        .content("{\"learnings\":\"Use a hash map of value to index.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.intervalDays").value(1))
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
    void aProblemCanBeMarkedDoneOnceAndItsReviewIsDueTomorrow() throws Exception {
        Cookie bob = signUp("bob");
        long id = markDone(bob, 3);

        mvc.perform(post("/api/catalog/3/done").with(csrf()).cookie(bob).contentType(APPLICATION_JSON)
                        .content("{\"learnings\":\"again\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/problems/" + id + "/reviews").with(csrf()).cookie(bob).contentType(APPLICATION_JSON)
                        .content("{\"remembered\":true}"))
                .andExpect(status().isConflict());   // not due until tomorrow
        mvc.perform(get("/api/dashboard").cookie(bob))
                .andExpect(jsonPath("$.consistency.activeToday").value(true))
                .andExpect(jsonPath("$.consistency.totalStudyDays").value(1));
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

    // ---------- notes, code and analysis

    private static final String TWO_SUM = """
            {"learnings":"Hash map of value to index.","excalidrawUrl":"",
             "code":"def twoSum(nums, target):\\n    seen = {}\\n    for i, n in enumerate(nums):\\n        if target - n in seen:\\n            return [seen[target - n], i]\\n        seen[n] = i\\n",
             "codeLanguage":"PYTHON"}""";

    @Test
    void notesAndCodeCanBeEditedAndAnalysedOnTheDaySolved() throws Exception {
        Cookie hana = signUp("hana");
        long id = markDone(hana, 1);

        mvc.perform(patch("/api/problems/" + id + "/notes").with(csrf()).cookie(hana)
                        .contentType(APPLICATION_JSON).content(TWO_SUM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.codeLanguage").value("PYTHON"))
                .andExpect(jsonPath("$.analysis").doesNotExist());

        mvc.perform(post("/api/problems/" + id + "/analysis").with(csrf()).cookie(hana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis.time").value("O(n)"))
                .andExpect(jsonPath("$.analysis.space").value("O(n)"))
                .andExpect(jsonPath("$.analysis.source").value("estimate"));

        // Changing the code clears the analysis of the old code
        mvc.perform(patch("/api/problems/" + id + "/notes").with(csrf()).cookie(hana)
                        .contentType(APPLICATION_JSON).content(TWO_SUM.replace("seen[n] = i", "seen[n] = i  # done")))
                .andExpect(jsonPath("$.analysis").doesNotExist());
    }

    @Test
    void codeCanBeAnalysedAndSavedInTheMarkDoneWindow() throws Exception {
        Cookie pia = signUp("pia");
        String code = "def f(nums):\\n    for x in nums:\\n        for y in nums:\\n            pass\\n";

        // Preview: analysed, nothing saved
        mvc.perform(post("/api/analysis/preview").with(csrf()).cookie(pia).contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"codeLanguage\":\"PYTHON\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.time").value("O(n²)"));
        mvc.perform(get("/api/dashboard").cookie(pia)).andExpect(jsonPath("$.counts.done").value(0));

        // Mark done with the code: saved together with its analysis
        mvc.perform(post("/api/catalog/1/done").with(csrf()).cookie(pia).contentType(APPLICATION_JSON)
                        .content("{\"learnings\":\"Nested loops.\",\"code\":\"" + code + "\",\"codeLanguage\":\"PYTHON\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.analysis.time").value("O(n²)"));
    }

    @Test
    void analysisRecommendsABetterApproach() throws Exception {
        Cookie ria = signUp("ria");
        String brute = "def f(nums):\\n    for i in range(len(nums)):\\n        for j in range(i + 1, len(nums)):\\n"
                + "            if nums[i] == nums[j]:\\n                return True\\n    return False\\n";

        // Preview for Contains Duplicate (catalog id 1): O(n²) → the hash set approach
        mvc.perform(post("/api/analysis/preview").with(csrf()).cookie(ria).contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + brute + "\",\"codeLanguage\":\"PYTHON\",\"catalogId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendation.verdict").value("faster"))
                .andExpect(jsonPath("$.recommendation.approach.name").value("Hash set"))
                .andExpect(jsonPath("$.recommendation.approach.time").value("O(n)"));

        // Without a catalog id there's nothing to compare with
        mvc.perform(post("/api/analysis/preview").with(csrf()).cookie(ria).contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + brute + "\",\"codeLanguage\":\"PYTHON\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendation").doesNotExist());

        // Saved: the problem's analysis carries the recommendation, on the dashboard too
        mvc.perform(post("/api/catalog/1/done").with(csrf()).cookie(ria).contentType(APPLICATION_JSON)
                        .content("{\"learnings\":\"Brute force first.\",\"code\":\"" + brute + "\",\"codeLanguage\":\"PYTHON\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.analysis.recommendation.verdict").value("faster"));
        mvc.perform(get("/api/dashboard").cookie(ria))
                .andExpect(jsonPath("$.catalog[0].progress.analysis.recommendation.approach.name").value("Hash set"));
    }

    @Test
    void notesAreFrozenOnceTheDayIsOver() throws Exception {
        Cookie ivan = signUp("ivan");
        long id = markDone(ivan, 1);
        // Pretend it was solved yesterday
        jdbc.update("UPDATE problems SET solved_on = solved_on - 1, last_reviewed_on = last_reviewed_on - 1, "
                + "next_due_on = next_due_on - 1 WHERE id = ?", id);

        mvc.perform(patch("/api/problems/" + id + "/notes").with(csrf()).cookie(ivan)
                        .contentType(APPLICATION_JSON).content(TWO_SUM))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("frozen")));
        mvc.perform(post("/api/problems/" + id + "/analysis").with(csrf()).cookie(ivan))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/dashboard").cookie(ivan))
                .andExpect(jsonPath("$.catalog[0].progress.editable").value(false));
    }

    @Test
    void analysingNeedsCodeAndOnlyYourOwnProblem() throws Exception {
        Cookie joan = signUp("joan");   // usernames are 3–30 characters
        Cookie kim = signUp("kim");
        long id = markDone(joan, 1);
        mvc.perform(post("/api/problems/" + id + "/analysis").with(csrf()).cookie(joan))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/problems/" + id + "/analysis").with(csrf()).cookie(kim))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/problems/" + id + "/notes").with(csrf()).cookie(kim)
                        .contentType(APPLICATION_JSON).content(TWO_SUM))
                .andExpect(status().isNotFound());
    }

    @Test
    void healthCheckIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
