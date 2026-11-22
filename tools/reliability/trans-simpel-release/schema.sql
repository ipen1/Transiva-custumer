CREATE DATABASE courier_test CHARACTER SET utf8mb4;
USE courier_test;
CREATE TABLE users (id INT PRIMARY KEY, username VARCHAR(100), name VARCHAR(120), role VARCHAR(20), email VARCHAR(120), phone VARCHAR(30), profile_photo TEXT, balance INT, restaurant_id INT, wisata_owner_id INT, email_verified INT, verified_by_admin INT, email_verified_at DATETIME, active_customer_device_id INT);
CREATE TABLE customer_devices (id INT PRIMARY KEY, status VARCHAR(20), ban_reason TEXT, last_seen_at DATETIME);
CREATE TABLE native_api_tokens (id INT PRIMARY KEY, user_id INT, app_scope VARCHAR(20), device_id INT, device_uuid_hash VARCHAR(64), token_hash VARCHAR(64), device_name VARCHAR(30), platform VARCHAR(30), created_at DATETIME, last_used_at DATETIME, expires_at DATETIME, revoked_at DATETIME);
CREATE TABLE api_rate_limits (id INT AUTO_INCREMENT PRIMARY KEY, action_name VARCHAR(50), subject_hash VARCHAR(64), window_started_at DATETIME, blocked_until DATETIME, attempts INT, UNIQUE KEY action_subject (action_name,subject_hash));
CREATE TABLE driver_profiles (user_id INT PRIMARY KEY, is_online INT, is_busy INT, driver_type VARCHAR(30), verification_status VARCHAR(20), latitude DOUBLE, longitude DOUBLE, last_location_at DATETIME);
CREATE TABLE driver_dispatch_offers (driver_username VARCHAR(100), outcome VARCHAR(20), expired_at DATETIME);
CREATE TABLE orders (driver VARCHAR(100), status VARCHAR(30), order_type VARCHAR(30), rating INT);
CREATE TABLE pickup_orders (driver_username VARCHAR(100), status VARCHAR(30), rating INT);
INSERT INTO users (id,username,name,role,active_customer_device_id) VALUES
(1,'buyer','Customer Test','customer',1),
(2,'anna_private','Anna Contoh','driver',NULL),
(3,'budi_private','Budi Contoh','driver',NULL),
(4,'busy_private','Busy','driver',NULL),
(5,'offline_private','Offline','driver',NULL),
(6,'unverified_private','Unverified','driver',NULL),
(7,'car_private','Car','driver',NULL),
(8,'stale_private','Stale','driver',NULL),
(9,'active_private','Active','driver',NULL),
(10,'pending_private','Pending','driver',NULL),
(11,'far_private','Far','driver',NULL);
INSERT INTO customer_devices VALUES (1,'active',NULL,NOW());
INSERT INTO native_api_tokens VALUES
(1,1,'customer',1,'__UUID_HASH__',SHA2('customer-fixture-token',256),'Test','android_native',NOW(),NOW(),DATE_ADD(NOW(),INTERVAL 1 DAY),NULL),
(2,2,'driver',NULL,NULL,SHA2('driver-fixture-token',256),'Test','android_native',NOW(),NOW(),DATE_ADD(NOW(),INTERVAL 1 DAY),NULL);
INSERT INTO driver_profiles
SELECT id,1,0,'bike','verified',-0.9,119.9,NOW() FROM users WHERE role='driver';
UPDATE driver_profiles SET longitude=119.92,driver_type='motor' WHERE user_id=3;
UPDATE driver_profiles SET is_busy=1 WHERE user_id=4;
UPDATE driver_profiles SET is_online=0 WHERE user_id=5;
UPDATE driver_profiles SET verification_status='pending' WHERE user_id=6;
UPDATE driver_profiles SET driver_type='car' WHERE user_id=7;
UPDATE driver_profiles SET last_location_at=DATE_SUB(NOW(),INTERVAL 11 MINUTE) WHERE user_id=8;
UPDATE driver_profiles SET longitude=120.2 WHERE user_id=11;
INSERT INTO orders VALUES ('anna_private','finished','ride',5),('anna_private','finished','transfood',1),('active_private','driver_accepted','ride',NULL);
INSERT INTO pickup_orders VALUES ('anna_private','finished',4);
INSERT INTO driver_dispatch_offers VALUES ('pending_private','offered',DATE_ADD(NOW(),INTERVAL 30 MINUTE));
ALTER TABLE users ADD fcm_token TEXT;
UPDATE users SET balance=40000;
UPDATE users SET profile_photo='uploads/drivers/anna.png' WHERE id=2;
ALTER TABLE driver_profiles ADD name VARCHAR(120), ADD driver_photo TEXT, ADD last_order_at DATETIME, ADD total_order_today INT, ADD total_order_all INT;
UPDATE driver_profiles SET name='Anna Kurir',last_order_at=DATE_SUB(NOW(),INTERVAL 1 HOUR) WHERE user_id=2;
ALTER TABLE orders ADD id INT PRIMARY KEY AUTO_INCREMENT, ADD user_id INT, ADD order_id VARCHAR(50), ADD room_id VARCHAR(100), ADD note TEXT;
ALTER TABLE pickup_orders ADD id INT PRIMARY KEY AUTO_INCREMENT, ADD order_id VARCHAR(50) UNIQUE, ADD user_id INT, ADD username VARCHAR(100),
    ADD pickup_address VARCHAR(255), ADD delivery_address VARCHAR(255), ADD pickup_lat DOUBLE, ADD pickup_lng DOUBLE, ADD delivery_lat DOUBLE, ADD delivery_lng DOUBLE,
    ADD item_name VARCHAR(255), ADD item_category VARCHAR(100), ADD item_value DOUBLE, ADD package_size VARCHAR(30), ADD vehicle_type VARCHAR(30),
    ADD fragile INT, ADD receiver_name VARCHAR(100), ADD receiver_phone VARCHAR(40), ADD note TEXT, ADD distance_km DOUBLE, ADD price INT,
    ADD payment_method VARCHAR(30), ADD delivery_otp VARCHAR(10), ADD payment_status VARCHAR(30), ADD escrow_amount BIGINT, ADD driver_settlement_status VARCHAR(30),
    ADD created_at DATETIME DEFAULT CURRENT_TIMESTAMP, ADD updated_at DATETIME, ADD accepted_at DATETIME;
