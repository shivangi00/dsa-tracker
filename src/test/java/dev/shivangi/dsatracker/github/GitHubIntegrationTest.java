package dev.shivangi.dsatracker.github;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Linking a repository and pushing, end to end: the real app and database, a stand-in GitHub. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class GitHubIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static final FakeGitHub GITHUB;

    static {
        try {
            GITHUB = new FakeGitHub();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void github(DynamicPropertyRegistry r) {
        r.add("app.rate-limits-enabled", () -> "false");
        r.add("app.github.api-url", GITHUB::url);
        r.add("app.github.token-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
    }

    @AfterAll
    static void stop() {
        GITHUB.close();
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private Cookie signUp(String username) throws Exception {
        return mvc.perform(post("/api/auth/signup").with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"password123\",\"confirmPassword\":\"password123\"}".formatted(username)))
                .andExpect(status().isCreated()).andReturn().getResponse().getCookie("SESSION");
    }

    private long markDone(Cookie s, int catalogId) throws Exception {
        String body = mvc.perform(post("/api/catalog/" + catalogId + "/done").with(csrf()).cookie(s).contentType(APPLICATION_JSON)
                        .content("{\"learnings\":\"Use a set.\",\"code\":\"class Solution { boolean containsDuplicate(int[] nums) { return false; } }\",\"codeLanguage\":\"JAVA\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String connectBody(String repo, String token) {
        return "{\"repository\":\"%s\",\"token\":\"%s\"}".formatted(repo, token);
    }

    @Test
    void linkPushThenPushAgainWithoutChanges() throws Exception {
        Cookie s = signUp("gh-ada");
        long problem = markDone(s, 1);

        mvc.perform(get("/api/github").cookie(s))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.connected").value(false));
        mvc.perform(post("/api/github/problems/" + problem + "/push").with(csrf()).cookie(s)).andExpect(status().isConflict());

        mvc.perform(put("/api/github").with(csrf()).cookie(s).contentType(APPLICATION_JSON).content(connectBody("nell/neetcode", "wrong")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/github").with(csrf()).cookie(s).contentType(APPLICATION_JSON).content(connectBody("not a repo", GITHUB.token)))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/github").with(csrf()).cookie(s).contentType(APPLICATION_JSON)
                        .content(connectBody("https://github.com/nell/neetcode.git", GITHUB.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repository").value("nell/neetcode"))
                .andExpect(jsonPath("$.branch").value("main"))
                .andExpect(jsonPath("$.isPrivate").value(true));

        // The token is stored encrypted, never as typed, and never sent back.
        String stored = jdbc.queryForObject("SELECT token_encrypted FROM github_links", String.class);
        assertFalse(stored.contains(GITHUB.token));
        String status = mvc.perform(get("/api/github").cookie(s)).andReturn().getResponse().getContentAsString();
        assertFalse(status.contains(GITHUB.token));

        mvc.perform(post("/api/github/problems/" + problem + "/push").with(csrf()).cookie(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(true));
        assertTrue(GITHUB.files().containsKey("01 Arrays & Hashing/001 Contains Duplicate/Solved/v1.java"));
        assertTrue(GITHUB.files().get("README.md").contains("Contains Duplicate"));
        mvc.perform(get("/api/github").cookie(s))
                .andExpect(jsonPath("$.problems['" + problem + "'].upToDate").value(true));

        int commits = GITHUB.commitMessages.size();
        mvc.perform(post("/api/github/problems/" + problem + "/push").with(csrf()).cookie(s))
                .andExpect(jsonPath("$.changed").value(false));
        assertEquals(commits, GITHUB.commitMessages.size());

        // Editing today's notes makes it "changes not pushed".
        long attempt = jdbc.queryForObject("SELECT id FROM attempts WHERE problem_id = ?", Long.class, problem);
        mvc.perform(patch("/api/attempts/" + attempt).with(csrf()).cookie(s).contentType(APPLICATION_JSON)
                .content("{\"learnings\":\"Use a set: O(n) time.\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/github").cookie(s))
                .andExpect(jsonPath("$.problems['" + problem + "'].upToDate").value(false));

        mvc.perform(post("/api/github/push-all").with(csrf()).cookie(s)).andExpect(jsonPath("$.changed").value(true));
        assertTrue(GITHUB.files().get("01 Arrays & Hashing/001 Contains Duplicate/Solved/notes.md").contains("O(n) time"));

        mvc.perform(delete("/api/github").with(csrf()).cookie(s)).andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM github_links", Integer.class));
    }

    @Test
    void youCanOnlyPushYourOwnProblems() throws Exception {
        Cookie bo = signUp("gh-bo");
        Cookie cy = signUp("gh-cy");
        long bosProblem = markDone(bo, 1);
        mvc.perform(put("/api/github").with(csrf()).cookie(cy).contentType(APPLICATION_JSON).content(connectBody("nell/neetcode", GITHUB.token)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/github/problems/" + bosProblem + "/push").with(csrf()).cookie(cy)).andExpect(status().isNotFound());
    }
}
