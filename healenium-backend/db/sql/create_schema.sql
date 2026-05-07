CREATE SCHEMA healenium;
CREATE TABLE IF NOT EXISTS healenium.test_results (
                                                      id           SERIAL PRIMARY KEY,
                                                      test_name    VARCHAR(300)  NOT NULL,
    suite        VARCHAR(100),
    browser      VARCHAR(50),
    status       VARCHAR(20)   NOT NULL,
    duration_ms  BIGINT,
    build        VARCHAR(100),
    run_at       TIMESTAMP     DEFAULT NOW(),
    error_message TEXT
    );