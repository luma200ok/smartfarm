-- sensor_history: humidity 컬럼 추가 (이미 존재하면 무시)
ALTER TABLE sensor_history ADD COLUMN IF NOT EXISTS humidity DOUBLE NOT NULL DEFAULT 0.0;

-- device_config: mem_usage_threshold_high 제거 (존재하는 경우에만)
ALTER TABLE device_config DROP COLUMN IF EXISTS mem_usage_threshold_high;

-- device_config: 임계값 컬럼 추가 (이미 존재하면 무시)
ALTER TABLE device_config ADD COLUMN IF NOT EXISTS temperature_threshold_low  DOUBLE NULL;
ALTER TABLE device_config ADD COLUMN IF NOT EXISTS humidity_threshold_high    DOUBLE NULL;
ALTER TABLE device_config ADD COLUMN IF NOT EXISTS humidity_threshold_low     DOUBLE NULL;
