-- 集成测试用的建表脚本（结构取自本机 travel_db 的真实 DDL）
-- 注意：容器里会自动建库，所以这里只写 CREATE TABLE，不写 CREATE DATABASE / USE

CREATE TABLE IF NOT EXISTS `t_user` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(50) NOT NULL COMMENT '登录名（电话号码）',
  `password_hash` varchar(100) NOT NULL COMMENT '密码哈希值',
  `nickname` varchar(50) DEFAULT NULL COMMENT '用户昵称',
  `avatar` varchar(255) DEFAULT NULL COMMENT '用户头像',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '用户状态，1-正常，0-禁用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`) COMMENT '用户名唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `t_trip` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `city` varchar(50) NOT NULL COMMENT '城市',
  `days` int NOT NULL COMMENT '天数',
  `budget_breakdown` json DEFAULT NULL COMMENT '预算明细',
  `tips` json DEFAULT NULL COMMENT '温馨提示',
  `warnings` json DEFAULT NULL COMMENT '注意事项',
  `daily_itinerary` json DEFAULT NULL COMMENT '行程计划',
  `total_budget` decimal(10,2) DEFAULT NULL COMMENT '预算金额',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='行程表';

CREATE TABLE IF NOT EXISTS `t_search` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL,
  `trip_id` bigint unsigned NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  `is_collected` tinyint NOT NULL DEFAULT '0' COMMENT '是否收藏，1-已收藏，0-未收藏',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_trip` (`user_id`,`trip_id`),
  KEY `idx_user_collected_created` (`user_id`,`is_collected`,`create_time`),
  KEY `fk_search_trip` (`trip_id`),
  CONSTRAINT `fk_search_trip` FOREIGN KEY (`trip_id`) REFERENCES `t_trip` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_search_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='搜索表';

CREATE TABLE IF NOT EXISTS `t_chat` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '用户id',
  `user_msg` text COMMENT '用户消息',
  `user_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '用户消息时间',
  `ai_msg` text COMMENT 'AI消息',
  `ai_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'AI消息时间',
  PRIMARY KEY (`id`),
  KEY `fk_chat_user` (`user_id`),
  CONSTRAINT `fk_chat_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
