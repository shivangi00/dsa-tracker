-- Push your solutions to a GitHub repository.
--
--   github_links   one per user: which repository and branch, and your access token, encrypted
--                  with the server's GITHUB_TOKEN_KEY (AES-GCM), never sent back to the browser
--   github_pushes  per problem: a fingerprint of what was last pushed, so the page can show
--                  "Pushed" or "Changes not pushed" without asking GitHub

CREATE TABLE github_links (
    user_id         BIGINT        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    owner           VARCHAR(100)  NOT NULL,
    repo            VARCHAR(100)  NOT NULL,
    branch          VARCHAR(255)  NOT NULL,
    token_encrypted VARCHAR(1000) NOT NULL,
    is_private      BOOLEAN       NOT NULL,
    auto_push       BOOLEAN       NOT NULL DEFAULT FALSE,
    connected_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    last_push_at    TIMESTAMPTZ
);

CREATE TABLE github_pushes (
    problem_id   BIGINT       PRIMARY KEY REFERENCES problems (id) ON DELETE CASCADE,
    content_hash CHAR(64)     NOT NULL,
    pushed_at    TIMESTAMPTZ  NOT NULL,
    commit_url   VARCHAR(500)
);
