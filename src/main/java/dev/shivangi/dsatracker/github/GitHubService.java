package dev.shivangi.dsatracker.github;

import dev.shivangi.dsatracker.analysis.ComplexityAnalysis;
import dev.shivangi.dsatracker.domain.AppUser;
import dev.shivangi.dsatracker.domain.Attempt;
import dev.shivangi.dsatracker.domain.AttemptRepository;
import dev.shivangi.dsatracker.domain.CatalogProblem;
import dev.shivangi.dsatracker.domain.CatalogRepository;
import dev.shivangi.dsatracker.domain.Problem;
import dev.shivangi.dsatracker.domain.ProblemRepository;
import dev.shivangi.dsatracker.domain.SolutionVersion;
import dev.shivangi.dsatracker.domain.SolutionVersionRepository;
import dev.shivangi.dsatracker.domain.UserRepository;
import dev.shivangi.dsatracker.repetition.Rating;
import dev.shivangi.dsatracker.service.BadRequestException;
import dev.shivangi.dsatracker.service.ConflictException;
import dev.shivangi.dsatracker.service.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * "Push to GitHub": your notes, every version of your code and its analysis, in your own repository,
 * laid out as topic / question / sitting (see {@link RepoLayout}). One click per problem, or
 * everything at once. Nothing is pushed unless you ask (or turn on "push after every save").
 */
@Service
public class GitHubService {

    /** What the page needs: whether it's available, linked, and which problems have unpushed changes. */
    public record Status(boolean available, String unavailableReason, boolean connected, String repository,
                         String branch, boolean isPrivate, String repoUrl, boolean autoPush, Instant lastPushAt,
                         Map<Long, ProblemStatus> problems) {
    }

    /** @param upToDate false when the problem changed since it was last pushed */
    public record ProblemStatus(Instant pushedAt, boolean upToDate, String folderUrl, String commitUrl) {
    }

    /** @param changed false when GitHub already had exactly this (nothing was committed) */
    public record PushView(boolean changed, int problems, String commitUrl, String folderUrl, String message) {
    }

    private static final java.util.regex.Pattern REPO = java.util.regex.Pattern.compile(
            "^(?:https?://(?:www\\.)?github\\.com/)?([A-Za-z0-9](?:[A-Za-z0-9-]{0,38}))/([A-Za-z0-9._-]{1,100}?)(?:\\.git)?/?$");

    private final GitHubLinkRepository links;
    private final ProblemPushRepository pushes;
    private final ProblemRepository problems;
    private final AttemptRepository attempts;
    private final SolutionVersionRepository versions;
    private final CatalogRepository catalog;
    private final UserRepository users;
    private final GitHubClient client;
    private final TokenCipher cipher;          // null when GITHUB_TOKEN_KEY isn't set
    private final String unavailableReason;
    private final Clock clock;

    public GitHubService(GitHubLinkRepository links, ProblemPushRepository pushes, ProblemRepository problems,
                         AttemptRepository attempts, SolutionVersionRepository versions, CatalogRepository catalog,
                         UserRepository users, GitHubClient client, Clock clock,
                         @Value("${app.github.token-key:}") String tokenKey) {
        this.links = links;
        this.pushes = pushes;
        this.problems = problems;
        this.attempts = attempts;
        this.versions = versions;
        this.catalog = catalog;
        this.users = users;
        this.client = client;
        this.clock = clock;
        TokenCipher c = null;
        String reason = null;
        if (tokenKey == null || tokenKey.isBlank()) {
            reason = "Pushing to GitHub needs GITHUB_TOKEN_KEY set on the server (see DEPLOY.md), so tokens can be stored encrypted.";
        } else {
            try {
                c = new TokenCipher(tokenKey);
            } catch (IllegalArgumentException e) {
                reason = e.getMessage() + ". See DEPLOY.md.";
            }
        }
        this.cipher = c;
        this.unavailableReason = reason;
    }

    // ---------------------------------------------------------------- linking

