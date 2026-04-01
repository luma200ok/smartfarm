import random
import requests
import time
import os
import threading
import json
import math
import sseclient
from datetime import datetime, timezone, timedelta
from pathlib import Path
from dotenv import load_dotenv, set_key

# ─────────────────────────────────────────────────────────────────────────────
# 환경 설정
# ─────────────────────────────────────────────────────────────────────────────

ENV_FILE    = Path(__file__).parent / ".env"
load_dotenv(dotenv_path=ENV_FILE)

DEVICE_ID   = os.getenv("DEVICE_ID")
API_KEY     = os.getenv("API_KEY", "")

BASE_URL        = "https://smartfarm.rkqkdrnportfolio.shop"
REGISTER_URL    = f"{BASE_URL}/api/device/register"
SENSOR_URL      = f"{BASE_URL}/api/sensor/data"
SSE_URL         = f"{BASE_URL}/api/sse/device-command-stream?deviceId={DEVICE_ID}"
ACK_URL         = f"{BASE_URL}/api/device-control/ack"
PENDING_URL     = f"{BASE_URL}/api/device-control/pending?deviceId={DEVICE_ID}"

AUTH_HEADERS    = {"X-Device-Id": DEVICE_ID, "X-Api-Key": API_KEY}


# ─────────────────────────────────────────────────────────────────────────────
# 신규 기기 자동 등록
# ─────────────────────────────────────────────────────────────────────────────

def register_device_if_needed():
    global API_KEY, AUTH_HEADERS

    if API_KEY:
        print(f"[INIT] API 키 로드 완료 (deviceId: {DEVICE_ID})")
        return

    print(f"[INIT] API 키 없음 — 서버에 기기 등록을 요청합니다. (deviceId: {DEVICE_ID})")
    try:
        resp = requests.post(
            REGISTER_URL,
            json={"deviceId": DEVICE_ID},
            timeout=10
        )

        if resp.status_code == 200:
            body    = resp.json()
            API_KEY = body["apiKey"]

            if not ENV_FILE.exists():
                ENV_FILE.touch()

            set_key(str(ENV_FILE), "API_KEY",   API_KEY)
            set_key(str(ENV_FILE), "DEVICE_ID", DEVICE_ID)

            AUTH_HEADERS = {"X-Device-Id": DEVICE_ID, "X-Api-Key": API_KEY}

            print(f"[INIT] 기기 등록 완료! API 키를 .env 에 저장했습니다.")
            print(f"  └─ deviceId  : {DEVICE_ID}")
            print(f"  └─ apiKey    : {API_KEY}")
            print(f"  └─ .env 경로 : {ENV_FILE}")

        elif resp.status_code == 409:
            print(f"[INIT] ⚠️  이미 등록된 기기입니다.")
            print(f"  └─ 대시보드에서 API 키를 재발급하고 .env 에 API_KEY=<키값> 을 직접 추가하세요.")
            raise SystemExit(1)

        else:
            body = resp.json() if resp.headers.get("content-type", "").startswith("application/json") else {}
            print(f"[INIT] ❌ 기기 등록 실패 (status={resp.status_code}): {body.get('message', resp.text)}")
            raise SystemExit(1)

    except requests.exceptions.RequestException as e:
        print(f"[INIT] ❌ 서버 연결 실패: {e}")
        raise SystemExit(1)


# ─────────────────────────────────────────────────────────────────────────────
# 제어 명령 실행
# ─────────────────────────────────────────────────────────────────────────────

def execute_command(command_type: str):
    handlers = {
        "COOLING_FAN_ON":  lambda: print("  🌀 [실행] 쿨링팬 가동"),
        "COOLING_FAN_OFF": lambda: print("  ⏹  [실행] 쿨링팬 정지"),
        "HUMIDIFIER_ON":   lambda: print("  💧 [실행] 가습기 가동"),
        "HUMIDIFIER_OFF":  lambda: print("  ⏹  [실행] 가습기 정지"),
    }
    handler = handlers.get(command_type)
    if handler:
        handler()
    else:
        print(f"  ⚠️  [경고] 알 수 없는 명령 타입: {command_type}")


def send_ack(command_id: int):
    try:
        resp = requests.post(ACK_URL, json={"commandId": command_id}, headers=AUTH_HEADERS, timeout=5)
        if resp.status_code == 200:
            print(f"  ✅ ACK 전송 완료 (commandId={command_id})")
        else:
            print(f"  ⚠️  ACK 전송 실패 (status={resp.status_code}): {resp.text}")
    except requests.exceptions.RequestException as e:
        print(f"  ❌ ACK 전송 중 네트워크 오류: {e}")


def handle_command(data: dict):
    command_id   = data.get("id")
    command_type = data.get("commandType")
    print(f"[{time.strftime('%H:%M:%S')}] 📡 제어 명령 수신: {command_type} (commandId={command_id})")
    execute_command(command_type)
    send_ack(command_id)


# ─────────────────────────────────────────────────────────────────────────────
# SSE 명령 스트림 수신 (백그라운드 스레드)
# ─────────────────────────────────────────────────────────────────────────────

def listen_command_stream():
    RECONNECT_DELAY = 5

    while True:
        try:
            print(f"[SSE] 명령 스트림 연결 시도 → {SSE_URL}")
            response = requests.get(SSE_URL, headers=AUTH_HEADERS, stream=True, timeout=None)
            response.raise_for_status()

            client = sseclient.SSEClient(response)
            print(f"[SSE] 명령 스트림 연결 성공")

            for event in client.events():
                if event.event == "connect":
                    print(f"[SSE] 서버 핸드셰이크 완료")
                elif event.event == "command":
                    try:
                        data = json.loads(event.data)
                        handle_command(data)
                    except (json.JSONDecodeError, KeyError) as e:
                        print(f"[SSE] 명령 파싱 오류: {e} | raw={event.data}")

        except requests.exceptions.RequestException as e:
            print(f"[SSE] 연결 실패 또는 끊김: {e}")
        except Exception as e:
            print(f"[SSE] 예상치 못한 오류: {e}")

        print(f"[SSE] {RECONNECT_DELAY}초 후 재연결합니다...")
        time.sleep(RECONNECT_DELAY)


