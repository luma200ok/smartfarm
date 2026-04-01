-- sensor_history: humidity 컬럼 추가
ALTER TABLE sensor_history ADD COLUMN humidity DOUBLE NOT NULL DEFAULT 0.0;

-- device_config: mem_usage_threshold_high 제거
ALTER TABLE device_config DROP COLUMN mem_usage_threshold_high;

-- device_config: 임계값 컬럼 추가
ALTER TABLE device_config ADD COLUMN temperature_threshold_low  DOUBLE NULL;
ALTER TABLE device_config ADD COLUMN humidity_threshold_high    DOUBLE NULL;
ALTER TABLE device_config ADD COLUMN humidity_threshold_low     DOUBLE NULL;
