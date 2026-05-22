CREATE TABLE `Counters` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `count` int(11) NOT NULL DEFAULT '1',
  `createdAt` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updatedAt` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `sci_users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_key` varchar(128) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sci_users_user_key` (`user_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `coupon_codes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `total_times` int NOT NULL DEFAULT 1,
  `redeemed_times` int NOT NULL DEFAULT 0,
  `expires_at` datetime DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coupon_codes_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `share_links` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `share_token` varchar(128) NOT NULL,
  `grant_times` int NOT NULL DEFAULT 1,
  `max_redeems` int NOT NULL DEFAULT 1,
  `redeemed_times` int NOT NULL DEFAULT 0,
  `expires_at` datetime DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_share_links_token` (`share_token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `plot_access` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `access_token` varchar(64) NOT NULL,
  `source_type` varchar(16) NOT NULL,
  `source_id` bigint DEFAULT NULL,
  `total_times` int NOT NULL,
  `remaining_times` int NOT NULL,
  `expires_at` datetime DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plot_access_token` (`access_token`),
  KEY `idx_plot_access_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `uploaded_files` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `task_id` bigint DEFAULT NULL,
  `original_name` varchar(255) NOT NULL,
  `extension` varchar(16) NOT NULL,
  `mime_type` varchar(128) DEFAULT NULL,
  `size_bytes` bigint NOT NULL,
  `storage_path` varchar(512) NOT NULL,
  `normalized_path` varchar(512) DEFAULT NULL,
  `parse_status` varchar(16) NOT NULL,
  `parse_summary` json DEFAULT NULL,
  `error_message` text,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_uploaded_files_user` (`user_id`),
  KEY `idx_uploaded_files_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `plot_tasks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `access_id` bigint NOT NULL,
  `upload_file_id` bigint DEFAULT NULL,
  `plot_type` varchar(32) NOT NULL,
  `output_format` varchar(16) NOT NULL DEFAULT 'png',
  `status` varchar(16) NOT NULL,
  `progress` int NOT NULL DEFAULT 0,
  `options_json` json DEFAULT NULL,
  `parse_summary` json DEFAULT NULL,
  `error_message` text,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_plot_tasks_user` (`user_id`),
  KEY `idx_plot_tasks_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `plot_result_resources` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL,
  `resource_type` varchar(32) NOT NULL,
  `format` varchar(16) NOT NULL,
  `storage_path` varchar(512) NOT NULL,
  `access_url` varchar(512) NOT NULL,
  `size_bytes` bigint DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_plot_result_resources_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `coupon_codes` (`code`, `total_times`, `expires_at`, `status`)
VALUES ('SCIDRAW-DEMO', 3, DATE_ADD(NOW(), INTERVAL 30 DAY), 'ACTIVE')
ON DUPLICATE KEY UPDATE `code`=`code`;

INSERT INTO `coupon_codes` (`code`, `total_times`, `expires_at`, `status`)
VALUES
  ('SCIDRAW-20-7K4M2Q', 20, DATE_ADD(NOW(), INTERVAL 180 DAY), 'ACTIVE'),
  ('SCIDRAW-20-P9X3LA', 20, DATE_ADD(NOW(), INTERVAL 180 DAY), 'ACTIVE'),
  ('SCIDRAW-20-M6R8TN', 20, DATE_ADD(NOW(), INTERVAL 180 DAY), 'ACTIVE'),
  ('SCIDRAW-20-H2V5CZ', 20, DATE_ADD(NOW(), INTERVAL 180 DAY), 'ACTIVE'),
  ('SCIDRAW-20-Q8B1WY', 20, DATE_ADD(NOW(), INTERVAL 180 DAY), 'ACTIVE')
ON DUPLICATE KEY UPDATE `code`=`code`;

INSERT INTO `share_links` (`share_token`, `grant_times`, `max_redeems`, `expires_at`, `status`)
VALUES ('share-demo-token', 1, 100, DATE_ADD(NOW(), INTERVAL 30 DAY), 'ACTIVE')
ON DUPLICATE KEY UPDATE `share_token`=`share_token`;
