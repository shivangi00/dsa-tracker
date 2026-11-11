package dev.shivangi.dsatracker.github;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GitHubLinkRepository extends JpaRepository<GitHubLink, Long> {
}
