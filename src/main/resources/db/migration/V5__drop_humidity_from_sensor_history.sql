-- sensor_history 테이블의 humidity 컬럼 제거.
-- SensorHistory 엔티티에 humidity 필드가 없어 INSERT 시 "Field 'humidity' doesn't have a default value" 오류 발생.
ALTER TABLE sensor_history
    DROP COLUMN humidity;
