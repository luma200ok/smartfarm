-- sensor_history: 더 이상 사용하지 않는 mem_usage 컬럼 제거
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name   = 'sensor_history'
      AND column_name  = 'mem_usage'
);
SET @sql = IF(@col_exists > 0,
    'ALTER TABLE sensor_history DROP COLUMN mem_usage',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
