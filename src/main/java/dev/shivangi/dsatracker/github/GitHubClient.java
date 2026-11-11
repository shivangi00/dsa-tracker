package dev.shivangi.dsatracker.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Talks to the GitHub REST API with your token. Everything for one push goes into a single commit
 * (Git Data API: tree → commit → move the branch), and only files whose content changed are sent.
 */
public class GitHubClient {

    /** A repository, as GitHub reports it. */
    public record Repo(String fullName, String defaultBranch, boolean isPrivate, String htmlUrl, boolean empty) {
    }

    /**
     * @param changed false when the repository already had exactly these files (nothing committed)
     * @param commitUrl the new commit's page, or null when nothing changed
     */
    public record PushResult(boolean changed, String commitUrl, int filesWritten, int filesDeleted) {
    }

    /** A GitHub error, with a message written for you rather than for a developer. */
    public static class GitHubException extends RuntimeException {
        private final int status;

        public GitHubException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    private final HttpClient http;
    private final ObjectMapper json;
    private final String apiUrl;

    public GitHubClient(ObjectMapper json, String apiUrl) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), json, apiUrl);
    }

    GitHubClient(HttpClient http, ObjectMapper json, String apiUrl) {
        this.http = http;
        this.json = json;
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
    }

    /** Checks the token can see the repository; tells you the default branch and whether it's private. */
    public Repo repo(String owner, String name, String token) {
        JsonNode r = call("GET", "/repos/" + enc(owner) + "/" + enc(name), token, null);
        // An empty repository has no commits: its branch ref doesn't exist yet.
        boolean empty = r.path("size").asInt(0) == 0 && refSha(owner, name, r.path("default_branch").asText("main"), token) == null;
        return new Repo(r.path("full_name").asText(owner + "/" + name), r.path("default_branch").asText("main"),
                r.path("private").asBoolean(true), r.path("html_url").asText(), empty);
    }

    /**
     * Makes the repository hold exactly {@code files} at those paths, and removes any other file
     * under {@code ownedFolders} (e.g. a code version you deleted in the tracker). Everything else in
     * the repository is left alone. One commit; nothing at all if nothing changed.
     */
    public PushResult push(String owner, String name, String branch, String token, Map<String, String> files,
                           Set<String> ownedFolders, String message) {
        try {
            return pushOnce(owner, name, branch, token, files, ownedFolders, message);
        } catch (GitHubException e) {
            // Someone else moved the branch in between (another push, an edit on github.com): try once more.
            if (e.status() == 422 && e.getMessage().contains("fast forward")) {
                return pushOnce(owner, name, branch, token, files, ownedFolders, message);
            }
            throw e;
        }
    }

    private PushResult pushOnce(String owner, String name, String branch, String token, Map<String, String> files,
                                Set<String> ownedFolders, String message) {
        String repoPath = "/repos/" + enc(owner) + "/" + enc(name);
        String head = refSha(owner, name, branch, token);
        if (head == null) {
            // A brand-new, empty repository: the Git Data API needs a first commit to build on.
            bootstrap(repoPath, branch, token, files.getOrDefault(RepoLayout.ROOT_README, "# DSA tracker\n"));
            head = refSha(owner, name, branch, token);
            if (head == null) {
                throw new GitHubException(404, "Branch \"" + branch + "\" doesn't exist in " + owner + "/" + name
                        + ". Check the branch name in Settings, or leave it empty to use the default branch.");
            }
        }
        String baseTree = call("GET", repoPath + "/git/commits/" + head, token, null).path("tree").path("sha").asText();
        JsonNode tree = call("GET", repoPath + "/git/trees/" + baseTree + "?recursive=1", token, null);
        Map<String, String> existing = new HashMap<>();
        for (JsonNode e : tree.path("tree")) {
            if ("blob".equals(e.path("type").asText())) {
                existing.put(e.path("path").asText(), e.path("sha").asText());
            }
        }
        boolean complete = !tree.path("truncated").asBoolean(false);

        ArrayNode entries = json.createArrayNode();
        int written = 0;
        for (Map.Entry<String, String> f : files.entrySet()) {
            if (!blobSha(f.getValue()).equals(existing.get(f.getKey()))) {
                ObjectNode e = entries.addObject();
                e.put("path", f.getKey()).put("mode", "100644").put("type", "blob").put("content", f.getValue());
                written++;
            }
        }
        int deleted = 0;
        if (complete) {   // a truncated listing (huge repo) can't tell us what to remove, so remove nothing
            for (String path : existing.keySet()) {
                if (!files.containsKey(path) && ownedFolders.stream().anyMatch(dir -> path.startsWith(dir + "/"))) {
                    ObjectNode e = entries.addObject();
                    e.put("path", path).put("mode", "100644").put("type", "blob").putNull("sha");
                    deleted++;
                }
            }
        }
        if (entries.isEmpty()) {
            return new PushResult(false, null, 0, 0);
        }
        ObjectNode treeBody = json.createObjectNode().put("base_tree", baseTree);
        treeBody.set("tree", entries);
        String newTree = call("POST", repoPath + "/git/trees", token, treeBody).path("sha").asText();
        ObjectNode commitBody = json.createObjectNode().put("message", message).put("tree", newTree);
        commitBody.putArray("parents").add(head);
        JsonNode commit = call("POST", repoPath + "/git/commits", token, commitBody);
        call("PATCH", repoPath + "/git/refs/heads/" + encPath(branch), token,
                json.createObjectNode().put("sha", commit.path("sha").asText()).put("force", false));
        return new PushResult(true, commit.path("html_url").asText(null), written, deleted);
    }

    /** The commit a branch points at, or null if the branch (or any commit) doesn't exist yet. */
    String refSha(String owner, String name, String branch, String token) {
        try {
            JsonNode ref = call("GET", "/repos/" + enc(owner) + "/" + enc(name) + "/git/ref/heads/" + encPath(branch), token, null);
            return ref.path("object").path("sha").asText(null);
        } catch (GitHubException e) {
            if (e.status() == 404 || e.status() == 409) {   // 409: "Git Repository is empty"
                return null;
            }
            throw e;
        }
    }

    private void bootstrap(String repoPath, String branch, String token, String readme) {
        ObjectNode body = json.createObjectNode()
                .put("message", "Start my DSA tracker repository")
                .put("content", Base64.getEncoder().encodeToString(readme.getBytes(StandardCharsets.UTF_8)))
                .put("branch", branch);
        try {
            call("PUT", repoPath + "/contents/README.md", token, body);
        } catch (GitHubException e) {
            if (e.status() != 422) {   // 422: the file exists, so the repository isn't empty after all
                throw e;
            }
        }
    }

    /** Git's own id for a file's content: SHA-1 of "blob {length}\0{bytes}". Lets us skip unchanged files. */
    static String blobSha(String content) {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(("blob " + body.length + "\0").getBytes(StandardCharsets.UTF_8));
            sha1.update(body);
            return HexFormat.of().formatHex(sha1.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode call(String method, String path, String token, JsonNode body) {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(apiUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "dsa-tracker");
        try {
            if (body == null) {
                req.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                req.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            }
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode parsed = res.body() == null || res.body().isBlank() ? json.createObjectNode() : json.readTree(res.body());
            if (res.statusCode() >= 300) {
                throw new GitHubException(res.statusCode(), explain(res.statusCode(), parsed.path("message").asText(""), res));
            }
            return parsed;
        } catch (IOException e) {
            throw new GitHubException(502, "Couldn't reach GitHub. Try again in a minute.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubException(502, "The push was interrupted. Try again.");
        }
    }

    private static String explain(int status, String githubMessage, HttpResponse<String> res) {
        return switch (status) {
            case 401 -> "GitHub didn't accept the token (it may have expired or been revoked). Connect again with a new token.";
            case 403 -> res.headers().firstValue("x-ratelimit-remaining").filter("0"::equals).isPresent()
                    ? "GitHub's rate limit was reached. Try again in an hour."
                    : "The token can't write to this repository. Give it \"Contents: Read and write\" access to it.";
            case 404 -> "Repository not found. Check the name, and that the token has access to it.";
            case 409 -> "GitHub says: " + githubMessage;
            case 422 -> githubMessage.toLowerCase().contains("fast forward")
                    ? "fast forward: the branch moved while pushing" : "GitHub refused the change: " + githubMessage;
            default -> "GitHub answered " + status + (githubMessage.isEmpty() ? "" : ": " + githubMessage);
        };
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Encodes each part of a slash-separated path (branch names can contain slashes). */
    private static String encPath(String s) {
        List<String> parts = new ArrayList<>();
        for (String p : s.split("/")) {
            parts.add(enc(p));
        }
        return String.join("/", parts);
    }
}