    @Transactional(readOnly = true)
    public Status status(Long userId) {
        GitHubLink link = links.findById(userId).orElse(null);
        if (cipher == null || link == null) {
            return new Status(cipher != null, unavailableReason, false, null, null, false, null, false, null, Map.of());
        }
        List<Problem> mine = problems.findByUserIdOrderBySolvedOnDescIdDesc(userId);
        Map<Long, ProblemPush> pushed = pushes.findByProblemIdIn(mine.stream().map(Problem::getId).toList()).stream()
                .collect(Collectors.toMap(ProblemPush::getProblemId, Function.identity()));
        Map<Long, ProblemStatus> out = new LinkedHashMap<>();
        if (!pushed.isEmpty()) {
            List<Problem> pushedProblems = mine.stream().filter(p -> pushed.containsKey(p.getId())).toList();
            Map<Long, RepoLayout.Problem> snaps = snapshots(pushedProblems);
            for (Problem p : pushedProblems) {
                ProblemPush push = pushed.get(p.getId());
                RepoLayout.Problem snap = snaps.get(p.getId());
                out.put(p.getId(), new ProblemStatus(push.getPushedAt(), push.getContentHash().equals(hash(RepoLayout.problemFiles(snap))),
                        folderUrl(link, RepoLayout.problemFolder(snap)), push.getCommitUrl()));
            }
        }
        return new Status(true, null, true, link.fullName(), link.getBranch(), link.isPrivate(), repoUrl(link),
                link.isAutoPush(), link.getLastPushAt(), out);
    }

    /**
     * Links a repository. {@code repository} may be "owner/name" or its github.com address; the
     * branch defaults to the repository's default branch. The token is checked against GitHub first.
     */
    @Transactional
    public Status connect(Long userId, String repository, String token, String branch, boolean autoPush) {
        requireAvailable();
        Matcher m = REPO.matcher(repository == null ? "" : repository.strip());
        if (!m.matches()) {
            throw new BadRequestException("Enter the repository as owner/name, e.g. shivangi/neetcode-150");
        }
        String t = token == null ? "" : token.strip();
        if (t.isEmpty() || t.length() > 300 || t.chars().anyMatch(Character::isWhitespace)) {
            throw new BadRequestException("Paste your GitHub token (it starts with github_pat_ or ghp_)");
        }
        GitHubClient.Repo repo = client.repo(m.group(1), m.group(2), t);
        String b = branch == null || branch.isBlank() ? repo.defaultBranch() : branch.strip();
        if (b.length() > 255 || b.contains("..") || b.startsWith("/") || b.endsWith("/") || b.chars().anyMatch(Character::isWhitespace)) {
            throw new BadRequestException("That isn't a valid branch name");
        }
        String[] parts = repo.fullName().split("/", 2);
        GitHubLink previous = links.findById(userId).orElse(null);
        if (previous == null || !previous.fullName().equalsIgnoreCase(repo.fullName()) || !previous.getBranch().equals(b)) {
            pushes.deleteForUser(userId);   // a different repository: nothing has been pushed there yet
        }
        links.findById(userId).ifPresent(links::delete);
        links.flush();
        links.save(new GitHubLink(userId, parts[0], parts[1], b, cipher.encrypt(t), repo.isPrivate(), autoPush, clock.instant()));
        return status(userId);
    }

    @Transactional
    public Status setAutoPush(Long userId, boolean autoPush) {
        requireLink(userId).setAutoPush(autoPush);
        return status(userId);
    }

    /** Forgets the repository and token. Nothing is removed from GitHub. */
    @Transactional
    public void disconnect(Long userId) {
        links.findById(userId).ifPresent(links::delete);
        pushes.deleteForUser(userId);
    }

    // ---------------------------------------------------------------- pushing

