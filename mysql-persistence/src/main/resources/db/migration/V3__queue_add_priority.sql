ALTER TABLE `queue_message` ADD COLUMN IF NOT EXISTS `priority` TINYINT DEFAULT 0 AFTER `message_id`;
