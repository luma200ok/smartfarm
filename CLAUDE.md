# Smartfarm Server — CLAUDE.md

## 프로젝트 개요

PC 클라이언트(Python)가 주기적으로 CPU 온도/메모리 사용률을 전송하면 Redis에 임시 저장하고, 배치로 MySQL에 이관하는 스마트팜 PC 모니터링 백엔드 서버.

## 기술 스택

- **언어/플랫폼**: Java 21, Spring Boot 3.5
- **데이터**: Spring Data JPA, Spring Data Redis, MySQL, H2(테스트)
- **쿼리**: QueryDSL 5.0 (Jakarta)
- **웹**: Spring MVC, Thymeleaf, Spring Security
- **API 문서**: springdoc-openapi 2.8.x (`/swagger-ui.html`)
- **빌드**: Gradle (Groovy DSL)
- **유틸**: Lombok

## 데이터 흐름

```
PC 클라이언트(Python)
  → POST /api/sensor/data (3초마다)
  → Redis 임시 저장 (SensorData)
  → 1분 배치 (DataBatchService) → MySQL 평균값 이관 (SensorHistory)
  → 임계값 초과 시 Discord 웹훅 알림 (DiscordNotificationService)
```

## 패키지 구조

```
com.smartfarm.server
├── config/          # QuerydslConfig, SwaggerConfig
├── controller/      # SensorController, DashboardController, WebController
├── dto/             # SensorRequestDto, SensorResponseDto, SensorHistoryResponseDto, SensorStatisticsDto
├── entity/          # SensorData(Redis), SensorHistory(MySQL), DeviceConfig(MySQL+Redis캐시), ControlEventLog(MySQL)
├── exception/       # GlobalExceptionHandler, CustomException, ErrorCode(enum), ErrorResponse
├── repository/      # SensorRedisRepository, SensorHistoryRepository, SensorHistoryRepositoryCustom(QueryDSL), DeviceConfigRepository, ControlEventLogRepository
└── service/         # SensorService, DataBatchService, DashboardService, DeviceConfigService, DiscordNotificationService
```

## 레이어 규칙

- **Controller → Service → Repository** 단방향 의존
- Controller는 DTO만 반환, 엔티티를 직접 노출하지 않음
- 예외는 `CustomException(ErrorCode)` 를 throw, `GlobalExceptionHandler` 가 일괄 처리
- 새 에러 케이스는 `ErrorCode` enum에 추가 후 `CustomException` 으로 래핑

## 빌드 및 실행

```bash
./gradlew build          # 전체 빌드 (Q클래스 자동 생성 포함)
./gradlew test           # 테스트
./gradlew bootRun        # 로컬 실행 (application-local.yaml 활성화 필요)
```

QueryDSL Q클래스 생성 경로: `build/generated/querydsl`

## 환경 프로파일

| 프로파일 | 설정 파일 | 설명 |
|---------|-----------|------|
| `local` | `application-local.yaml` | 로컬 개발 (H2 또는 로컬 MySQL/Redis) |
| `prod`  | `application-prod.yaml`  | 운영 환경 |

## 엔티티 요약

| 엔티티 | 저장소 | 설명 |
|--------|--------|------|
| `SensorData` | Redis | PC에서 수신한 실시간 센서값 임시 저장 |
| `SensorHistory` | MySQL | 1분 평균값 영구 저장 |
| `DeviceConfig` | MySQL + Redis 캐시 | 기기별 임계값 설정 |
| `ControlEventLog` | MySQL | 임계값 초과 이벤트 이력 |

## 주요 API

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/sensor/data` | PC 클라이언트 센서 데이터 수신 |
| GET | `/swagger-ui.html` | Swagger API 문서 |
| GET | `/dashboard` | Thymeleaf 대시보드 (인증 필요) |

## 코딩 규칙

- Lombok(`@RequiredArgsConstructor`) 생성자 주입 사용, 필드 주입(`@Autowired`) 금지
- 복잡한 통계/집계 쿼리는 `SensorHistoryRepositoryCustom` (QueryDSL) 에 작성
- 배치 인서트는 `DataBatchService` 에서 처리, 단건 루프 insert 금지
- Discord 알림은 `DiscordNotificationService` 에서만 발송
