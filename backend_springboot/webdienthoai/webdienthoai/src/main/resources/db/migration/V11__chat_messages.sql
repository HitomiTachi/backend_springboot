CREATE TABLE chat_messages (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    role        VARCHAR(16)  NOT NULL COMMENT 'user | assistant',
    content     TEXT         NOT NULL,
    sent_at     DATETIME(6)  NOT NULL DEFAULT NOW(6),

    INDEX idx_chat_messages_user_sent (user_id, sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