    /** Pushes one problem (all its sittings) and refreshes the repository's front page. */
    @Transactional
    public PushView pushProblem(Long userId, long problemId) {
        GitHubLink link = requireLink(userId);
        Problem problem = problems.findByIdAndUserId(problemId, userId)
                .orElseThrow(() -> new NotFoundException("No problem with id " + problemId));
        RepoLayout.Problem snap = snapshots(List.of(problem)).get(problem.getId());
        Map<String, String> files = new LinkedHashMap<>(RepoLayout.problemFiles(snap));
        files.put(RepoLayout.ROOT_README, rootReadme(userId, link, Set.of(problem.getId())));
        String folder = RepoLayout.problemFolder(snap);
        GitHubClient.PushResult result = client.push(link.getOwner(), link.getRepo(), link.getBranch(), token(link),
                files, Set.of(folder), RepoLayout.commitMessage(snap));
        Instant now = clock.instant();
        record(problem.getId(), RepoLayout.problemFiles(snap), now, result.commitUrl());
        link.pushed(now);
        return new PushView(result.changed(), 1, result.commitUrl(), folderUrl(link, folder),
                result.changed() ? "Pushed " + problem.getName() + " to " + link.fullName() + "."
                        : problem.getName() + " was already up to date on GitHub.");
    }

    /** Pushes every problem you've solved, in one commit. */
    @Transactional
    public PushView pushAll(Long userId) {
        GitHubLink link = requireLink(userId);
        List<Problem> mine = problems.findByUserIdOrderBySolvedOnDescIdDesc(userId);
        if (mine.isEmpty()) {
            throw new ConflictException("Nothing to push yet: mark a problem as done first.");
        }
        Map<Long, RepoLayout.Problem> snaps = snapshots(mine);
        Map<String, String> files = new LinkedHashMap<>();
        Set<String> folders = new LinkedHashSet<>();
        for (RepoLayout.Problem s : snaps.values()) {
            files.putAll(RepoLayout.problemFiles(s));
            folders.add(RepoLayout.problemFolder(s));
        }
        files.put(RepoLayout.ROOT_README, RepoLayout.rootReadme(List.copyOf(snaps.values()), ownerName(userId)));
        GitHubClient.PushResult result = client.push(link.getOwner(), link.getRepo(), link.getBranch(), token(link),
                files, folders, "Sync " + mine.size() + (mine.size() == 1 ? " problem" : " problems") + " from my DSA tracker");
        Instant now = clock.instant();
        snaps.forEach((id, s) -> record(id, RepoLayout.problemFiles(s), now, result.commitUrl()));
        link.pushed(now);
        return new PushView(result.changed(), mine.size(), result.commitUrl(), repoUrl(link),
                result.changed() ? "Pushed " + mine.size() + (mine.size() == 1 ? " problem" : " problems") + " to " + link.fullName() + "."
                        : "Everything was already up to date on GitHub.");
    }

    // ---------------------------------------------------------------- building the files

    /** The front page lists every problem pushed so far, plus {@code alsoIncluding}. */
    private String rootReadme(Long userId, GitHubLink link, Set<Long> alsoIncluding) {
        List<Problem> mine = problems.findByUserIdOrderBySolvedOnDescIdDesc(userId);
        Set<Long> pushed = pushes.findByProblemIdIn(mine.stream().map(Problem::getId).toList()).stream()
                .map(ProblemPush::getProblemId).collect(Collectors.toSet());
        List<Problem> listed = mine.stream().filter(p -> pushed.contains(p.getId()) || alsoIncluding.contains(p.getId())).toList();
        return RepoLayout.rootReadme(List.copyOf(snapshots(listed).values()), ownerName(userId));
    }

