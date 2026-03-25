CREATE TABLE dead_letters (
    id          VARCHAR(36) PRIMARY KEY,
    task_type   VARCHAR(30)  NOT NULL,
    target_id   VARCHAR(36)  NOT NULL,
    error_message TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count INT          NOT NULL DEFAULT 0,
    max_retries INT          NOT NULL DEFAULT 3,
    next_retry_at TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dead_letters_status_retry ON dead_letters (status, next_retry_at);
CREATE INDEX idx_dead_letters_task_type ON dead_letters (task_type);
