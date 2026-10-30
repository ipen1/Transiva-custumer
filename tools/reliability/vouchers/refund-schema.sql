DROP TABLE IF EXISTS `voucher_usage_refunds`;
CREATE TABLE `voucher_usage_refunds` (
  `source_table` varchar(32) NOT NULL,
  `usage_id` bigint(20) UNSIGNED NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `order_id` varchar(120) NOT NULL,
  `discount_amount` int(11) NOT NULL DEFAULT 0,
  `refunded_at` datetime NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE `voucher_usage_refunds`
  ADD PRIMARY KEY (`source_table`,`usage_id`),
  ADD KEY `idx_voucher_refund_order` (`user_id`,`order_id`);