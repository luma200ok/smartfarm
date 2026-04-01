-- sensor_history: humidity 컬럼 추가 (V5에서 제거했던 것을 다시 추가)
ALTER TABLE sensor_history
    ADD COLUMN humidity DOUBLE NOT NULL DEFAULT 0.0;

-- device_config: mem_usage_threshold_high 제거 후 4개 임계값 컬럼으로 교체
ALTER TABLE device_config
    DROP COLUMN mem_usage_threshold_high;

ALTER TABLE device_config
    ADD COLUMN temperature_threshold_low  DOUBLE NULL,
    ADD COLUMN humidity_threshold_high    DOUBLE NULL,
    ADD COLUMN humidity_threshold_low     DOUBLE NULL;
