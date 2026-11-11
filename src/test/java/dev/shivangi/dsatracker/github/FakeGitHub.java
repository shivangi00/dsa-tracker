package dev.shivangi.dsatracker.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A tiny stand-in for the parts of GitHub's REST API that {@link GitHubClient} uses: one repository,
 * one branch, commits holding a full snapshot of files. Enough to test pushing end to end.
 */
final class FakeGitHub implements AutoCloseable {

    final String token = "github_pat_test";
    final String owner = "nell";
    final String repo = "neetcode";
    final String branch = "main";
    final List<String> commitMessages = new ArrayList<>();
    final List<String> requests = new ArrayList<>();
    /** commit sha → files (path → content) */
    private final Map<String, Map<String, String>> commits = new HashMap<>();
    private final Map<String, Map<String, String>> trees = new HashMap<>();
    private final Map<String, String> commitTree = new HashMap<>();
    private String head;   // null: an empty repository
    private final ObjectMapper json = new ObjectMapper();
    private final HttpServer server;

    FakeGitHub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** The files on the branch now. */
    Map<String, String> files() {
        return head == null ? Map.of() : commits.get(head);
    }

    /** Someone edits the repository on github.com. */
    void commitDirectly(String path, String content) {
        Map<String, String> next = new TreeMap<>(files());
        next.put(path, content);
        newCommit(next, "edited on github.com");
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private String newCommit(Map<String, String> files, String message) {
        String tree = UUID.randomUUID().toString();
        trees.put(tree, new TreeMap<>(files));
        String sha = UUID.randomUUID().toString();
        commits.put(sha, new TreeMap<>(files));
        commitTree.put(sha, tree);
        head = sha;
        commitMessages.add(message);
        return sha;
    }

    private void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = URLDecoder.decode(ex.getRequestURI().getRawPath(), StandardCharsets.UTF_8);
        String query = ex.getRequestURI().getQuery();
        requests.add(method + " " + path);
        byte[] in = ex.getRequestBody().readAllBytes();
        JsonNode body = in.length == 0 ? null : json.readTree(in);
        if (!("Bearer " + token).equals(ex.getRequestHeaders().getFirst("Authorization"))) {
            reply(ex, 401, json.createObjectNode().put("message", "Bad credentials"));
            return;
        }
        String base = "/repos/" + owner + "/" + repo;
        if (!path.startsWith(base)) {
            reply(ex, 404, json.createObjectNode().put("message", "Not Found"));
            return;
        }
        String rest = path.substring(base.length());
        ObjectNode out = json.createObjectNode();
        switch (method + " " + (rest.isEmpty() ? "/" : rest.replaceAll("/[^/]+$", "/*"))) {
            default -> {
                if (method.equals("GET") && rest.isEmpty()) {
                    out.put("full_name", owner + "/" + repo).put("default_branch", branch).put("private", true)
                            .put("html_url", "https://github.com/" + owner + "/" + repo).put("size", head == null ? 0 : 1);
                    reply(ex, 200, out);
                } else if (method.equals("GET") && rest.equals("/git/ref/heads/" + branch)) {
                    if (head == null) {
                        reply(ex, 409, out.put("message", "Git Repository is empty."));
                    } else {
                        out.putObject("object").put("sha", head);
                        reply(ex, 200, out);
                    }
                } else if (method.equals("GET") && rest.startsWith("/git/ref/heads/")) {
                    reply(ex, 404, out.put("message", "Not Found"));
                } else if (method.equals("GET") && rest.startsWith("/git/commits/")) {
                    String sha = rest.substring("/git/commits/".length());
                    out.putObject("tree").put("sha", commitTree.get(sha));
                    reply(ex, 200, out);
                } else if (method.equals("GET") && rest.startsWith("/git/trees/")) {
                    Map<String, String> t = trees.get(rest.substring("/git/trees/".length()));
                    ArrayNode arr = out.putArray("tree");
                    for (Map.Entry<String, String> e : t.entrySet()) {
                        arr.addObject().put("path", e.getKey()).put("type", "blob").put("sha", GitHubClient.blobSha(e.getValue()));
                    }
                    out.put("truncated", false);
                    reply(ex, 200, out);
                } else if (method.equals("PUT") && rest.startsWith("/contents/")) {
                    if (head != null) {
                        reply(ex, 422, out.put("message", "sha wasn't supplied"));
                        return;
                    }
                    String file = rest.substring("/contents/".length());
                    newCommit(Map.of(file, new String(Base64.getDecoder().decode(body.path("content").asText()), StandardCharsets.UTF_8)),
                            body.path("message").asText());
                    reply(ex, 201, out);
                } else if (method.equals("POST") && rest.equals("/git/trees")) {
                    Map<String, String> t = new TreeMap<>(trees.get(body.path("base_tree").asText()));
                    for (JsonNode e : body.path("tree")) {
                        if (e.has("sha") && e.get("sha").isNull()) {
                            t.remove(e.path("path").asText());
                        } else {
                            t.put(e.path("path").asText(), e.path("content").asText());
                        }
                    }
                    String sha = UUID.randomUUID().toString();
                    trees.put(sha, t);
                    reply(ex, 201, out.put("sha", sha));
                } else if (method.equals("POST") && rest.equals("/git/commits")) {
                    String sha = UUID.randomUUID().toString();
                    commits.put(sha, trees.get(body.path("tree").asText()));
                    commitTree.put(sha, body.path("tree").asText());
                    pendingParent.put(sha, body.path("parents").get(0).asText());
                    pendingMessage.put(sha, body.path("message").asText());
                    reply(ex, 201, out.put("sha", sha).put("html_url", "https://github.com/" + owner + "/" + repo + "/commit/" + sha));
                } else if (method.equals("PATCH") && rest.equals("/git/refs/heads/" + branch)) {
                    String sha = body.path("sha").asText();
                    if (!head.equals(pendingParent.get(sha))) {
                        reply(ex, 422, out.put("message", "Update is not a fast forward"));
                        return;
                    }
                    head = sha;
                    commitMessages.add(pendingMessage.get(sha));
                    reply(ex, 200, out);
                } else {
                    reply(ex, 404, out.put("message", "Not Found: " + method + " " + rest + (query == null ? "" : "?" + query)));
                }
            }
        }
    }

    private final Map<String, String> pendingParent = new HashMap<>();
    private final Map<String, String> pendingMessage = new HashMap<>();

    private void reply(HttpExchange ex, int status, JsonNode body) throws IOException {
        byte[] bytes = json.writeValueAsBytes(body);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
