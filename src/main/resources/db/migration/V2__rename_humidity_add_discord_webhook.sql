-- mem_usage_threshold_high, discord_webhook_url 컬럼은 Hibernate ddl-auto에 의해 이미 생성됨
-- humidity_threshold_high 고아 컬럼만 제거
ALTER TABLE device_config DROP COLUMN humidity_threshold_high;
