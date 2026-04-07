-- sensor_history: humidity 컬럼 추가
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name   = 'sensor_history'
      AND column_name  = 'humidity'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE sensor_history ADD COLUMN humidity DOUBLE NOT NULL DEFAULT 0.0',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- device_config: mem_usage_threshold_high 제거
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name   = 'device_config'
      AND column_name  = 'mem_usage_threshold_high'
);
SET @sql = IF(@col_exists > 0,
    'ALTER TABLE device_config DROP COLUMN mem_usage_threshold_high',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- device_config: temperature_threshold_low 추가
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name   = 'device_config'
      AND column_name  = 'temperature_threshold_low'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE device_config ADD COLUMN temperature_threshold_low DOUBLE NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- device_config: humidity_threshold_high 추가
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name   = 'device_config'
      AND column_name  = 'humidity_threshold_high'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE device_config ADD COLUMN humidity_threshold_high DOUBLE NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- device_config: humidity_threshold_low 추가
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name   = 'device_config'
      AND column_name  = 'humidity_threshold_low'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE device_config ADD COLUMN humidity_threshold_low DOUBLE NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
