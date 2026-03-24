# 센서 에이전트 설치 및 배포 가이드

EC2 서버에서 CPU 사용률과 메모리 사용률을 실시간으로 수집하여 Smartfarm 서버로 전송합니다.

## 1. 전제 조건

- EC2 인스턴스 (Amazon Linux 2 / Ubuntu 권장)
- Python 3.7+
- pip 설치 완료

## 2. 필수 패키지 설치

```bash
# 1단계: 저장소 디렉토리 생성
mkdir -p /home/ec2-user/smartfarm-sensor-agent
cd /home/ec2-user/smartfarm-sensor-agent

# 2단계: 센서 에이전트 코드 다운로드
# (스크립트를 EC2로 업로드: senesor_agent.py와 requirements.txt)

# 3단계: Python 패키지 설치
pip3 install -r requirements.txt
```

## 3. 환경 설정 (.env 파일)

```bash
# .env 파일 생성
cp .env.example .env

# DEVICE_ID를 고유한 값으로 변경
# 예: EC2_PROD_01, production-monitor, 등
nano .env
```

`.env` 파일 예시:
```
DEVICE_ID=EC2_PROD_01
API_KEY=
```

**중요:**
- `DEVICE_ID`: EC2 인스턴스마다 고유한 값을 설정하세요
- `API_KEY`: 처음에는 비워두세요. 스크립트가 자동으로 서버에 등록하고 저장합니다

## 4. 수동 테스트 (선택사항)

배포 전에 센서 에이전트가 정상 작동하는지 확인합니다:

```bash
cd /home/ec2-user/smartfarm-sensor-agent
python3 senesor_agent.py
```

정상 작동 시 출력:
```
--- 스마트팜 센서 에이전트 시작 (deviceId: EC2_PROD_01, server: http://smartfarm.rkqkdrnportfolio.shop) ---
[INIT] API 키 없음 — 서버에 기기 등록을 요청합니다. (deviceId: EC2_PROD_01)
[INIT] 기기 등록 완료! API 키를 .env 에 저장했습니다.
  └─ deviceId  : EC2_PROD_01
  └─ apiKey    : <발급받은 API 키>
  └─ .env 경로 : /home/ec2-user/smartfarm-sensor-agent/.env
[SSE] 명령 스트림 연결 시도 → http://smartfarm.rkqkdrnportfolio.shop/api/sse/device-command-stream?deviceId=EC2_PROD_01
[SSE] 명령 스트림 연결 성공
[SSE] 서버 핸드셰이크 완료
[16:30:45] ✅ 전송 완료 (CPU: 12.5%, 메모리: 45.2%)
  └─ 서버 상태: OK - Sensor data received
```

**Ctrl+C로 종료합니다**

## 5. systemd 서비스로 자동 실행

### 5.1 서비스 파일 설치

```bash
sudo cp sensor-agent.service /etc/systemd/system/smartfarm-sensor-agent.service
sudo systemctl daemon-reload
```

### 5.2 서비스 시작

```bash
# 서비스 시작
sudo systemctl start smartfarm-sensor-agent

# 부팅 시 자동 시작 활성화
sudo systemctl enable smartfarm-sensor-agent

# 서비스 상태 확인
sudo systemctl status smartfarm-sensor-agent
```

### 5.3 로그 확인

```bash
# 실시간 로그 보기
sudo journalctl -u smartfarm-sensor-agent -f

# 최근 50줄 로그
sudo journalctl -u smartfarm-sensor-agent -n 50
```

## 6. 문제 해결

### 6.1 API 키 등록 오류
```
[INIT] ⚠️  이미 등록된 기기입니다.
  └─ 대시보드에서 API 키를 재발급하고 .env 에 API_KEY=<키값> 을 직접 추가하세요.
```

**해결:**
1. Smartfarm 대시보드 접속: `http://smartfarm.rkqkdrnportfolio.shop/dashboard`
2. 기기 목록에서 해당 DEVICE_ID 찾기
3. API 키 재발급 및 복사
4. `.env` 파일의 `API_KEY` 값 업데이트
5. 서비스 재시작: `sudo systemctl restart smartfarm-sensor-agent`

### 6.2 서버 연결 실패
```
[INIT] ❌ 서버 연결 실패: Connection refused
```

**해결:**
- Smartfarm 서버 상태 확인
- EC2 보안 그룹: 아웃바운드 HTTP(80) 허용 확인
- DNS 이름 해석 확인:
  ```bash
  nslookup smartfarm.rkqkdrnportfolio.shop
  curl -v http://smartfarm.rkqkdrnportfolio.shop/api/health
  ```

### 6.3 데이터 전송 안 됨
- 로그 확인: `sudo journalctl -u smartfarm-sensor-agent -f`
- `.env` 파일의 `DEVICE_ID`와 `API_KEY` 확인
- 서버의 `/api/sensor/data` 엔드포인트 상태 확인

## 7. 모니터링

Smartfarm 대시보드에서 센서 데이터 확인:
- URL: `http://smartfarm.rkqkdrnportfolio.shop/dashboard`
- 로그인 필요
- 센서 데이터 및 실시간 그래프 표시

## 8. 서비스 종료 및 제거

```bash
# 서비스 중지
sudo systemctl stop smartfarm-sensor-agent

# 부팅 자동 시작 비활성화
sudo systemctl disable smartfarm-sensor-agent

# 서비스 파일 삭제
sudo rm /etc/systemd/system/smartfarm-sensor-agent.service
sudo systemctl daemon-reload
```

---

**데이터 흐름:**
```
EC2 센서 에이전트 (3초마다)
  → CPU 사용률 (%)
  → 메모리 사용률 (%)
  → POST /api/sensor/data
  → Smartfarm 서버 (Redis 임시 저장)
  → 1분 배치 (MySQL 저장)
  → 대시보드 표시
```
