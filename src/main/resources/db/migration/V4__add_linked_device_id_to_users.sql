-- 사용자 계정에 접근 가능한 기기 ID 연결 컬럼 추가
-- null = 전체 기기 접근 가능 (ROLE_ADMIN)
-- not-null = 해당 기기만 접근 가능 (ROLE_USER)
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name   = 'users'
      AND column_name  = 'linked_device_id'
);
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE users ADD COLUMN linked_device_id VARCHAR(100) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