def flush_pending_commands():
    try:
        resp = requests.get(PENDING_URL, headers=AUTH_HEADERS, timeout=5)
        if resp.status_code != 200:
            return
        pending = resp.json()
        if not pending:
            return
        print(f"[INIT] 미처리 PENDING 명령 {len(pending)}개 발견, 순차 처리합니다.")
        for cmd in pending:
            handle_command(cmd)
    except requests.exceptions.RequestException as e:
        print(f"[INIT] PENDING 명령 조회 실패: {e}")


# ─────────────────────────────────────────────────────────────────────────────
# 가상 센서 데이터 생성 (온도 + 습도)
# ─────────────────────────────────────────────────────────────────────────────

# 현실적인 온도/습도 시뮬레이션을 위한 시작값
_temp     = 23.0   # 초기 온도 (°C)
_humidity = 70.0   # 초기 습도 (%)

# 일교차 설정 상수
_PEAK_HOUR  = 14.5   # 최고 기온 시각 (14:30)
_RISE_START = 6.0    # 상승 시작 시각 (06:00)
_RISE_HOURS = _PEAK_HOUR - _RISE_START   # 상승 구간: 8.5시간
_FALL_HOURS = 24.0  - _RISE_HOURS        # 하강 구간: 15.5시간

_TEMP_MIN  = 20.0   # 일 최저 온도 (°C)
_TEMP_MAX  = 26.0   # 일 최고 온도 (°C)
_HUMI_MIN  = 60.0   # 일 최저 습도 (%)
_HUMI_MAX  = 80.0   # 일 최고 습도 (%)

def get_sensor_data() -> dict:
    """
    현실적인 일교차 패턴으로 온도·습도 데이터를 생성합니다.
    - 온도: 최저 20°C (06:00) → 최고 26°C (14:30) → 최저 (다음 06:00)
            상승(06:00~14:30, 8.5h): sin(0→π/2), 하강(14:30~06:00, 15.5h): cos(0→π/2)
    - 습도: 온도와 반비례, 최고 80% (06:00) → 최저 60% (14:30)
    """
    global _temp, _humidity

    KST  = timezone(timedelta(hours=9))
    now  = datetime.now(KST)
    hour = now.hour + now.minute / 60.0 + now.second / 3600.0

    if _RISE_START <= hour < _PEAK_HOUR:
        # 상승 구간: 0 → 1
        progress = (hour - _RISE_START) / _RISE_HOURS
        factor   = math.sin(math.pi / 2 * progress)
    else:
        # 하강 구간: 1 → 0 (자정을 넘기 위해 hour 보정)
        h        = hour if hour >= _PEAK_HOUR else hour + 24.0
        progress = (h - _PEAK_HOUR) / _FALL_HOURS
        factor   = math.cos(math.pi / 2 * progress)

    _temp     = round(_TEMP_MIN + (_TEMP_MAX - _TEMP_MIN) * factor + random.uniform(-0.2, 0.2), 1)
    _humidity = round(_HUMI_MAX - (_HUMI_MAX - _HUMI_MIN) * factor + random.uniform(-0.5, 0.5), 1)

    t = time.time()
    return {
        "deviceId":        DEVICE_ID,
        "cpu_temperature": _temp,
        "humidity":        _humidity,
        "timestamp":       int(t * 1000),
    }


# ─────────────────────────────────────────────────────────────────────────────
# 센서 데이터 전송 루프 (메인)
# ─────────────────────────────────────────────────────────────────────────────

def run_sensor_loop():
    """3초마다 가상 센서 데이터를 서버로 전송합니다."""
    while True:
        try:
            data     = get_sensor_data()
            response = requests.post(SENSOR_URL, json=data, headers=AUTH_HEADERS, timeout=5)

            if response.status_code != 200:
                try:
                    err = response.json()
                    print(f"[{time.strftime('%H:%M:%S')}] ❌ 서버 거부 ({response.status_code}): {err.get('message')}")
                    if err.get("details"):
                        print(f"    └─ 상세: {err['details']}")
                except ValueError:
                    print(f"[{time.strftime('%H:%M:%S')}] ❌ 서버 통신 오류 ({response.status_code}): {response.text}")
            else:
                body = response.json()
                print(f"[{time.strftime('%H:%M:%S')}] ✅ 전송 완료 "
                      f"(온도: {data['cpu_temperature']}°C, 습도: {data['humidity']}%)")
                print(f"  └─ 서버 상태: {body.get('status')} - {body.get('message')}")

        except requests.exceptions.RequestException as e:
            print(f"[{time.strftime('%H:%M:%S')}] ❌ 네트워크 오류: {e}")
        except Exception as e:
            print(f"[{time.strftime('%H:%M:%S')}] ❌ 내부 오류: {e}")

        time.sleep(3)


# ─────────────────────────────────────────────────────────────────────────────
# 진입점
# ─────────────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    print(f"--- 스마트팜 센서 에이전트 시작 (deviceId: {DEVICE_ID}, server: {BASE_URL}) ---")

    register_device_if_needed()
    flush_pending_commands()

    sse_thread = threading.Thread(target=listen_command_stream, daemon=True, name="SSE-Command-Listener")
    sse_thread.start()

    run_sensor_loop()
