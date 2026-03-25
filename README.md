# SmartFarm Server

IoT 기반 스마트팜 모니터링 & 역제어 백엔드 서비스입니다.
기기(PC)에서 3초마다 전송하는 온도/습도 데이터를 실시간으로 수집하고, 임계값 초과 시 자동으로 냉각/난방 장치를 제어합니다.

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.5.11 |
| ORM | Spring Data JPA + Querydsl |
| Database | MySQL, Redis |
| Security | Spring Security 6 |
| Realtime | Server-Sent Events (SSE) |
| Export | Apache POI (Excel), CSV |
| Notification | Discord Webhook |
| Docs | Springdoc OpenAPI (Swagger) |
| View | Thymeleaf |

---

## 아키텍처

```
기기(PC)
   │  3초마다 POST /api/sensor/data
   ▼
SensorService
   ├─ 유효성 검사
   ├─ Redis 저장 (임시)
   ├─ 임계치 초과 → ControlEventLog 기록
   ├─ Discord 알림
   └─ SSE 실시간 푸시
   │
   │  1분마다 배치
   ▼
DataBatchService
   ├─ Redis 데이터 집계 (기기별 평균값 계산)
   └─ MySQL 저장 (SensorHistory)
   │
   ▼
Dashboard / Export
   ├─ 이력 조회 (페이징)
   ├─ 오늘 통계 (최고/최저/평균)
   └─ CSV / Excel 내보내기
```

---

## 주요 기능

### 센서 데이터 수신 & 역제어
- 기기에서 3초 간격으로 온도/습도 수신
- 기기별 임계값(온도/습도) 개별 설정 가능
- 임계값 초과 시 자동 판정
  - 온도 초과 → `COOLING_FAN_ON`
  - 습도 초과 → `HEATER_ON`

### 실시간 모니터링 (SSE)
- Server-Sent Events로 브라우저에 실시간 데이터 푸시
- 다중 탭/브라우저 지원

### 배치 처리
- 매 1분: Redis 임시 데이터를 집계하여 MySQL에 영구 저장
- 매일 02:00: 1개월 이상 된 이력 데이터 자동 삭제
- 매일 03:00: 소프트 삭제 후 1주일 경과 데이터 하드 삭제

### 데이터 관리
- 기기 등록/삭제 (삭제 시 관련 이력 소프트 딜리트)
- 이력 조회 (페이징, 날짜 범위 필터)
- 오늘 통계 조회 (최고/최저/평균 온도·습도)
- CSV / Excel 내보내기 (한글 지원)

### 알림
- 임계값 초과 시 Discord 웹훅으로 알림 전송

---

## API 엔드포인트

### 센서
| Method | URI | 설명 |
|--------|-----|------|
| POST | `/api/sensor/data` | 센서 데이터 수신 (인증 불필요) |

### 기기 설정
| Method | URI | 설명 |
|--------|-----|------|
| GET | `/api/device-config` | 전체 기기 목록 조회 |
| GET | `/api/device-config/{deviceId}` | 특정 기기 설정 조회 |
| POST | `/api/device-config` | 기기 설정 저장/수정 |
| DELETE | `/api/device-config/{deviceId}` | 기기 삭제 |

### 대시보드
| Method | URI | 설명 |
|--------|-----|------|
| GET | `/api/dashboard/history` | 센서 이력 페이징 조회 |
| GET | `/api/dashboard/statistics/today` | 오늘 통계 조회 |
| GET | `/api/dashboard/export/csv` | CSV 내보내기 |
| GET | `/api/dashboard/export/excel` | Excel 내보내기 |

### 실시간
| Method | URI | 설명 |
|--------|-----|------|
| GET | `/api/sse/subscribe` | SSE 구독 |

> Swagger UI: `http://localhost:8080/swagger-ui/index.html`

---

## 데이터 구조

### SensorData (Redis — 임시)
| 필드 | 타입 | 설명 |
|------|------|------|
| deviceId | String | 기기 식별자 (PK) |
| temperature | Double | 온도 |
| humidity | Double | 습도 |
| timestamp | LocalDateTime | 측정 시각 |

### SensorHistory (MySQL — 영구)
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| deviceId | String | 기기 식별자 |
| temperature | Double | 1분 평균 온도 |
| humidity | Double | 1분 평균 습도 |
| timestamp | LocalDateTime | 저장 시각 |
| deletedAt | LocalDateTime | 소프트 삭제 일시 |

### DeviceConfig (MySQL)
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | PK |
| deviceId | String | 기기 식별자 (unique) |
| temperatureThresholdHigh | Double | 온도 임계값 |
| humidityThresholdHigh | Double | 습도 임계값 |

---

## 설정

`src/main/resources/application.yaml`

```yaml
sensor:
  threshold:
    temperature: 70    # 기본 온도 임계값 (°C)
    humidity: 90       # 기본 습도 임계값 (%)
  validation:
    temperature:
      min: -50
      max: 150
    humidity:
      min: 0
      max: 100
  batch:
    interval: 60       # 배치 주기 (초)

discord:
  webhook:
    url: ${DISCORD_WEBHOOK_URL}
```

### 환경변수

| 변수 | 설명 |
|------|------|
| `DISCORD_WEBHOOK_URL` | Discord 알림 웹훅 URL |

---

## 실행 방법

### 사전 요구사항
- Java 21
- MySQL (포트: 3306, DB: `smartfarm`)
- Redis (포트: 6380)

### 실행

```bash
./gradlew bootRun
```

### 초기 관리자 계정
애플리케이션 시작 시 자동 생성됩니다.

| 항목 | 값 |
|------|----|
| username | `admin` |
| password | `admin1234` |

---

## 예외 처리

| 코드 | HTTP | 설명 |
|------|------|------|
| E001 | 400 | 입력값 오류 |
| E002 | 400 | 기기 없음 |
| S001 | 500 | 서버 내부 오류 |
| S002 | 500 | 데이터베이스 오류 |