ALTER TABLE driver_dispatch_offers ADD id INT PRIMARY KEY AUTO_INCREMENT, ADD source_table VARCHAR(32), ADD order_id INT, ADD action_token VARCHAR(128);
CREATE TABLE driver_dispatch_history (driver_username VARCHAR(100), outcome VARCHAR(20), source_table VARCHAR(32),order_id INT);
CREATE TABLE wallet_transactions (user_id INT,type VARCHAR(60),amount BIGINT,reference VARCHAR(90),description VARCHAR(200));
CREATE TABLE order_attempts (user_id INT,attempt_key VARCHAR(120),request_hash CHAR(64),response_json LONGTEXT, PRIMARY KEY(user_id,attempt_key));
CREATE TABLE order_notification_outbox (id INT AUTO_INCREMENT PRIMARY KEY,event_key VARCHAR(190) UNIQUE,kind VARCHAR(32),payload_json LONGTEXT,
 status VARCHAR(16) DEFAULT 'pending',attempts INT DEFAULT 0,available_at DATETIME DEFAULT CURRENT_TIMESTAMP,locked_at DATETIME NULL,sent_at DATETIME NULL,last_error VARCHAR(80));
CREATE TABLE pickup_driver_fee_holds (id INT AUTO_INCREMENT PRIMARY KEY,order_id INT,driver_user_id INT,driver_username VARCHAR(100),amount INT,
 quote_json LONGTEXT,state VARCHAR(22),revision INT,updated_at DATETIME,UNIQUE KEY pickup_driver(order_id,driver_username));
