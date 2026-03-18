package com.smartfarm.server.repository;

import com.smartfarm.server.entity.SensorData;
import org.springframework.data.repository.CrudRepository;

/**
 * Spring Data Redis가 제공하는 CrudRepository 인터페이스를 상속받으면,
 * 런타임에 프록시(Proxy) 객체가 생성되어 기본적인 CRUD(Create, Read, Update, Delete) 
 * 메서드를 직접 구현하지 않고도 사용할 수 있습니다. 이는 JPA 스타일과 동일한 이점을 제공합니다.
 */
public interface SensorRedisRepository extends CrudRepository<SensorData, String> {
}
