SET FOREIGN_KEY_CHECKS=0;
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `id` int(11) NOT NULL,
  `username` varchar(50) NOT NULL,
  `email` varchar(150) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `profile_photo` varchar(255) DEFAULT NULL,
  `email_verified` tinyint(1) NOT NULL DEFAULT 0,
  `verified_by_admin` tinyint(1) NOT NULL DEFAULT 0,
  `email_verified_at` datetime DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `role` varchar(20) DEFAULT 'customer',
  `balance` bigint(20) DEFAULT 0,
  `restaurant_id` int(11) DEFAULT 0,
  `latitude` varchar(50) DEFAULT NULL,
  `longitude` varchar(50) DEFAULT NULL,
  `delivery_address` text DEFAULT NULL,
  `delivery_lat` varchar(50) DEFAULT NULL,
  `delivery_lng` varchar(50) DEFAULT NULL,
  `wisata_owner_id` int(11) DEFAULT NULL,
  `fcm_token` text DEFAULT NULL,
  `fcm_token_updated_at` datetime DEFAULT NULL,
  `fcm_updated_at` datetime DEFAULT NULL,
  `fcm_platform` varchar(40) DEFAULT NULL,
  `active_customer_device_id` bigint(20) UNSIGNED DEFAULT NULL,
  `device_binding_updated_at` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;
ALTER TABLE `users`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `username` (`username`),
  ADD UNIQUE KEY `uq_users_phone_role` (`phone`,`role`),
  ADD UNIQUE KEY `uq_users_email_role` (`email`,`role`),
  ADD KEY `idx_driver_ready` (`role`,`balance`),
  ADD KEY `idx_users_active_customer_device` (`active_customer_device_id`);
ALTER TABLE `users`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=127;
DROP TABLE IF EXISTS `orders`;
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
  ADD KEY `idx_orders_scheduled_status` (`status`,`scheduled_at`);
ALTER TABLE `orders`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=152;
DROP TABLE IF EXISTS `wallet_transactions`;
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
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=34;
DROP TABLE IF EXISTS `ride_vouchers`;
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
DROP TABLE IF EXISTS `ride_voucher_usages`;
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
DROP TABLE IF EXISTS `customer_promos`;
CREATE TABLE `customer_promos` (
  `id` int(10) UNSIGNED NOT NULL,
  `title` varchar(120) NOT NULL,
  `description` varchar(255) NOT NULL,
  `promo_code` varchar(80) NOT NULL DEFAULT '',
  `discount_type` varchar(20) NOT NULL DEFAULT 'percent',
  `discount_value` int(11) NOT NULL DEFAULT 0,
  `max_discount` int(11) DEFAULT NULL,
  `min_order` int(11) NOT NULL DEFAULT 0,
  `service_type` varchar(30) NOT NULL DEFAULT 'all',
  `quota_total` int(11) DEFAULT NULL,
  `per_user_limit` int(11) NOT NULL DEFAULT 1,
  `image_url` varchar(500) NOT NULL DEFAULT '',
  `action_url` text DEFAULT NULL,
  `target_role` varchar(30) NOT NULL DEFAULT 'all',
  `notification_sent_count` int(11) NOT NULL DEFAULT 0,
  `last_notified_at` datetime DEFAULT NULL,
  `theme_start` char(7) NOT NULL DEFAULT '#0759E8',
  `theme_end` char(7) NOT NULL DEFAULT '#18B5FF',
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `starts_at` datetime DEFAULT NULL,
  `ends_at` datetime DEFAULT NULL,
  `created_by` int(11) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `customer_promos`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_active_schedule` (`is_active`,`starts_at`,`ends_at`),
  ADD KEY `idx_sort` (`sort_order`,`id`);
ALTER TABLE `customer_promos`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=17;
DROP TABLE IF EXISTS `customer_promo_usages`;
CREATE TABLE `customer_promo_usages` (
  `id` bigint(20) UNSIGNED NOT NULL,
  `promo_id` bigint(20) UNSIGNED NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `order_id` varchar(120) NOT NULL,
  `discount_amount` int(11) NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `customer_promo_usages`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_promo_user` (`promo_id`,`user_id`),
  ADD KEY `idx_order` (`order_id`);
ALTER TABLE `customer_promo_usages`
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;
DROP TABLE IF EXISTS `customer_loyalty_redemptions`;
CREATE TABLE `customer_loyalty_redemptions` (
  `id` bigint(20) UNSIGNED NOT NULL,
  `user_id` int(11) NOT NULL,
  `reward_id` int(10) UNSIGNED NOT NULL,
  `voucher_code` varchar(64) NOT NULL,
  `points_cost` int(11) NOT NULL,
  `status` enum('issued','used','expired','cancelled') NOT NULL DEFAULT 'issued',
  `issued_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `expires_at` datetime DEFAULT NULL,
  `used_at` datetime DEFAULT NULL,
  `order_id` varchar(80) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
ALTER TABLE `customer_loyalty_redemptions`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `voucher_code` (`voucher_code`),
  ADD KEY `idx_user_status` (`user_id`,`status`),
  ADD KEY `idx_reward` (`reward_id`);
ALTER TABLE `customer_loyalty_redemptions`
  MODIFY `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;
DROP TABLE IF EXISTS `loyalty_reward_catalog`;
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
DROP TABLE IF EXISTS `loyalty_mission_settings`;
CREATE TABLE `loyalty_mission_settings` (
  `id` tinyint(4) NOT NULL,
  `effective_at` datetime NOT NULL,
  `transaction_points_enabled` tinyint(4) NOT NULL DEFAULT 0,
  `spend_per_point` int(11) NOT NULL DEFAULT 1000,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `loyalty_mission_settings`
  ADD PRIMARY KEY (`id`);
DROP TABLE IF EXISTS `loyalty_voucher_snapshots`;
CREATE TABLE `loyalty_voucher_snapshots` (
  `redemption_id` bigint(20) UNSIGNED NOT NULL,
  `terms_json` longtext NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `loyalty_voucher_snapshots`
  ADD PRIMARY KEY (`redemption_id`);
DROP TABLE IF EXISTS `split_bill_payments`;
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