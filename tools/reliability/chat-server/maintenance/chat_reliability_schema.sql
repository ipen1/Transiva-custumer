-- Run BEFORE deploying the new server endpoints and Android release.
-- Existing messages are preserved. Do not purge these receipts while clients can retry.
CREATE TABLE IF NOT EXISTS chat_message_receipts (
    user_id BIGINT UNSIGNED NOT NULL,
    client_message_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    message_id BIGINT UNSIGNED NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, client_message_id),
    KEY idx_chat_receipt_message (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
