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

CREATE TABLE IF NOT EXISTS order_notification_outbox (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,event_key VARCHAR(190) NOT NULL UNIQUE,
 kind VARCHAR(32) NOT NULL,payload_json LONGTEXT NOT NULL,status VARCHAR(16) NOT NULL DEFAULT 'pending',
 attempts INT NOT NULL DEFAULT 0,available_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 locked_at DATETIME NULL,sent_at DATETIME NULL,last_error VARCHAR(80) NULL,
 KEY idx_outbox_due(status,available_at),KEY idx_outbox_lease(status,locked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Install before deploying chat endpoints. Existing chat_message_receipts and
-- order_notification_outbox are included below.
CREATE TABLE IF NOT EXISTS chat_media_receipts (
 user_id BIGINT UNSIGNED NOT NULL,
 operation_id VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 response_json MEDIUMTEXT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(user_id,operation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Do not delete receipts while an installed client can retry an operation.
