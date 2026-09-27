-- V6: production readiness.

-- ---------------------------------------------------------------
-- Sessions in the database (Spring Session JDBC, standard PostgreSQL schema).
-- Sign-ins survive restarts and redeploys, and several copies of the app can
-- share them, so a request can land on any copy.
-- ---------------------------------------------------------------
CREATE TABLE SPRING_SESSION (
    PRIMARY_ID            CHAR(36)     NOT NULL,
    SESSION_ID            CHAR(36)     NOT NULL,
    CREATION_TIME         BIGINT       NOT NULL,
    LAST_ACCESS_TIME      BIGINT       NOT NULL,
    MAX_INACTIVE_INTERVAL INT          NOT NULL,
    EXPIRY_TIME           BIGINT       NOT NULL,
    PRINCIPAL_NAME        VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);
CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36)     NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES    BYTEA        NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
);

-- ---------------------------------------------------------------
-- Optimistic locking. Every update checks the row's version and bumps it:
--   UPDATE problems SET ..., version = 4 WHERE id = 7 AND version = 3
-- If two requests (say, two browser tabs) change the same row at once, the second
-- one updates 0 rows and fails cleanly (409) instead of silently double-counting.
-- ---------------------------------------------------------------
ALTER TABLE problems          ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE weekly_tests      ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE weekly_test_items ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users             ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
