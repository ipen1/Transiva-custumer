-- Optional performance index for phpMyAdmin deployment.
-- Run ONCE after checking these names do not already exist.
-- Use migrate_chat_reliability.php instead if CLI is available; it checks existence.
ALTER TABLE chat_messages
 ADD COLUMN room_key VARCHAR(191) GENERATED ALWAYS AS (UPPER(REPLACE(room_id,'_','-'))) STORED;
ALTER TABLE chat_messages ADD INDEX idx_chat_room_key_id(room_key,id);
