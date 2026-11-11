package dev.shivangi.dsatracker.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pushing against a stand-in GitHub: one commit per push, unchanged files skipped, nothing else touched. */
class GitHubClientTest {

    private static final String FOLDER = "01 Arrays & Hashing/001 Contains Duplicate";

    private static Map<String, String> files(String... pathsAndContents) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < pathsAndContents.length; i += 2) {
            m.put(pathsAndContents[i], pathsAndContents[i + 1]);
        }
        return m;
    }

    @Test
    void pushesIntoAnEmptyRepositoryThenSkipsUnchangedFiles() throws Exception {
        try (FakeGitHub gh = new FakeGitHub()) {
            GitHubClient client = new GitHubClient(new ObjectMapper(), gh.url());
            GitHubClient.Repo repo = client.repo(gh.owner, gh.repo, gh.token);
            assertTrue(repo.empty());
            assertEquals("main", repo.defaultBranch());

            Map<String, String> v1 = files("README.md", "# index\n", FOLDER + "/README.md", "# Contains Duplicate\n",
                    FOLDER + "/Solved/notes.md", "notes\n", FOLDER + "/Solved/v1.java", "class Solution {}\n");
            GitHubClient.PushResult first = client.push(gh.owner, gh.repo, "main", gh.token, v1, Set.of(FOLDER), "Contains Duplicate: solved");
            assertTrue(first.changed());
            assertEquals(v1, gh.files());
            assertEquals("Contains Duplicate: solved", gh.commitMessages.get(gh.commitMessages.size() - 1));

            // The same files again: nothing to commit.
            int commitsBefore = gh.commitMessages.size();
            GitHubClient.PushResult again = client.push(gh.owner, gh.repo, "main", gh.token, v1, Set.of(FOLDER), "again");
            assertFalse(again.changed());
            assertEquals(commitsBefore, gh.commitMessages.size());
        }
    }

    @Test
    void onlyChangedFilesAreSentAndRemovedOnesAreDeletedOnlyInsideTheProblemsFolder() throws Exception {
        try (FakeGitHub gh = new FakeGitHub()) {
            GitHubClient client = new GitHubClient(new ObjectMapper(), gh.url());
            client.push(gh.owner, gh.repo, "main", gh.token, files("README.md", "# index\n",
                    FOLDER + "/Solved/v1.java", "a\n", FOLDER + "/Solved/v2.java", "b\n"), Set.of(FOLDER), "first");
            gh.commitDirectly("my-own-notes.md", "keep me\n");                  // your own file: left alone
            gh.commitDirectly("02 Two Pointers/x/Solved/v1.py", "other\n");     // another problem: left alone

            // Version 2 was deleted in the tracker; version 1 changed.
            GitHubClient.PushResult r = client.push(gh.owner, gh.repo, "main", gh.token,
                    files("README.md", "# index\n", FOLDER + "/Solved/v1.java", "a2\n"), Set.of(FOLDER), "update");
            assertEquals(1, r.filesWritten());
            assertEquals(1, r.filesDeleted());
            assertEquals("a2\n", gh.files().get(FOLDER + "/Solved/v1.java"));
            assertFalse(gh.files().containsKey(FOLDER + "/Solved/v2.java"));
            assertEquals("keep me\n", gh.files().get("my-own-notes.md"));
            assertEquals("other\n", gh.files().get("02 Two Pointers/x/Solved/v1.py"));
        }
    }

    @Test
    void aFolderWithASimilarNameIsNotTreatedAsOurs() throws Exception {
        try (FakeGitHub gh = new FakeGitHub()) {
            GitHubClient client = new GitHubClient(new ObjectMapper(), gh.url());
            client.push(gh.owner, gh.repo, "main", gh.token, files(FOLDER + "/Solved/v1.java", "a\n"), Set.of(FOLDER), "first");
            gh.commitDirectly(FOLDER + " II/Solved/v1.java", "sibling\n");
            client.push(gh.owner, gh.repo, "main", gh.token, files(FOLDER + "/Solved/v1.java", "b\n"), Set.of(FOLDER), "second");
            assertEquals("sibling\n", gh.files().get(FOLDER + " II/Solved/v1.java"));
        }
    }

    @Test
    void errorsAreExplainedInPlainWords() throws Exception {
        try (FakeGitHub gh = new FakeGitHub()) {
            GitHubClient client = new GitHubClient(new ObjectMapper(), gh.url());
            GitHubClient.GitHubException bad = assertThrows(GitHubClient.GitHubException.class,
                    () -> client.repo(gh.owner, gh.repo, "wrong-token"));
            assertEquals(401, bad.status());
            assertTrue(bad.getMessage().contains("token"), bad.getMessage());
            GitHubClient.GitHubException missing = assertThrows(GitHubClient.GitHubException.class,
                    () -> client.repo(gh.owner, "no-such-repo", gh.token));
            assertTrue(missing.getMessage().startsWith("Repository not found"), missing.getMessage());
            client.push(gh.owner, gh.repo, "main", gh.token, files("README.md", "x\n"), Set.of(), "first");
            GitHubClient.GitHubException branch = assertThrows(GitHubClient.GitHubException.class,
                    () -> client.push(gh.owner, gh.repo, "no-such-branch", gh.token, files("README.md", "y\n"), Set.of(), "m"));
            assertTrue(branch.getMessage().contains("no-such-branch"), branch.getMessage());
        }
    }

    @Test
    void blobShaMatchesGit() {
        // `printf 'hello\n' | git hash-object --stdin`
        assertEquals("ce013625030ba8dba906f756967f9e9ca394464a", GitHubClient.blobSha("hello\n"));
    }
}
