-- Phase 1: humidity_threshold_high → mem_usage_threshold_high
ALTER TABLE device_config
    CHANGE COLUMN humidity_threshold_high mem_usage_threshold_high DOUBLE NOT NULL;

-- Phase 2: 기기별 Discord 웹훅 URL 컬럼 추가
ALTER TABLE device_config
    ADD COLUMN discord_webhook_url VARCHAR(500) NULL;
