# 🌱 SmartFarm Server

> 🎯 **"센서 데이터를 실시간으로 수집·분석하고, 임계값 초과 시 자동으로 장치를 제어하는 스마트팜 모니터링 백엔드"**
>
> 단순한 데이터 저장을 넘어, Redis와 MySQL의 이중 저장소 전략으로 고속 수집과 영구 보관을 동시에 달성했습니다.
>
> Server-Sent Events(SSE) 기반 실시간 푸시, 1분 단위 배치 집계, 임계값 초과 시 쿨링팬·가습기 자동 제어, Discord 알림까지 실제 운영 환경을 고려한 아키텍처를 구축했습니다.


🔗 **실제 서비스 접속해 보기:** [https://smartfarm.rkqkdrnportfolio.shop/](https://smartfarm.rkqkdrnportfolio.shop/)

🔗 **Swagger API**: [https://smartfarm.rkqkdrnportfolio.shop/swagger-ui/index.html](https://smartfarm.rkqkdrnportfolio.shop/swagger-ui/index.html)

🔗 **Discord Webhook**:[https://discord.gg/a9VhVFbqnR](https://discord.gg/a9VhVFbqnR)

🧪 **테스트 계정**
┣ ID: admin
┣ PW: admin1234
<br>

## 📋 목차

- [Tech Stack & Architecture](#-tech-stack--architecture)
- [주요 기술적 의사결정 및 트러블슈팅](#-주요-기술적-의사결정-및-트러블-슈팅)
- [API 엔드포인트](#-api-엔드포인트)
- [실행 방법](#-실행-방법)
- [센서 에이전트 (Python)](#-센서-에이전트-python)
- [예외 처리](#-예외-처리)

<br>

## 🛠️ Tech Stack & Architecture

### Tech Stack
* **Backend:** Java 21, Spring Boot 3.5, Spring Data JPA, Querydsl, Spring Security 6
* **Database & Cache:** MySQL 8, Redis, Flyway (DB 마이그레이션)
* **Realtime:** Server-Sent Events (SSE) — 대시보드 실시간 푸시 + PC 클라이언트 명령 스트림
* **Sensor Agent:** Python 3 (가상 온도·습도 데이터 생성 및 전송, systemd 서비스 운용)
* **Export:** Apache POI (Excel), CSV
* **Notification:** Discord Webhook (임계값 초과 알림, 2분 쿨다운)
* **Docs:** Springdoc OpenAPI (Swagger UI)
* **View:** Thymeleaf
* **CI/CD:** GitHub Actions + Blue-Green 배포 (Nginx 포트 전환)

### System Architecture

![smartfarm_architecture.png](docs/images/smartfarm_architecture.png)


* **Redis:** 3초마다 쏟아지는 센서 데이터를 메모리에 임시 버퍼링하여 DB 쓰기 부하 차단
* **MySQL:** 1분 평균값만 영구 저장하여 스토리지 효율 극대화
* **Spring Cache (Redis 백엔드):** 기기별 임계값 설정을 캐시하여 매 요청마다 발생하는 DB 조회 제거
* **SSE (이중 채널):** 브라우저 대시보드용 실시간 센서 스트림 + PC 클라이언트용 제어 명령 스트림을 분리하여 운용
* **자동 제어:** 온도 상한 초과 시 쿨링팬 ON, 하한 이하 시 OFF / 습도 상한 초과 시 가습기 ON, 하한 이하 시 OFF

<br>

## 💡 주요 기술적 의사결정 및 트러블 슈팅

### 1. ⚡ Redis + MySQL 이중 저장소 전략 (Write-Buffer + Batch Flush)
> **의사결정:** 3초마다 발생하는 고빈도 쓰기 요청을 MySQL이 직접 받지 않도록 Redis를 중간 버퍼로 사용하고, 1분 단위 배치로 집계 후 저장

* **🚨 Issue:** 기기에서 3초마다 온도·습도 데이터를 전송할 경우, 기기 수가 늘어날수록 MySQL에 초당 수십 건의 INSERT가 발생해 DB 병목이 불가피
* **💡 Resolution:**
  * **Redis 임시 버퍼:** 수신 즉시 Redis에 저장 (기기당 최신 1건만 유지, 60초 TTL)
  * **Batch Flush:** `@Scheduled`로 1분마다 Redis 전체 데이터를 읽어 deviceId별 평균값을 계산한 뒤 MySQL에 1행만 삽입
  * **메모리 해제:** 배치 완료 후 Redis 데이터 즉시 삭제
* **📈 성과:** DB INSERT 횟수를 기기당 분당 20건 → 1건으로 절감 (기기 10대 기준 약 95% 감소)

<br>

### 2. 🔄 Spring Cache를 활용한 기기 설정 캐싱
> **의사결정:** 센서 데이터 수신마다 기기별 임계값을 DB에서 조회하는 반복 I/O를 제거하고자 Redis 백엔드 기반 `@Cacheable` 적용

* **🚨 Issue:** 매 센서 요청마다 DeviceConfig를 DB에서 조회하면, 기기 수 증가 시 불필요한 SELECT 쿼리가 선형으로 증가
* **💡 Resolution:**
  * **`@Cacheable`:** 기기 설정 최초 조회 시 Redis에 캐싱하여 이후 요청은 DB 접근 없이 처리
  * **`@CacheEvict`:** 기기 설정 변경/삭제 시 캐시 즉시 무효화하여 데이터 정합성 유지
* **📈 성과:** 설정 조회 쿼리를 캐시 히트 시 0회로 감소, 임계값 판정 로직의 응답 속도 개선

<br>

### 3. 📡 Server-Sent Events(SSE) 기반 실시간 모니터링
> **의사결정:** 브라우저에서 최신 센서 데이터를 보여주기 위해 폴링 대신 SSE로 서버 주도 푸시 방식 채택

* **🚨 Issue:** 3초마다 브라우저가 API를 폴링하면 불필요한 HTTP 오버헤드가 발생하고, 다중 탭에서 중복 요청이 급증
* **💡 Resolution:**
  * **SSE 구독:** 브라우저가 `/api/sse/subscribe?deviceId=X`로 연결을 맺으면 서버가 데이터 수신 시 자동으로 푸시
  * **다중 탭 지원:** `ConcurrentHashMap<deviceId, CopyOnWriteArrayList<SseEmitter>>` 구조로 동일 기기를 구독 중인 모든 탭에 동시 푸시
  * **30분 타임아웃:** 장시간 연결 유지 시 리소스 누수 방지
* **📈 성과:** 클라이언트 요청 제거로 서버 부하 감소, 센서 수신 즉시 브라우저 반영

<br>

### 4. 🧹 소프트 딜리트 & 데이터 생명주기 관리
> **의사결정:** 기기 삭제 시 관련 이력 데이터를 즉시 하드 삭제하지 않고 소프트 딜리트 후 스케줄러로 단계적 영구 삭제

* **🚨 Issue:** 기기 삭제 직후 대량의 SensorHistory를 한 번에 하드 삭제하면 DB 락 및 응답 지연이 발생할 위험
* **💡 Resolution:**
  * **소프트 딜리트:** 기기 삭제 시 관련 SensorHistory의 `deletedAt` 필드에 타임스탬프만 기록
  * **1주일 후 하드 삭제:** 매일 03:00 스케줄러가 `deletedAt` 기준 7일 경과 데이터를 배치 삭제
  * **1개월 후 만료 삭제:** 매일 02:00 스케줄러가 오래된 이력 데이터를 자동 정리
* **📈 성과:** 기기 삭제 응답 시간 단축, DB 부하 분산 및 스토리지 자동 관리

<br>

### 5. 🚀 Blue-Green 무중단 배포 트러블슈팅
> **상황:** GitHub Actions CI/CD에서 Blue-Green 배포 시 Nginx 포트 전환이 되지 않고 구버전·신버전 서버가 동시에 떠있는 문제 발생

* **🚨 Issue:** 신버전 서버(8083)가 기동됐음에도 Nginx가 구버전(8084)을 계속 바라보고, 구버전 프로세스가 종료되지 않는 현상
* **💡 원인 분석:**
  * 헬스체크 `curl`에 타임아웃 옵션이 없어, 서버가 포트를 열기 전까지 `curl`이 무한 대기 상태 진입
  * SSH 액션 기본 타임아웃(10분) 초과 → 스크립트 강제 종료 → Nginx 전환·구버전 종료 로직이 실행되지 않음
  * 신버전 프로세스는 `nohup &` 백그라운드 실행이라 SSH 세션 종료 후에도 좀비 프로세스로 잔존
* **💡 Resolution:**
  * 헬스체크 `curl`에 `--connect-timeout 3 --max-time 5` 옵션 추가로 빠른 실패 후 재시도 처리
* **📈 성과:** 헬스체크 실패 시 즉시 다음 재시도로 넘어가 SSH 타임아웃 이내에 배포 완료, 포트 전환 정상화

<br>

### 6. 🌡️ 임계값 기반 자동 제어 아키텍처
> **의사결정:** 온도·습도가 설정 임계값을 벗어나면 서버가 PC 클라이언트에 제어 명령을 자동 발송하고, 클라이언트는 명령을 수행 후 ACK를 반환

* **🚨 Issue:** 임계값 초과 시 관리자가 직접 대응해야 하고, PC 클라이언트가 SSE 미연결 상태일 때 명령이 유실될 위험
* **💡 Resolution:**
  * **자동 제어 흐름:** 센서 수신 → 임계값 비교 → `DeviceControlCommand` DB 저장(PENDING) → SSE로 즉시 푸시
  * **SSE 미연결 대비:** SSE 푸시 실패 시 명령을 DB PENDING 상태로 보존, 클라이언트 재연결 시 `/api/device-control/pending` 폴링으로 수령
  * **중복 방지:** 동일 명령이 이미 PENDING이면 재발송 생략, 반대 명령(ON↔OFF)이 PENDING이면 자동 취소
  * **ACK 처리:** 클라이언트가 명령 실행 후 `/api/device-control/ack` 호출 → 상태를 ACKNOWLEDGED로 변경하고 대시보드에 실시간 푸시
* **📈 성과:** 명령 유실 없는 신뢰성 보장, 자동·수동 제어 이력 DB 기록으로 감사 추적 가능

<br>

### 7. 🔀 SSE 이중 채널 분리 (대시보드 vs PC 클라이언트)
> **의사결정:** 브라우저 대시보드와 PC 클라이언트가 SSE를 각각 다른 목적으로 사용하므로 채널을 분리하여 설계

* **🚨 Issue:** 단일 SSE 엔드포인트로 센서 데이터와 제어 명령을 모두 전송하면 클라이언트 종류에 따라 불필요한 이벤트를 수신하고 인증 정책도 달라짐
* **💡 Resolution:**
  * **`/api/sse/subscribe`** — 브라우저 전용. Spring Security 인증 필요. `sensor` 이벤트로 실시간 온도·습도·제어 상태 전송
  * **`/api/sse/device-command-stream`** — PC 클라이언트 전용. API 키 인증. `command` 이벤트로 제어 명령만 전송
  * **에미터 관리 분리:** 대시보드 에미터는 `ConcurrentHashMap<deviceId, CopyOnWriteArrayList>` (다중 탭 지원), PC 클라이언트 에미터는 기기당 1개 유지
* **📈 성과:** 채널별 독립적인 인증·타임아웃 정책 적용 가능, 불필요한 이벤트 수신 제거

<br>

### 8. 🗄️ Flyway 마이그레이션 도입 (기존 운영 DB 대응)
> **상황:** JPA auto-DDL로 운영하던 DB에 Flyway를 사후 도입하면서, `flyway_schema_history` 테이블이 없는 상태에서 마이그레이션 실패 발생

* **🚨 Issue:** EC2 MySQL에 `flyway_schema_history` 테이블이 없어 Flyway가 V7 마이그레이션을 실행하려 하지만, 일부 컬럼은 이미 존재해 `Duplicate column` 에러 발생
* **💡 Resolution:**
  * **`baseline-on-migrate: true`** — flyway_schema_history 테이블이 없으면 자동 생성하고 baseline 버전 이하를 적용 완료로 표시
  * **`baseline-version: 7`** — V7까지는 이미 반영된 것으로 처리하여 재실행 방지
  * **수동 컬럼 추가:** 이미 존재하는 컬럼은 MySQL CLI로 직접 추가 후 baseline 설정 적용
* **📈 성과:** 기존 운영 DB를 건드리지 않고 Flyway 이력 관리 체계 합류, 이후 마이그레이션은 자동화

<br>

### 9. 📊 Querydsl 기반 통계 쿼리 최적화
> **의사결정:** 오늘의 최고·최저·평균 통계를 애플리케이션 레이어에서 계산하지 않고 DB에 위임하기 위해 Querydsl 도입

* **🚨 Issue:** JPA 메서드 네이밍만으로 집계(max, min, avg)와 날짜 범위 필터를 조합한 동적 쿼리를 표현하기 어려움
* **💡 Resolution:**
  * **Querydsl Projections:** `QSensorStatisticsDto`로 집계 결과를 DTO에 직접 매핑, 엔티티 불필요 조회 제거
  * **BooleanExpression:** 날짜 범위, deviceId 조건을 타입 안전하게 조합
  * **Repository-Custom-Impl 3단 구조:** JPA 인터페이스와 Querydsl 구현체를 분리하여 유지보수성 확보
* **📈 성과:** 집계 로직 DB 위임으로 애플리케이션 메모리 부하 절감, 컴파일 타임 쿼리 검증으로 런타임 오류 사전 차단

<br>

## 📌 API 엔드포인트

### 센서
| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| POST | `/api/sensor/data` | API 키 | 센서 데이터 수신 (온도·습도) |

### 기기 등록
| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| POST | `/api/device/register` | ❌ | 신규 기기 등록 및 API 키 발급 |

### 기기 설정
| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| GET | `/api/device-config` | ✅ | 전체 기기 목록 조회 |
| GET | `/api/device-config/{deviceId}` | ✅ | 특정 기기 설정 조회 |
| POST | `/api/device-config` | ✅ | 기기 설정 저장/수정 (임계값 포함) |
| DELETE | `/api/device-config/{deviceId}` | ✅ | 기기 삭제 |

### 기기 제어
| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| POST | `/api/device-control/command` | ✅ ADMIN | 수동 제어 명령 발송 |
| GET | `/api/device-control/pending` | API 키 | PC 클라이언트 PENDING 명령 폴링 |
| POST | `/api/device-control/ack` | API 키 | 명령 실행 완료 ACK 전송 |
| GET | `/api/device-control/history` | ✅ ADMIN | 제어 명령 이력 조회 |

### 대시보드
| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| GET | `/api/dashboard/history` | ✅ | 센서 이력 페이징 조회 |
| GET | `/api/dashboard/statistics/today` | ✅ | 오늘 통계 조회 |
| GET | `/api/dashboard/export/csv` | ✅ | CSV 내보내기 |
| GET | `/api/dashboard/export/excel` | ✅ | Excel 내보내기 |

### 실시간 (SSE)
| Method | URI | 인증 | 설명 |
|--------|-----|------|------|
| GET | `/api/sse/subscribe` | ✅ | 브라우저 대시보드 구독 (`sensor` 이벤트) |
| GET | `/api/sse/device-command-stream` | API 키 | PC 클라이언트 제어 명령 수신 (`command` 이벤트) |

> 📄 **Swagger UI:** `http://localhost:8080/swagger-ui/index.html`

<br>

## ⚙️ 실행 방법

### 사전 요구사항
* Java 21
* MySQL 8 — `localhost:3306` / DB: `smartfarm`
* Redis — `localhost:6379` (운영) / `localhost:6380` (로컬, `application-local.yaml` 기본값)

### 환경변수
| 변수 | 설명 |
|------|------|
| `DISCORD_WEBHOOK_URL` | Discord 알림 웹훅 URL (미설정 시 알림 비활성화) |
| `DB_HOST` | (운영) MySQL 호스트 |
| `DB_USERNAME` | (운영) MySQL 계정 |
| `DB_PASSWORD` | (운영) MySQL 비밀번호 |
| `REDIS_HOST` | (운영) Redis 호스트 |

### 실행
```bash
./gradlew bootRun
```

### 테스트 계정
애플리케이션 최초 시작 시 자동 생성됩니다.

| 항목 | 값 |
|------|----|
| username | `admin` |
| password | `admin1234` |

> ⚠️ 로컬 개발 및 테스트 전용 계정입니다. 실제 운영 환경에서는 반드시 변경하세요.

<br>

## 🐍 센서 에이전트 (Python)

`src/scripts/sensor_agent.py` — 가상 온도·습도 데이터를 생성하여 3초마다 서버에 전송하고, SSE로 제어 명령을 수신·실행하는 PC 클라이언트입니다.

### 사전 요구사항
```bash
pip install requests sseclient-py python-dotenv
```

### 환경변수 (`.env`)
```
DEVICE_ID=my-device-001
API_KEY=              # 최초 실행 시 자동 발급 후 저장
```

### 실행
```bash
python sensor_agent.py
```

최초 실행 시 서버에 기기를 자동 등록하고 API 키를 `.env`에 저장합니다.

### 운영 환경 (systemd)
```bash
# 서비스 등록 및 시작
sudo cp sensor-agent.service /etc/systemd/system/smartfarm-sensor-agent.service
sudo systemctl daemon-reload
sudo systemctl enable smartfarm-sensor-agent
sudo systemctl start smartfarm-sensor-agent

# 로그 확인
journalctl -u smartfarm-sensor-agent -f
```

<br>

## 🚨 예외 처리

| 코드 | HTTP | 설명 |
|------|------|------|
| E001 | 400 | 입력값 오류 |
| E002 | 400 | 기기 없음 |
| S001 | 500 | 서버 내부 오류 |
| S002 | 500 | 데이터베이스 오류 |

---

최근 업데이트 2026.04.01 — README V2.0.0
