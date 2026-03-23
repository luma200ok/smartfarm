-- 임계값 컬럼을 nullable로 변경 (null = 전역 yaml 기본값 사용)
ALTER TABLE device_config
    MODIFY COLUMN temperature_threshold_high DOUBLE NULL;

ALTER TABLE device_config
    MODIFY COLUMN mem_usage_threshold_high DOUBLE NULL;

-- 기존 기기를 전역 기본값 상속으로 초기화
-- (등록 시 yaml 기본값이 그대로 저장되어 있으므로 null로 리셋)
UPDATE device_config
SET temperature_threshold_high = NULL,
    mem_usage_threshold_high   = NULL;
