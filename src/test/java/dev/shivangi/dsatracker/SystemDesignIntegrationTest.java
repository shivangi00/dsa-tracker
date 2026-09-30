package dev.shivangi.dsatracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.shivangi.dsatracker.sd.SdCatalog;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The system design tracker end to end: quizzes, designs, versions, the schedule and ownership. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "app.rate-limits-enabled=false")
class SystemDesignIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();
    private final SdCatalog catalog = SdCatalog.load(json);

    private static final String DESIGN = """
            {"rating":"GOOD","sections":{
              "requirements":"Shorten URLs and redirect; 100 million links per day, read heavy",
              "entities":"User, Link(shortCode primary key, longUrl, expiresAt)",
              "api":"POST /links {url, alias?} returns shortCode; GET /{code} gives a 302 redirect",
              "highLevel":"Client to load balancer to stateless services; Redis cache in front of Postgres",
              "deepDives":"Counter with Redis INCR handing out ranges, base62 encoded; links expire with a TTL"}}
            """;

    private Cookie signUp(String username) throws Exception {
        Cookie session = mvc.perform(post("/api/auth/signup").with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"password123\",\"confirmPassword\":\"password123\"}"
                                .formatted(username)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getCookie("SESSION");
        assertNotNull(session);
        return session;
    }

    private JsonNode read(String body) throws Exception {
        return json.readTree(body);
    }

    /** Answers a quiz with {@code right} correct answers (the rest wrong). */
    private String answers(String topicKey, JsonNode quiz, int right) {
        List<SdCatalog.Question> questions = catalog.topic(topicKey).orElseThrow().questions();
        List<Map<String, Object>> out = new ArrayList<>();
        int n = 0;
        for (JsonNode q : quiz.path("questions")) {
            int index = q.path("index").asInt();
            String correct = questions.get(index).answer();
            String choice = correct;
            if (n++ >= right) {
                for (JsonNode o : q.path("options")) {
                    if (!o.asText().equals(correct)) {
                        choice = o.asText();
                        break;
                    }
                }
            }
            out.add(Map.of("index", index, "choice", choice));
        }
        try {
            return json.writeValueAsString(Map.of("answers", out, "notes", "Cache-aside by default"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode quiz(Cookie session, String topic) throws Exception {
        return read(mvc.perform(get("/api/sd/topics/" + topic + "/quiz").cookie(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void dashboardListsEveryTopicAndProblem() throws Exception {
        Cookie s = signUp("sd-ana");
        mvc.perform(get("/api/sd/dashboard").cookie(s))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topics.length()").value(31))
                .andExpect(jsonPath("$.problems.length()").value(32))
                .andExpect(jsonPath("$.stats.topicsStudied").value(0))
                .andExpect(jsonPath("$.firstGaps.PROBLEM.GOOD").value(3));
        mvc.perform(get("/api/due-counts").cookie(s))
                .andExpect(jsonPath("$.dsa").value(0))
                .andExpect(jsonPath("$.systemDesign").value(0));
        mvc.perform(get("/api/sd/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void aQuizMarksATopicStudiedThenOnlyPracticeUntilItsDue() throws Exception {
        Cookie s = signUp("sd-ben");
        JsonNode q = quiz(s, "caching");
        assertEquals("FIRST", q.path("mode").asText());
        assertEquals(5, q.path("questions").size());

        JsonNode r = read(mvc.perform(post("/api/sd/topics/caching/quiz").with(csrf()).cookie(s)
                        .contentType(APPLICATION_JSON).content(answers("caching", q, 4)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(4, r.path("correct").asInt());
        assertEquals("GOOD", r.path("rating").asText());
        assertEquals(true, r.path("saved").asBoolean());
        assertEquals(3, jdbc.queryForObject("SELECT interval_days FROM sd_items WHERE item_key = 'caching'", Integer.class));

        JsonNode practice = quiz(s, "caching");
        assertEquals("PRACTICE", practice.path("mode").asText());
        mvc.perform(post("/api/sd/topics/caching/quiz").with(csrf()).cookie(s)
                        .contentType(APPLICATION_JSON).content(answers("caching", practice, 5)))
                .andExpect(jsonPath("$.saved").value(false));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM sd_attempts a JOIN sd_items i ON i.id = a.item_id "
                + "JOIN users u ON u.id = i.user_id WHERE u.username = 'sd-ben'", Integer.class));

        // Due: the next quiz is revision 1; under 50% repeats it.
        jdbc.update("UPDATE sd_items SET next_due_on = CURRENT_DATE WHERE item_key = 'caching'");
        mvc.perform(get("/api/due-counts").cookie(s)).andExpect(jsonPath("$.systemDesign").value(1));
        JsonNode review = quiz(s, "caching");
        assertEquals("REVIEW", review.path("mode").asText());
        mvc.perform(post("/api/sd/topics/caching/quiz").with(csrf()).cookie(s)
                        .contentType(APPLICATION_JSON).content(answers("caching", review, 2)))
                .andExpect(jsonPath("$.rating").value("AGAIN"))
                .andExpect(jsonPath("$.item.revisionsDone").value(0));
    }

    @Test
    void incompleteQuizzesAreRefused() throws Exception {
        Cookie s = signUp("sd-cat");
        mvc.perform(post("/api/sd/topics/caching/quiz").with(csrf()).cookie(s)
                        .contentType(APPLICATION_JSON).content("{\"answers\":[{\"index\":0,\"choice\":\"x\"}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/sd/topics/no-such-topic/quiz").cookie(s)).andExpect(status().isNotFound());
    }

    @Test
    void aDesignIsScoredKeptInVersionsAndRevisedTwice() throws Exception {
        Cookie s = signUp("sd-dan");
        JsonNode item = read(mvc.perform(post("/api/sd/problems/bitly/done").with(csrf()).cookie(s)
                        .contentType(APPLICATION_JSON).content(DESIGN))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long itemId = item.path("id").asLong();
        long attemptId = item.path("attempts").get(0).path("id").asLong();
        double score = item.path("attempts").get(0).path("answers").get(0).path("score").asDouble();
        assertEquals(true, score >= 7.0, "a decent design should cover most key points: " + score);

        mvc.perform(post("/api/sd/problems/bitly/done").with(csrf()).cookie(s)
                .contentType(APPLICATION_JSON).content(DESIGN)).andExpect(status().isConflict());

        mvc.perform(post("/api/sd/attempts/" + attemptId + "/answers").with(csrf()).cookie(s)
                        .contentType(APPLICATION_JSON).content("{\"sections\":{\"deepDives\":\"custom alias; 301 vs 302\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attempts[0].answers.length()").value(2));

        mvc.perform(patch("/api/sd/attempts/" + attemptId).with(csrf()).cookie(s)
                        .contentType(APPLICATION_JSON).content("{\"notes\":\"remember ID ranges\"}"))
                .andExpect(jsonPath("$.attempts[0].notes").value("remember ID ranges"));

        // Not due yet
        mvc.perform(post("/api/sd/items/" + itemId + "/reviews").with(csrf()).cookie(s)
                .contentType(APPLICATION_JSON).content("{\"rating\":\"GOOD\"}")).andExpect(status().isConflict());

        // Yesterday's attempt is frozen
        jdbc.update("UPDATE sd_attempts SET attempted_on = attempted_on - 1 WHERE id = ?", attemptId);
        mvc.perform(patch("/api/sd/attempts/" + attemptId).with(csrf()).cookie(s)
                .contentType(APPLICATION_JSON).content("{\"notes\":\"late\"}")).andExpect(status().isConflict());

        jdbc.update("UPDATE sd_items SET next_due_on = CURRENT_DATE WHERE id = ?", itemId);
        mvc.perform(post("/api/sd/items/" + itemId + "/reviews").with(csrf()).cookie(s)
                        .contentType(APPLICATION_JSON).content(DESIGN))
                .andExpect(jsonPath("$.revisionsDone").value(1));
        jdbc.update("UPDATE sd_items SET next_due_on = CURRENT_DATE WHERE id = ?", itemId);
        mvc.perform(post("/api/sd/items/" + itemId + "/reviews").with(csrf()).cookie(s)
                        .contentType(APPLICATION_JSON).content("{\"rating\":\"EASY\"}"))
                .andExpect(jsonPath("$.revisionsDone").value(2))
                .andExpect(jsonPath("$.fullyRevised").value(true));
    }

    @Test
    void previewScoresWithoutSaving() throws Exception {
        Cookie s = signUp("sd-eve");
        mvc.perform(post("/api/sd/score").with(csrf()).cookie(s).contentType(APPLICATION_JSON)
                        .content("{\"problemKey\":\"bitly\",\"sections\":{\"highLevel\":\"base62 counter, Redis cache\"}}"))
                .andExpect(jsonPath("$.scored").value(true))
                .andExpect(jsonPath("$.hits.length()").value(2));
        mvc.perform(post("/api/sd/score").with(csrf()).cookie(s).contentType(APPLICATION_JSON)
                        .content("{\"problemKey\":\"uber\",\"sections\":{\"highLevel\":\"match riders\"}}"))
                .andExpect(jsonPath("$.scored").value(true))
                .andExpect(jsonPath("$.hits.length()").value(0));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM sd_items i JOIN users u ON u.id = i.user_id WHERE u.username = 'sd-eve'", Integer.class));
    }

    @Test
    void usersCanNeverSeeOrChangeEachOthersDesigns() throws Exception {
        Cookie fay = signUp("sd-fay");
        Cookie gus = signUp("sd-gus");
        JsonNode item = read(mvc.perform(post("/api/sd/problems/dropbox/done").with(csrf()).cookie(fay)
                        .contentType(APPLICATION_JSON).content("{\"rating\":\"HARD\",\"notes\":\"chunks + presigned URLs\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long itemId = item.path("id").asLong();
        long attemptId = item.path("attempts").get(0).path("id").asLong();

        mvc.perform(get("/api/sd/items/" + itemId).cookie(gus)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/sd/attempts/" + attemptId).with(csrf()).cookie(gus)
                .contentType(APPLICATION_JSON).content("{\"notes\":\"mine now\"}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/sd/items/" + itemId).with(csrf()).cookie(gus)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/sd/items/" + itemId).with(csrf()).cookie(fay)).andExpect(status().isNoContent());
    }
}
