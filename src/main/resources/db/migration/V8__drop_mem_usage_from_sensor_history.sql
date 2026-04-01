-- sensor_history: 더 이상 사용하지 않는 mem_usage 컬럼 제거
ALTER TABLE sensor_history DROP COLUMN mem_usage;
