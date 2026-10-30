DROP TABLE IF EXISTS `order_attempts`;
CREATE TABLE `order_attempts` (
  `user_id` bigint(20) NOT NULL,
  `attempt_key` varchar(120) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `response_json` longtext NOT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `order_attempts`
  ADD PRIMARY KEY (`user_id`,`attempt_key`);
DROP TABLE IF EXISTS `customer_coin_ledger`;
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
DROP TABLE IF EXISTS `transiva_point_ledger`;
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
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=202;
DROP TABLE IF EXISTS `order_notification_outbox`;
CREATE TABLE `order_notification_outbox` (
  `id` bigint(20) UNSIGNED NOT NULL,
  `event_key` varchar(190) NOT NULL,
  `kind` varchar(32) NOT NULL,
  `payload_json` longtext NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'pending',
  `attempts` int(11) NOT NULL DEFAULT 0,
  `available_at` datetime NOT NULL DEFAULT current_timestamp(),
  `locked_at` datetime DEFAULT NULL,
  `sent_at` datetime DEFAULT NULL,
  `last_error` varchar(80) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `order_notification_outbox`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `event_key` (`event_key`),
  ADD KEY `idx_outbox_due` (`status`,`available_at`),
  ADD KEY `idx_outbox_lease` (`status`,`locked_at`);
ALTER TABLE `order_notification_outbox`
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=148;
DROP TABLE IF EXISTS `ride_server_routes`;
CREATE TABLE `ride_server_routes` (
  `order_db_id` bigint(20) NOT NULL,
  `route_json` longtext NOT NULL,
  `route_hash` char(64) NOT NULL,
  `version` bigint(20) NOT NULL DEFAULT 1,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `ride_server_routes`
  ADD PRIMARY KEY (`order_db_id`);
DROP TABLE IF EXISTS `ride_feature_meta`;
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
DROP TABLE IF EXISTS `ride_waypoints`;
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
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=21;
DROP TABLE IF EXISTS `order_cancellations`;
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
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=47;
DROP TABLE IF EXISTS `customer_hemat_usages`;
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
DROP TABLE IF EXISTS `customer_hemat_tier_limits`;
CREATE TABLE `customer_hemat_tier_limits` (
  `tier` varchar(20) NOT NULL,
  `monthly_limit` int(11) NOT NULL DEFAULT 0,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `customer_hemat_tier_limits`
  ADD PRIMARY KEY (`tier`);