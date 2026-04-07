-- sensor_history 테이블의 humidity 컬럼 제거.
-- SensorHistory 엔티티에 humidity 필드가 없어 INSERT 시 "Field 'humidity' doesn't have a default value" 오류 발생.
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name   = 'sensor_history'
      AND column_name  = 'humidity'
);
SET @sql = IF(@col_exists > 0,
    'ALTER TABLE sensor_history DROP COLUMN humidity',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