    /** The tracker's data for these problems, in the shape {@link RepoLayout} needs. */
    Map<Long, RepoLayout.Problem> snapshots(Collection<Problem> list) {
        Map<Long, RepoLayout.Problem> out = new LinkedHashMap<>();
        if (list.isEmpty()) {
            return out;
        }
        Map<Integer, CatalogProblem> cat = catalog.findAllByOrderByIdAsc().stream()
                .collect(Collectors.toMap(CatalogProblem::getId, Function.identity()));
        List<Long> ids = list.stream().map(Problem::getId).toList();
        Map<Long, List<Attempt>> byProblem = attempts.findByProblemIdInOrderByProblemIdAscRevisionAscTryNoAsc(ids).stream()
                .collect(Collectors.groupingBy(Attempt::getProblemId));
        List<Long> attemptIds = byProblem.values().stream().flatMap(List::stream).map(Attempt::getId).toList();
        Map<Long, List<SolutionVersion>> byAttempt = attemptIds.isEmpty() ? Map.of()
                : versions.findByAttemptIdInOrderByAttemptIdAscVersionNoAsc(attemptIds).stream()
                .collect(Collectors.groupingBy(SolutionVersion::getAttemptId));
        // Topics without a catalog entry (your own problems) go last, in "Other".
        int otherOrder = cat.values().stream().mapToInt(CatalogProblem::getCategoryOrder).max().orElse(17) + 1;
        for (Problem p : list) {
            CatalogProblem c = p.getCatalogId() == null ? null : cat.get(p.getCatalogId());
            List<RepoLayout.Sitting> sittings = new ArrayList<>();
            for (Attempt a : byProblem.getOrDefault(p.getId(), List.of())) {
                List<RepoLayout.Version> vs = new ArrayList<>();
                for (SolutionVersion v : byAttempt.getOrDefault(a.getId(), List.of())) {
                    ComplexityAnalysis an = v.analysis();
                    vs.add(new RepoLayout.Version(v.getVersionNo(), v.getCodeLanguage() == null ? null : v.getCodeLanguage().name(),
                            v.getCode(), an == null ? null : new RepoLayout.Analysis(an.time(), an.space(), an.reasons(), an.source())));
                }
                sittings.add(new RepoLayout.Sitting(a.getRevision(), a.getTryNo(), a.getAttemptedOn(),
                        label(a.getRating(), a.getRevision() == 0), a.getLearnings(), a.getExcalidrawUrl(), vs));
            }
            out.put(p.getId(), new RepoLayout.Problem(
                    c == null ? null : c.getId(),
                    c == null ? "Other" : c.getCategory(),
                    c == null ? otherOrder : c.getCategoryOrder(),
                    p.getName(),
                    p.getDifficulty() == null ? null : p.getDifficulty().name(),
                    c != null ? c.getUrl() : p.getLink(),
                    p.isFullyRevised(),
                    sittings));
        }
        return out;
    }

    /** The labels you chose on the page: Forgot/Hard/Medium/Easy when solving, Again/Hard/Good/Easy when revising. */
    static String label(Rating r, boolean solve) {
        if (r == null) {
            return null;
        }
        return switch (r) {
            case AGAIN -> solve ? "Forgot" : "Again";
            case HARD -> "Hard";
            case GOOD -> solve ? "Medium" : "Good";
            case EASY -> "Easy";
        };
    }

    private void record(Long problemId, Map<String, String> files, Instant at, String commitUrl) {
        String h = hash(files);
        ProblemPush existing = pushes.findById(problemId).orElse(null);
        if (existing == null) {
            pushes.save(new ProblemPush(problemId, h, at, commitUrl));
        } else {
            existing.update(h, at, commitUrl);
        }
    }

    /** A fingerprint of a problem's files (paths and contents), to tell whether anything changed. */
    static String hash(Map<String, String> files) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (Map.Entry<String, String> e : new TreeMap<>(files).entrySet()) {
                sha.update(e.getKey().getBytes(StandardCharsets.UTF_8));
                sha.update((byte) 0);
                sha.update(e.getValue().getBytes(StandardCharsets.UTF_8));
                sha.update((byte) 0);
            }
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- helpers

    private GitHubLink requireLink(Long userId) {
        requireAvailable();
        return links.findById(userId).orElseThrow(() -> new ConflictException("Link a GitHub repository first (Settings → GitHub)."));
    }

    private void requireAvailable() {
        if (cipher == null) {
            throw new ConflictException(unavailableReason);
        }
    }

    private String token(GitHubLink link) {
        try {
            return cipher.decrypt(link.getTokenEncrypted());
        } catch (IllegalStateException e) {
            throw new ConflictException(e.getMessage());
        }
    }

    private String ownerName(Long userId) {
        return users.findById(userId).map(AppUser::getUsername).orElse("");
    }

    private static String repoUrl(GitHubLink link) {
        return "https://github.com/" + link.getOwner() + "/" + link.getRepo();
    }

    static String folderUrl(GitHubLink link, String folder) {
        StringBuilder url = new StringBuilder(repoUrl(link)).append("/tree");
        for (String part : (link.getBranch() + "/" + folder).split("/")) {
            url.append('/').append(enc(part));
        }
        return url.toString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