CREATE TABLE chat_messages(id INT PRIMARY KEY AUTO_INCREMENT,room_id VARCHAR(100),sender_type VARCHAR(20),message TEXT,created_at DATETIME,is_read INT,read_at DATETIME);
CREATE TABLE service_pricing (service_type VARCHAR(40) PRIMARY KEY,min_price BIGINT,base_km DOUBLE,mid_to_km DOUBLE,mid_price_per_km BIGINT,next_price_per_km BIGINT,is_active INT);
CREATE TABLE weather_pricing (service_type VARCHAR(40),is_rain_active INT,start_surcharge BIGINT);
INSERT INTO users (id,username,name,role,active_customer_device_id,balance) VALUES (12,'other_buyer','Other Customer','customer',2,40000);
INSERT INTO customer_devices VALUES (2,'active',NULL,NOW());
INSERT INTO native_api_tokens VALUES
(3,3,'driver',NULL,NULL,SHA2('other-driver-token',256),'Test','android_native',NOW(),NOW(),DATE_ADD(NOW(),INTERVAL 1 DAY),NULL),
(4,12,'customer',2,'__UUID_HASH__',SHA2('other-customer-token',256),'Test','android_native',NOW(),NOW(),DATE_ADD(NOW(),INTERVAL 1 DAY),NULL);
CREATE TABLE IF NOT EXISTS transsend_contacts (
    id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    customer_id BIGINT UNSIGNED NOT NULL,
    driver_id BIGINT UNSIGNED NOT NULL,
    state ENUM('pending','accepted','rejected','cancelled','ordered') NOT NULL DEFAULT 'pending',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,
    accepted_at DATETIME NULL,
    order_db_id BIGINT UNSIGNED NULL,
    UNIQUE KEY uq_tsc_pickup_order (order_db_id),
    KEY idx_tsc_customer (customer_id,state,expires_at),
    KEY idx_tsc_driver (driver_id,state,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
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
CREATE TABLE transiva_webrtc_calls (id INT PRIMARY KEY AUTO_INCREMENT,call_id VARCHAR(64) UNIQUE,order_id VARCHAR(64),order_source VARCHAR(40),
 caller_role VARCHAR(20),caller_user_id INT,caller_username VARCHAR(100),callee_role VARCHAR(20),callee_user_id INT,callee_username VARCHAR(100),
 status VARCHAR(20),expires_at DATETIME,created_at DATETIME DEFAULT CURRENT_TIMESTAMP,accepted_at DATETIME,ended_at DATETIME,offer_sdp LONGTEXT,answer_sdp LONGTEXT);
CREATE TABLE transiva_webrtc_ice (id INT PRIMARY KEY AUTO_INCREMENT,call_id VARCHAR(64),sender_role VARCHAR(20),candidate TEXT,sdp_mid VARCHAR(40),sdp_mline_index INT);
ALTER TABLE wallet_transactions ADD balance_before BIGINT, ADD balance_after BIGINT, ADD status VARCHAR(25), ADD created_at DATETIME;
CREATE TABLE user_pins (user_id INT PRIMARY KEY, pin_hash VARCHAR(200));
INSERT INTO user_pins VALUES (12,'local-payment-signing-fixture');

DROP TABLE wallet_transactions;
RENAME TABLE orders TO orders_test_old;
CREATE TABLE `orders` (
  `id` int(11) NOT NULL,
  `user_id` int(11) DEFAULT NULL,
  `order_id` varchar(100) DEFAULT NULL,
  `pickup_lat` double DEFAULT NULL,
  `pickup_lng` double DEFAULT NULL,
  `pickup_address` text DEFAULT NULL,
  `delivery_lat` double DEFAULT NULL,
  `delivery_lng` double DEFAULT NULL,
  `delivery_address` text DEFAULT NULL,
  `distance_km` double DEFAULT NULL,
  `duration_minutes` double DEFAULT NULL,
  `price` int(11) DEFAULT NULL,
  `payment_method` varchar(30) NOT NULL DEFAULT 'cash',
  `payment_status` varchar(30) NOT NULL DEFAULT 'unpaid',
  `escrow_amount` int(11) NOT NULL DEFAULT 0,
  `escrow_released_at` datetime DEFAULT NULL,
  `refunded_at` datetime DEFAULT NULL,
  `driver_settlement_status` varchar(30) NOT NULL DEFAULT 'none',
  `user_lat` double DEFAULT NULL,
  `user_lng` double DEFAULT NULL,
  `status` varchar(50) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `scheduled_at` datetime DEFAULT NULL,
  `driver` varchar(100) DEFAULT NULL,
  `note` text DEFAULT NULL,
  `room_id` varchar(100) DEFAULT NULL,
  `driver_lat` varchar(50) DEFAULT NULL,
  `driver_lng` varchar(50) DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `offered_driver` varchar(50) DEFAULT NULL,
  `offer_expired_at` datetime DEFAULT NULL,
  `accepted_at` datetime DEFAULT NULL,
  `order_type` varchar(30) DEFAULT 'kurir',
  `cancel_reason` text DEFAULT NULL,
  `driver_cancelled_at` datetime DEFAULT NULL,
  `rating` int(11) DEFAULT 0,
  `review` text DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `driver_type` varchar(20) NOT NULL DEFAULT 'motor',
  `offer_action_token` varchar(80) DEFAULT NULL,
  `merchant_action_token` varchar(80) DEFAULT NULL,
  `voucher_code` varchar(40) DEFAULT NULL,
  `voucher_discount` int(11) NOT NULL DEFAULT 0,
  `original_price` int(11) NOT NULL DEFAULT 0,
  `price_mode` varchar(24) NOT NULL DEFAULT 'standard',
  `price_guaranteed` tinyint(1) NOT NULL DEFAULT 1,
  `family_member_id` bigint(20) DEFAULT NULL,
  `customer_received` tinyint(1) NOT NULL DEFAULT 0,
  `customer_received_at` datetime DEFAULT NULL,
  `price_change_status` varchar(24) NOT NULL DEFAULT 'none',
  `price_change_requested` decimal(12,2) DEFAULT NULL,
  `price_change_reason` varchar(255) DEFAULT NULL,
  `price_change_driver` varchar(120) DEFAULT NULL,
  `price_change_requested_at` datetime DEFAULT NULL,
  `price_change_resolved_at` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
ALTER TABLE `orders`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_status` (`status`),
  ADD KEY `idx_offer_status` (`offered_driver`,`status`,`offer_expired_at`),
  ADD KEY `idx_driver_status` (`driver`,`status`),
  ADD KEY `idx_orders_payment_status` (`payment_method`,`payment_status`),
  ADD KEY `idx_offer_action` (`id`,`offered_driver`,`offer_action_token`),
  ADD KEY `idx_merchant_action` (`id`,`merchant_action_token`),
  ADD KEY `idx_order_offer_driver` (`offered_driver`,`offer_expired_at`),
  ADD KEY `idx_orders_order_id` (`order_id`),
  ADD KEY `idx_orders_status_driver` (`status`,`driver`),
  ADD KEY `idx_orders_offer` (`offered_driver`,`offer_expired_at`,`status`),
  ADD KEY `idx_orders_driver_status` (`driver`,`status`),
  ADD KEY `idx_driver_status_id` (`driver`,`status`,`id`),
  ADD KEY `idx_offer_driver_status_id` (`offered_driver`,`status`,`id`),
  ADD KEY `idx_offer_expiry` (`offered_driver`,`status`,`offer_expired_at`,`id`),
  ADD KEY `idx_orders_scheduled_status` (`status`,`scheduled_at`),
  ADD KEY `idx_orders_customer_id` (`user_id`,`id`);
ALTER TABLE `orders`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=284;
CREATE TABLE `ride_server_routes` (
  `order_db_id` bigint(20) NOT NULL,
  `route_json` longtext NOT NULL,
  `route_hash` char(64) NOT NULL,
  `version` bigint(20) NOT NULL DEFAULT 1,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `ride_server_routes`
  ADD PRIMARY KEY (`order_db_id`);
CREATE TABLE `ride_feature_meta` (
  `order_id` varchar(80) NOT NULL,
  `trip_monitoring` tinyint(1) NOT NULL DEFAULT 1,
  `audio_protect` tinyint(1) NOT NULL DEFAULT 0,
  `group_size` tinyint(3) UNSIGNED NOT NULL DEFAULT 1,
  `split_fare_mode` varchar(24) NOT NULL DEFAULT 'none',
  `family_payer_id` bigint(20) UNSIGNED DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `ride_feature_meta`
  ADD PRIMARY KEY (`order_id`);
CREATE TABLE `ride_waypoints` (
  `id` bigint(20) UNSIGNED NOT NULL,
  `order_id` varchar(80) NOT NULL,
  `sequence_no` tinyint(3) UNSIGNED NOT NULL,
  `latitude` decimal(10,7) NOT NULL,
  `longitude` decimal(10,7) NOT NULL,
  `address` varchar(255) DEFAULT NULL,
  `note` varchar(180) DEFAULT NULL,
  `status` varchar(32) NOT NULL DEFAULT 'pending',
  `arrived_at` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `ride_waypoints`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_order_seq` (`order_id`,`sequence_no`),
  ADD KEY `idx_order` (`order_id`);
ALTER TABLE `ride_waypoints`
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=38;
CREATE TABLE `driver_order_fee_holds` (
  `id` bigint(20) UNSIGNED NOT NULL,
  `order_id` bigint(20) NOT NULL,
  `driver_user_id` bigint(20) NOT NULL,
  `driver_username` varchar(100) NOT NULL,
  `amount` bigint(20) NOT NULL DEFAULT 0,
  `quote_json` longtext NOT NULL,
  `state` varchar(20) NOT NULL DEFAULT 'held',
  `revision` int(11) NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `driver_order_fee_holds`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_driver_order_fee` (`order_id`,`driver_username`),
  ADD KEY `idx_driver_fee_state` (`driver_user_id`,`state`);
ALTER TABLE `driver_order_fee_holds`
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=68;
CREATE TABLE `wallet_transactions` (
  `id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `type` varchar(30) NOT NULL,
  `direction` varchar(8) DEFAULT NULL,
  `amount` int(11) NOT NULL DEFAULT 0,
  `balance_before` int(11) NOT NULL DEFAULT 0,
  `balance_after` int(11) NOT NULL DEFAULT 0,
  `status` varchar(30) NOT NULL DEFAULT 'success',
  `reference` varchar(100) NOT NULL,
  `description` text DEFAULT NULL,
  `related_user_id` bigint(20) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `wallet_transactions`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `unique_reference_type` (`reference`,`type`),
  ADD KEY `idx_user_id` (`user_id`),
  ADD KEY `idx_created_at` (`created_at`);
ALTER TABLE `wallet_transactions`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=163;
CREATE TABLE `admin_order_cancellations` (
  `id` bigint(20) UNSIGNED NOT NULL,
  `source_table` varchar(40) NOT NULL,
  `order_db_id` bigint(20) NOT NULL,
  `order_id` varchar(120) NOT NULL DEFAULT '',
  `previous_status` varchar(60) NOT NULL DEFAULT '',
  `customer_user_id` bigint(20) NOT NULL DEFAULT 0,
  `driver_username` varchar(120) NOT NULL DEFAULT '',
  `refund_amount` bigint(20) NOT NULL DEFAULT 0,
  `coin_refund` int(11) NOT NULL DEFAULT 0,
  `reason` varchar(255) NOT NULL DEFAULT '',
  `admin_username` varchar(120) NOT NULL DEFAULT '',
  `created_at` datetime NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `admin_order_cancellations`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_admin_cancel_order` (`source_table`,`order_db_id`),
  ADD KEY `idx_admin_cancel_code` (`order_id`),
  ADD KEY `idx_admin_cancel_created` (`created_at`);
ALTER TABLE `admin_order_cancellations`
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=22;
CREATE TABLE `admin_control_audit` (
  `id` bigint(20) UNSIGNED NOT NULL,
  `actor_id` int(11) NOT NULL,
  `actor_name` varchar(80) NOT NULL,
  `endpoint` varchar(100) NOT NULL,
  `action_name` varchar(100) NOT NULL,
  `target_ref` varchar(120) NOT NULL DEFAULT '',
  `outcome` varchar(24) NOT NULL DEFAULT 'started',
  `before_json` mediumtext DEFAULT NULL,
  `after_json` mediumtext DEFAULT NULL,
  `reason` varchar(500) NOT NULL DEFAULT '',
  `created_at` datetime NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `admin_control_audit`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_audit_created` (`created_at`,`id`),
  ADD KEY `idx_audit_actor` (`actor_id`,`id`);
ALTER TABLE `admin_control_audit`
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=115;
CREATE TABLE `admin_staff_access` (
  `user_id` int(11) NOT NULL,
  `access_role` varchar(24) NOT NULL,
  `enabled` tinyint(4) NOT NULL DEFAULT 1,
  `updated_by` int(11) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `admin_staff_access`
  ADD PRIMARY KEY (`user_id`);
CREATE TABLE `customer_coin_ledger` (
  `id` bigint(20) UNSIGNED NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `order_id` varchar(120) DEFAULT NULL,
  `entry_type` enum('earn','redeem','refund','adjustment') NOT NULL,
  `coin_delta` int(11) NOT NULL,
  `rupiah_value` int(11) NOT NULL DEFAULT 0,
  `reference` varchar(160) NOT NULL,
  `description` varchar(255) NOT NULL DEFAULT '',
  `created_at` datetime NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `customer_coin_ledger`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_coin_reference` (`reference`),
  ADD KEY `idx_coin_user` (`user_id`,`id`),
  ADD KEY `idx_coin_order` (`order_id`);
ALTER TABLE `customer_coin_ledger`
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=51;
CREATE TABLE `food_order_stock_reservations` (
  `order_db_id` bigint(20) NOT NULL,
  `menu_id` int(11) NOT NULL,
  `restaurant_id` int(11) NOT NULL,
  `quantity` int(11) NOT NULL,
  `state` varchar(20) NOT NULL DEFAULT 'reserved',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `food_order_stock_reservations`
  ADD PRIMARY KEY (`order_db_id`,`menu_id`),
  ADD KEY `idx_food_stock_menu` (`menu_id`,`state`);
CREATE TABLE `food_menu_availability_flags` (
  `menu_id` int(11) NOT NULL,
  `restaurant_id` int(11) NOT NULL,
  `auto_hidden` tinyint(4) NOT NULL DEFAULT 0,
  `manual_disabled` tinyint(4) NOT NULL DEFAULT 0,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `food_menu_availability_flags`
  ADD PRIMARY KEY (`menu_id`);
CREATE TABLE `merchant_menu_meta` (
  `menu_id` int(11) NOT NULL,
  `restaurant_id` int(11) NOT NULL,
  `stock` int(11) DEFAULT NULL,
  `track_stock` tinyint(1) NOT NULL DEFAULT 0,
  `description` varchar(500) NOT NULL DEFAULT '',
  `options_json` longtext DEFAULT NULL,
  `original_price` bigint(20) DEFAULT NULL,
  `grossup_fee` bigint(20) DEFAULT NULL,
  `discount_percent` decimal(5,2) NOT NULL DEFAULT 0.00,
  `discount_active` tinyint(1) NOT NULL DEFAULT 0,
  `sales_count` bigint(20) NOT NULL DEFAULT 0,
  `updated_at` timestamp NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `merchant_menu_meta`
  ADD PRIMARY KEY (`menu_id`),
  ADD KEY `idx_restaurant` (`restaurant_id`),
  ADD KEY `idx_merchant_menu_active_discount` (`restaurant_id`,`discount_active`,`discount_percent`);
CREATE TABLE `food_menus` (
  `id` int(11) NOT NULL,
  `restaurant_id` int(11) DEFAULT NULL,
  `name` varchar(120) DEFAULT NULL,
  `price` int(11) DEFAULT NULL,
  `image` varchar(255) DEFAULT NULL,
  `category` varchar(80) DEFAULT NULL,
  `is_active` int(11) DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
ALTER TABLE `food_menus`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_food_menus_restaurant_active_id` (`restaurant_id`,`is_active`,`id`);
ALTER TABLE `food_menus`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=39;
CREATE TABLE `smart_waiting_bills` (
  `source` varchar(24) NOT NULL,
  `order_id` bigint(20) NOT NULL,
  `applied_fee` bigint(20) NOT NULL DEFAULT 0,
  `credited_fee` bigint(20) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `smart_waiting_bills`
  ADD PRIMARY KEY (`source`,`order_id`);
CREATE TABLE `smart_waiting_sessions` (
  `id` bigint(20) UNSIGNED NOT NULL,
  `source` varchar(24) NOT NULL,
  `order_id` bigint(20) NOT NULL,
  `point_key` varchar(80) NOT NULL,
  `label` varchar(255) NOT NULL,
  `started_at` bigint(20) NOT NULL,
  `ended_at` bigint(20) DEFAULT NULL,
  `free_seconds` int(11) NOT NULL DEFAULT 300,
  `rate_per_minute` int(11) NOT NULL DEFAULT 500
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `smart_waiting_sessions`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `point_once` (`source`,`order_id`,`point_key`),
  ADD KEY `active_order` (`source`,`order_id`,`ended_at`);
ALTER TABLE `smart_waiting_sessions`
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=83;
CREATE TABLE `driver_transaction_receipts` (
  `id` int(11) NOT NULL,
  `order_id` int(11) NOT NULL,
  `order_code` varchar(100) DEFAULT NULL,
  `driver` varchar(100) NOT NULL,
  `ongkir` int(11) DEFAULT 0,
  `merchant_order` int(11) DEFAULT 0,
  `app_fee` int(11) DEFAULT 0,
  `merchant_grossup_fee` int(11) DEFAULT 0,
  `total_pendapatan` int(11) DEFAULT 0,
  `total_potongan` int(11) DEFAULT 0,
  `saldo_sebelum` int(11) DEFAULT 0,
  `saldo_saat_ini` int(11) DEFAULT 0,
  `sisa_saldo` int(11) DEFAULT 0,
  `items_count` int(11) DEFAULT 0,
  `is_food` tinyint(1) DEFAULT 0,
  `receipt_json` text DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
ALTER TABLE `driver_transaction_receipts`
  ADD PRIMARY KEY (`id`);
ALTER TABLE `driver_transaction_receipts`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=77;
CREATE TABLE `ride_destination_requests` (
  `id` bigint(20) UNSIGNED NOT NULL,
  `order_db_id` bigint(20) NOT NULL,
  `driver` varchar(100) NOT NULL,
  `from_lat` decimal(10,7) NOT NULL,
  `from_lng` decimal(10,7) NOT NULL,
  `to_lat` decimal(10,7) NOT NULL,
  `to_lng` decimal(10,7) NOT NULL,
  `address` varchar(255) NOT NULL,
  `distance_km` decimal(10,3) NOT NULL,
  `extra_fare` bigint(20) NOT NULL,
  `old_price` bigint(20) NOT NULL,
  `total` bigint(20) NOT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'quoted',
  `needs_arrival` tinyint(4) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `resolved_at` datetime DEFAULT NULL,
  `route_json` longtext DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `ride_destination_requests`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_rex_order` (`order_db_id`,`status`,`id`);
ALTER TABLE `ride_destination_requests`
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=7;
CREATE TABLE `driver_customer_reviews` (
  `id` bigint(20) UNSIGNED NOT NULL,
  `order_source` varchar(32) NOT NULL DEFAULT 'orders',
  `order_id` bigint(20) NOT NULL,
  `order_code` varchar(100) NOT NULL DEFAULT '',
  `driver_user_id` bigint(20) NOT NULL,
  `driver_username` varchar(120) NOT NULL,
  `customer_user_id` bigint(20) NOT NULL,
  `rating` tinyint(3) UNSIGNED NOT NULL,
  `review` varchar(300) NOT NULL DEFAULT '',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `driver_customer_reviews`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_driver_customer_review_order` (`order_source`,`order_id`,`driver_user_id`),
  ADD KEY `idx_customer_rating` (`customer_user_id`,`rating`),
  ADD KEY `idx_driver_review` (`driver_user_id`,`created_at`);
ALTER TABLE `driver_customer_reviews`
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=50;
CREATE TABLE `customer_review_moderation` (
  `review_id` bigint(20) UNSIGNED NOT NULL,
  `state` varchar(24) NOT NULL DEFAULT 'visible',
  `admin_note` varchar(1000) NOT NULL DEFAULT '',
  `admin_id` bigint(20) NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `customer_review_moderation`
  ADD PRIMARY KEY (`review_id`);
CREATE TABLE `ride_stop_pricing` (
  `service_type` varchar(32) NOT NULL,
  `max_stops` tinyint(3) UNSIGNED NOT NULL DEFAULT 2,
  `stop_fee` int(10) UNSIGNED NOT NULL,
  `free_seconds` int(10) UNSIGNED NOT NULL DEFAULT 180,
  `rate_per_minute` int(10) UNSIGNED NOT NULL,
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `ride_stop_pricing`
  ADD PRIMARY KEY (`service_type`);
CREATE TABLE `service_fee` (
  `id` int(11) NOT NULL,
  `service_type` varchar(30) NOT NULL,
  `fee_type` enum('fixed','percent') NOT NULL DEFAULT 'fixed',
  `fee_value` decimal(10,2) NOT NULL DEFAULT 0.00,
  `is_active` tinyint(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
ALTER TABLE `service_fee`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `service_type` (`service_type`);
ALTER TABLE `service_fee`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=84;

INSERT INTO orders (driver,status,order_type,rating) SELECT driver,status,order_type,rating FROM orders_test_old;
INSERT INTO service_pricing VALUES ('Transbike',11000,3,5,2000,2600,1),('TransSend',35000,3,5,2000,2600,1);
INSERT INTO service_fee(service_type,fee_type,fee_value) VALUES ('Transbike','percent',15);
INSERT INTO ride_stop_pricing(service_type,max_stops,stop_fee,free_seconds,rate_per_minute) VALUES ('Transbike',2,3000,180,500);
UPDATE users SET profile_photo='uploads/buyer.png' WHERE id=1;
INSERT INTO users (id,username,role) VALUES (13,'test_admin','admin');
INSERT INTO admin_staff_access(user_id,access_role,enabled) VALUES (13,'owner',1);
INSERT INTO native_api_tokens VALUES (8,13,'admin',NULL,NULL,SHA2('admin-fixture-token',256),'Test','web',NOW(),NOW(),DATE_ADD(NOW(),INTERVAL 1 DAY),NULL);
CREATE TABLE `food_restaurants` (
  `id` int(11) NOT NULL,
  `name` varchar(120) DEFAULT NULL,
  `banner` varchar(255) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `latitude` varchar(50) DEFAULT NULL,
  `longitude` varchar(50) DEFAULT NULL,
  `category` varchar(100) DEFAULT NULL,
  `is_open` tinyint(1) NOT NULL DEFAULT 1,
  `rating` decimal(3,1) NOT NULL DEFAULT 0.0,
  `duration` varchar(50) NOT NULL DEFAULT '15 menit',
  `review_count` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
ALTER TABLE `food_restaurants`
  ADD PRIMARY KEY (`id`);
ALTER TABLE `food_restaurants`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=37;
CREATE TABLE `merchant_operations` (
  `restaurant_id` int(11) NOT NULL,
  `pause_until` datetime DEFAULT NULL,
  `pause_indefinite` tinyint(1) NOT NULL DEFAULT 0,
  `schedule_json` longtext DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `merchant_operations`
  ADD PRIMARY KEY (`restaurant_id`);
CREATE TABLE `merchant_branches` (
  `restaurant_id` int(11) NOT NULL,
  `owner_user_id` int(11) NOT NULL,
  `request_key` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `archived_at` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `merchant_branches`
  ADD PRIMARY KEY (`restaurant_id`),
  ADD UNIQUE KEY `uq_branch_request` (`owner_user_id`,`request_key`);
CREATE TABLE `merchant_account_meta` (
  `user_id` int(11) NOT NULL,
  `email` varchar(190) DEFAULT NULL,
  `phone` varchar(32) DEFAULT NULL,
  `is_verified` tinyint(1) NOT NULL DEFAULT 0,
  `registration_source` enum('self','admin') NOT NULL DEFAULT 'self',
  `verified_at` datetime DEFAULT NULL,
  `verified_by` int(11) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `merchant_account_meta`
  ADD PRIMARY KEY (`user_id`),
  ADD UNIQUE KEY `uq_merchant_email` (`email`),
  ADD UNIQUE KEY `uq_merchant_phone` (`phone`),
  ADD KEY `idx_merchant_verified` (`is_verified`);
CREATE TABLE `merchant_admin_state` (
  `user_id` int(11) NOT NULL,
  `archived_at` datetime DEFAULT NULL,
  `archived_by` int(11) DEFAULT NULL,
  `archive_reason` varchar(500) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `merchant_admin_state`
  ADD PRIMARY KEY (`user_id`);
CREATE TABLE `transfood_fee_rules` (
  `id` int(11) NOT NULL,
  `min_amount` int(11) NOT NULL,
  `max_amount` int(11) DEFAULT NULL,
  `fee` int(11) NOT NULL,
  `is_active` tinyint(1) DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
ALTER TABLE `transfood_fee_rules`
  ADD PRIMARY KEY (`id`);
ALTER TABLE `transfood_fee_rules`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=98;
CREATE TABLE `merchant_menu_subcategories` (
  `restaurant_id` int(11) NOT NULL,
  `menu_id` int(11) NOT NULL,
  `category_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `merchant_menu_subcategories`
  ADD PRIMARY KEY (`restaurant_id`,`menu_id`),
  ADD KEY `idx_merchant_subcategory` (`restaurant_id`,`category_id`);

INSERT INTO users(id,username,name,role,restaurant_id) VALUES (101,'m1','M1','merchant',111),(102,'m2','M2','merchant',112),(103,'m3','M3','merchant',113);
INSERT INTO food_restaurants(id,name,latitude,longitude,is_open) VALUES (111,'Resto Rapi','-0.9','119.9',1),(112,'Tutup','-0.91','119.91',0),(113,'Pause','-0.91','119.91',1);
INSERT INTO merchant_account_meta(user_id,is_verified) VALUES (101,1),(102,1),(103,1);
INSERT INTO merchant_operations(restaurant_id,pause_indefinite) VALUES (113,1);
INSERT INTO food_menus(id,restaurant_id,name,price,is_active) VALUES (1,111,'Nasi Ayam',19000,1),(2,111,'Nasi Habis',18500,1),(3,111,'Menu nonaktif',15000,0),(4,112,'Menu toko tutup',19000,1);
INSERT INTO merchant_menu_meta(menu_id,restaurant_id,track_stock,stock) VALUES (1,111,1,4),(2,111,1,0);
CREATE TABLE `merchant_categories` (
  `id` int(11) NOT NULL,
  `restaurant_id` int(11) NOT NULL,
  `name` varchar(80) NOT NULL,
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` timestamp NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE `customer_coin_settings` (
  `id` tinyint(4) NOT NULL,
  `spend_per_coin` int(11) NOT NULL DEFAULT 2000,
  `coin_value_rupiah` int(11) NOT NULL DEFAULT 1,
  `min_redeem_coins` int(11) NOT NULL DEFAULT 1000,
  `min_order_after_discount` int(11) NOT NULL DEFAULT 1000,
  `max_redeem_percent` int(11) NOT NULL DEFAULT 100,
  `is_enabled` tinyint(4) NOT NULL DEFAULT 1,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `customer_coin_settings`
  ADD PRIMARY KEY (`id`);
CREATE TABLE `customer_coin_secure_balance` (
  `user_id` bigint(20) NOT NULL,
  `balance` bigint(20) NOT NULL DEFAULT 0,
  `version` bigint(20) NOT NULL DEFAULT 1,
  `signature` char(64) NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `customer_coin_secure_balance`
  ADD PRIMARY KEY (`user_id`);
CREATE TABLE `transiva_point_settings` (
  `id` tinyint(4) NOT NULL,
  `cutover_at` datetime NOT NULL,
  `legacy_coin_value` int(11) NOT NULL DEFAULT 1,
  `points_per_unit` int(11) NOT NULL DEFAULT 1,
  `migration_completed` tinyint(4) NOT NULL DEFAULT 0,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `transiva_point_settings`
  ADD PRIMARY KEY (`id`);
CREATE TABLE `transiva_point_ledger` (
  `id` bigint(20) UNSIGNED NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `order_id` varchar(120) DEFAULT NULL,
  `source_table` varchar(24) NOT NULL DEFAULT 'orders',
  `entry_kind` varchar(32) NOT NULL,
  `points_delta` bigint(20) NOT NULL DEFAULT 0,
  `tier_delta` bigint(20) NOT NULL DEFAULT 0,
  `spend_amount` bigint(20) NOT NULL DEFAULT 0,
  `reserved_points` bigint(20) NOT NULL DEFAULT 0,
  `reference` varchar(190) NOT NULL,
  `description` varchar(255) NOT NULL DEFAULT '',
  `occurred_at` datetime NOT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `transiva_point_ledger`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_transiva_point_reference` (`reference`),
  ADD KEY `idx_transiva_point_user` (`user_id`,`id`),
  ADD KEY `idx_transiva_point_season` (`user_id`,`occurred_at`),
  ADD KEY `idx_transiva_point_order` (`user_id`,`source_table`,`order_id`);
ALTER TABLE `transiva_point_ledger`
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=221;
CREATE TABLE `split_bill_payments` (
  `id` bigint(20) UNSIGNED NOT NULL,
  `session_key` varchar(64) NOT NULL,
  `order_id` varchar(80) NOT NULL,
  `user_id` bigint(20) UNSIGNED NOT NULL,
  `percentage` decimal(5,2) NOT NULL,
  `amount` bigint(20) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'held',
  `created_at` datetime NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `split_bill_payments`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_sb_pay` (`order_id`,`user_id`),
  ADD KEY `session_key` (`session_key`);
ALTER TABLE `split_bill_payments`
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=14;
CREATE TABLE `merchant_order_lookup` (
  `order_db_id` bigint(20) UNSIGNED NOT NULL,
  `restaurant_id` int(10) UNSIGNED NOT NULL,
  `order_created_at` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `merchant_order_lookup`
  ADD PRIMARY KEY (`order_db_id`),
  ADD KEY `idx_mol_restaurant_order` (`restaurant_id`,`order_db_id`),
  ADD KEY `idx_mol_restaurant_created` (`restaurant_id`,`order_created_at`,`order_db_id`);
CREATE TABLE `ride_vouchers` (
  `id` bigint(20) UNSIGNED NOT NULL,
  `code` varchar(40) NOT NULL,
  `title` varchar(120) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `discount_type` enum('fixed','percent') NOT NULL DEFAULT 'fixed',
  `discount_value` int(11) NOT NULL DEFAULT 0,
  `max_discount` int(11) DEFAULT NULL,
  `min_order` int(11) NOT NULL DEFAULT 0,
  `quota_total` int(11) DEFAULT NULL,
  `quota_used` int(11) NOT NULL DEFAULT 0,
  `per_user_limit` int(11) NOT NULL DEFAULT 1,
  `service_type` enum('TransRide','TransCar','all') NOT NULL DEFAULT 'TransRide',
  `starts_at` datetime DEFAULT NULL,
  `expires_at` datetime DEFAULT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `ride_vouchers`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_ride_voucher_code` (`code`),
  ADD KEY `idx_ride_voucher_active` (`is_active`,`starts_at`,`expires_at`);
ALTER TABLE `ride_vouchers`
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;
CREATE TABLE `ride_voucher_usages` (
  `id` bigint(20) UNSIGNED NOT NULL,
  `voucher_id` bigint(20) UNSIGNED NOT NULL,
  `user_id` int(11) NOT NULL,
  `order_id` varchar(100) NOT NULL,
  `discount_amount` int(11) NOT NULL DEFAULT 0,
  `used_at` datetime NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `ride_voucher_usages`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_ride_voucher_order` (`order_id`),
  ADD KEY `idx_ride_voucher_user` (`voucher_id`,`user_id`);
ALTER TABLE `ride_voucher_usages`
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;
CREATE TABLE `food_expiry_retries` (
  `order_db_id` bigint(20) UNSIGNED NOT NULL,
  `attempts` tinyint(3) UNSIGNED NOT NULL,
  `available_at` datetime NOT NULL,
  `last_error` varchar(80) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `food_expiry_retries`
  ADD PRIMARY KEY (`order_db_id`);
CREATE TABLE `merchant_daily_stats` (
  `restaurant_id` int(10) UNSIGNED NOT NULL,
  `stat_date` date NOT NULL,
  `today_orders` int(10) UNSIGNED NOT NULL DEFAULT 0,
  `active_orders` int(10) UNSIGNED NOT NULL DEFAULT 0,
  `pending_orders` int(10) UNSIGNED NOT NULL DEFAULT 0,
  `finished_orders` int(10) UNSIGNED NOT NULL DEFAULT 0,
  `merchant_revenue` bigint(20) NOT NULL DEFAULT 0,
  `dirty` tinyint(1) NOT NULL DEFAULT 1,
  `refreshed_at` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `merchant_daily_stats`
  ADD PRIMARY KEY (`restaurant_id`,`stat_date`),
  ADD KEY `idx_mds_date` (`stat_date`,`restaurant_id`),
  ADD KEY `idx_mds_dirty` (`dirty`,`refreshed_at`);
CREATE TABLE `order_cancellations` (
  `id` bigint(20) UNSIGNED NOT NULL,
  `source_table` varchar(32) NOT NULL,
  `order_id` varchar(120) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `details_json` longtext NOT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `order_cancellations`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_cancel_source` (`source_table`,`order_id`),
  ADD KEY `idx_cancel_user` (`user_id`,`created_at`);
ALTER TABLE `order_cancellations`
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=119;

ALTER TABLE users ADD delivery_lat DOUBLE, ADD delivery_lng DOUBLE, ADD delivery_address VARCHAR(200);
UPDATE users SET delivery_lat=-0.92,delivery_lng=119.92,delivery_address='Antar' WHERE id=1;
INSERT INTO native_api_tokens VALUES (9,101,'merchant',NULL,NULL,SHA2('merchant-fixture-token',256),'Test','android_native',NOW(),NOW(),DATE_ADD(NOW(),INTERVAL 1 DAY),NULL);
UPDATE merchant_menu_meta SET options_json='[{"type":"bundle_topping","label":"Topping","requires_topping":true,"items":[{"name":"Keju","bundle_price":20000,"is_available":true}]}]' WHERE menu_id=1;
CREATE TABLE `customer_coin_tier_rates` (
  `tier` varchar(20) NOT NULL,
  `earn_percent` int(11) NOT NULL DEFAULT 100,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `customer_coin_tier_rates`
  ADD PRIMARY KEY (`tier`);
CREATE TABLE `customer_coin_service_rates` (
  `service` varchar(32) NOT NULL,
  `earn_percent` int(11) NOT NULL DEFAULT 100,
  `is_enabled` tinyint(4) NOT NULL DEFAULT 1,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `customer_coin_service_rates`
  ADD PRIMARY KEY (`service`);
CREATE TABLE `customer_hemat_tier_limits` (
  `tier` varchar(20) NOT NULL,
  `monthly_limit` int(11) NOT NULL DEFAULT 0,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `customer_hemat_tier_limits`
  ADD PRIMARY KEY (`tier`);
CREATE TABLE `customer_hemat_usages` (
  `id` bigint(20) UNSIGNED NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `benefit_bucket` varchar(20) NOT NULL COMMENT 'ride = TransRide+TransCar, food = TransFood',
  `service_type` varchar(30) NOT NULL,
  `order_id` varchar(120) NOT NULL,
  `discount_amount` int(11) NOT NULL DEFAULT 0,
  `usage_month` char(7) NOT NULL COMMENT 'YYYY-MM',
  `created_at` datetime NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `customer_hemat_usages`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_hemat_order` (`order_id`),
  ADD KEY `idx_hemat_month` (`user_id`,`benefit_bucket`,`usage_month`);
ALTER TABLE `customer_hemat_usages`
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT;
CREATE TABLE `loyalty_season_tier_rules` (
  `tier` varchar(20) NOT NULL,
  `min_points` int(11) NOT NULL DEFAULT 0,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `loyalty_season_tier_rules`
  ADD PRIMARY KEY (`tier`);
CREATE TABLE `loyalty_season_settings` (
  `id` tinyint(3) UNSIGNED NOT NULL,
  `season_name` varchar(80) NOT NULL DEFAULT 'Transiva Season',
  `anchor_date` date NOT NULL,
  `duration_months` tinyint(3) UNSIGNED NOT NULL DEFAULT 6,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `loyalty_season_settings`
  ADD PRIMARY KEY (`id`);
CREATE TABLE `api_idempotency` (
  `id` bigint(20) UNSIGNED NOT NULL,
  `user_id` int(11) NOT NULL,
  `action_name` varchar(80) NOT NULL,
  `idem_key` varchar(120) NOT NULL,
  `response_json` longtext DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `api_idempotency`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_idem_user_action` (`user_id`,`action_name`,`idem_key`),
  ADD KEY `idx_idem_created` (`created_at`);
ALTER TABLE `api_idempotency`
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=70;
CREATE TABLE `loyalty_reward_catalog` (
  `id` int(10) UNSIGNED NOT NULL,
  `code` varchar(40) NOT NULL,
  `title` varchar(120) NOT NULL,
  `description` varchar(255) NOT NULL DEFAULT '',
  `terms_text` text DEFAULT NULL,
  `discount_type` enum('fixed','percent') NOT NULL DEFAULT 'fixed',
  `discount_value` int(11) NOT NULL DEFAULT 0,
  `max_discount` int(11) NOT NULL DEFAULT 0,
  `min_order` int(11) NOT NULL DEFAULT 0,
  `points_cost` int(11) NOT NULL,
  `min_tier` enum('BRONZE','SILVER','GOLD','DIAMOND','PLATINUM') NOT NULL DEFAULT 'SILVER',
  `unlock_points` int(11) NOT NULL DEFAULT 500,
  `quota_total` int(11) NOT NULL DEFAULT 0,
  `quota_used` int(11) NOT NULL DEFAULT 0,
  `validity_days` int(11) NOT NULL DEFAULT 14,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `loyalty_reward_catalog`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `code` (`code`);
ALTER TABLE `loyalty_reward_catalog`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=4;
CREATE TABLE `loyalty_redeem_requests` (
  `user_id` bigint(20) NOT NULL,
  `request_key` varchar(80) NOT NULL,
  `redemption_id` bigint(20) UNSIGNED NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `loyalty_redeem_requests`
  ADD PRIMARY KEY (`user_id`,`request_key`);
CREATE TABLE `loyalty_voucher_snapshots` (
  `redemption_id` bigint(20) UNSIGNED NOT NULL,
  `terms_json` longtext NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `loyalty_voucher_snapshots`
  ADD PRIMARY KEY (`redemption_id`);
CREATE TABLE `loyalty_order_events` (
  `id` bigint(20) UNSIGNED NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `order_id` varchar(120) NOT NULL,
  `source_table` varchar(24) NOT NULL DEFAULT 'orders',
  `spend_amount` bigint(20) NOT NULL,
  `ordered_at` datetime NOT NULL,
  `completed_at` datetime NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `loyalty_order_events`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `uq_mission_order` (`source_table`,`order_id`),
  ADD KEY `idx_mission_user` (`user_id`,`ordered_at`,`completed_at`);
ALTER TABLE `loyalty_order_events`
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=48;
CREATE TABLE `pickup_fee_policy` (
  `id` tinyint(4) NOT NULL,
  `effective_from` datetime NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `pickup_fee_policy`
  ADD PRIMARY KEY (`id`);
INSERT INTO customer_coin_settings(id) VALUES (1);
INSERT INTO transiva_point_settings(id,migration_completed,cutover_at) VALUES (1,1,'2026-01-01');
INSERT INTO loyalty_season_settings(id,anchor_date) VALUES (1,'2026-01-01');
ALTER TABLE driver_dispatch_offers ADD batch_no INT NOT NULL DEFAULT 1,ADD dispatch_wave INT NOT NULL DEFAULT 1,ADD offered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,ADD cluster_id INT NOT NULL DEFAULT 0,ADD distance_km DECIMAL(8,3) NOT NULL DEFAULT 0;
ALTER TABLE driver_dispatch_history ADD offered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,ADD expired_at DATETIME NULL,ADD distance_km DECIMAL(8,3) NOT NULL DEFAULT 0,ADD id INT NOT NULL AUTO_INCREMENT PRIMARY KEY;
ALTER TABLE pickup_orders ADD COLUMN room_id VARCHAR(80), CHANGE status status ENUM('pending','taken','arrived_pickup','picked_up','on_delivery','arrived_delivery','finished','canceled') DEFAULT 'pending';

ALTER TABLE driver_profiles ADD id INT, ADD plate VARCHAR(40), ADD vehicle_photo TEXT, ADD location_accuracy DOUBLE, ADD location_speed DOUBLE, ADD location_bearing DOUBLE, ADD rating DECIMAL(4,2);
UPDATE driver_profiles SET id=user_id;
