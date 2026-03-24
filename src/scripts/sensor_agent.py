import psutil
import requests
import time
import os
import threading
import json
import sseclient
from pathlib import Path
from dotenv import load_dotenv, set_key

# ─────────────────────────────────────────────────────────────────────────────
# 환경 설정
# ─────────────────────────────────────────────────────────────────────────────

# 스크립트와 같은 폴더의 .env 파일 경로
ENV_FILE    = Path(__file__).parent / ".env"
load_dotenv(dotenv_path=ENV_FILE)

DEVICE_ID   = os.getenv("DEVICE_ID")   # .env 에서 읽거나 기본값 사용
API_KEY     = os.getenv("API_KEY", "")                   # 최초에는 빈 문자열

BASE_URL        = "http://smartfarm.rkqkdrnportfolio.shop"
REGISTER_URL    = f"{BASE_URL}/api/device/register"
SENSOR_URL      = f"{BASE_URL}/api/sensor/data"
SSE_URL         = f"{BASE_URL}/api/sse/device-command-stream?deviceId={DEVICE_ID}"
ACK_URL         = f"{BASE_URL}/api/device-control/ack"
PENDING_URL     = f"{BASE_URL}/api/device-control/pending?deviceId={DEVICE_ID}"

# 모든 PC 클라이언트 요청에 공통으로 붙는 인증 헤더
AUTH_HEADERS    = {"X-Device-Id": DEVICE_ID, "X-Api-Key": API_KEY}


# ─────────────────────────────────────────────────────────────────────────────
# 신규 기기 자동 등록
# ─────────────────────────────────────────────────────────────────────────────

def register_device_if_needed():
    """
    .env 에 API_KEY 가 없으면 서버에 기기 자동 등록을 요청하고
    발급받은 API 키를 .env 파일에 영구 저장합니다.
    이후 AUTH_HEADERS 를 갱신하여 현재 실행에서도 즉시 사용합니다.
    """
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

            # .env 파일이 없으면 미리 생성 (python-dotenv 구버전 호환)
            if not ENV_FILE.exists():
                ENV_FILE.touch()

            # .env 파일에 영구 저장
            set_key(str(ENV_FILE), "API_KEY",   API_KEY)
            set_key(str(ENV_FILE), "DEVICE_ID", DEVICE_ID)

            # 현재 실행에서 즉시 반영 (이후 모든 요청에 사용)
            AUTH_HEADERS = {"X-Device-Id": DEVICE_ID, "X-Api-Key": API_KEY}

            print(f"[INIT] 기기 등록 완료! API 키를 .env 에 저장했습니다.")
            print(f"  └─ deviceId  : {DEVICE_ID}")
            print(f"  └─ apiKey    : {API_KEY}")
            print(f"  └─ .env 경로 : {ENV_FILE}")

        elif resp.status_code == 409:
            # 이미 등록된 기기 — .env 에 키가 없는 비정상 상태
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
    """
    서버로부터 수신한 제어 명령을 실행합니다.
    실제 환경에서는 GPIO, serial, subprocess 등 하드웨어 제어 코드로 교체하세요.
    """
    handlers = {
        "COOLING_FAN_ON":  lambda: print("  🌀 [실행] 쿨링팬 가동"),
        "COOLING_FAN_OFF": lambda: print("  ⏹  [실행] 쿨링팬 정지"),
        "HEATER_ON":       lambda: print("  🔥 [실행] 히터 가동"),
        "HEATER_OFF":      lambda: print("  ⏹  [실행] 히터 정지"),
    }
    handler = handlers.get(command_type)
    if handler:
        handler()
    else:
        print(f"  ⚠️  [경고] 알 수 없는 명령 타입: {command_type}")


def send_ack(command_id: int):
    """명령 실행 후 서버에 ACK를 전송합니다."""
    try:
        resp = requests.post(ACK_URL, json={"commandId": command_id}, headers=AUTH_HEADERS, timeout=5)
        if resp.status_code == 200:
            print(f"  ✅ ACK 전송 완료 (commandId={command_id})")
        else:
            print(f"  ⚠️  ACK 전송 실패 (status={resp.status_code}): {resp.text}")
    except requests.exceptions.RequestException as e:
        print(f"  ❌ ACK 전송 중 네트워크 오류: {e}")


def handle_command(data: dict):
    """명령 처리 파이프라인: 실행 → ACK"""
    command_id   = data.get("id")
    command_type = data.get("commandType")
    print(f"[{time.strftime('%H:%M:%S')}] 📡 제어 명령 수신: {command_type} (commandId={command_id})")
    execute_command(command_type)
    send_ack(command_id)


# ─────────────────────────────────────────────────────────────────────────────
# SSE 명령 스트림 수신 (백그라운드 스레드)
# ─────────────────────────────────────────────────────────────────────────────

def listen_command_stream():
    """
    서버의 SSE 명령 스트림에 상시 연결합니다.
    네트워크 오류 / 서버 재시작 시 자동으로 재연결합니다.
    """
    RECONNECT_DELAY = 5  # 재연결 대기 시간(초)

    while True:
        try:
            print(f"[SSE] 명령 스트림 연결 시도 → {SSE_URL}")
            # stream=True + timeout=None 으로 장시간 연결 유지
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
    """
    SSE가 끊겨있던 동안 DB에 쌓인 PENDING 명령을 시작 시 한번에 처리합니다.
    (SSE 연결 직후 호출)
    """
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
# 센서 데이터 수집 및 전송 (메인 루프)
# ─────────────────────────────────────────────────────────────────────────────

def get_pc_status() -> dict:
    """시스템의 CPU 사용률과 메모리 사용률을 가져옵니다.

    주의: EC2 환경에서는 CPU 온도 센서가 없으므로 CPU 사용률을 온도 범위(20~100°C)로 맵핑합니다.
    맵핑: CPU 0% = 20°C, CPU 100% = 100°C
    """
    cpu_usage = psutil.cpu_percent(interval=1)
    mem_usage = psutil.virtual_memory().percent

    # CPU 사용률을 온도 범위로 맵핑 (0% → 20°C, 100% → 100°C)
    mapped_temperature = 20 + (cpu_usage * 0.8)

    return {
        "deviceId":         DEVICE_ID,
        "cpu_temperature":  round(mapped_temperature, 1),  # EC2: CPU 사용률을 온도로 맵핑
        "mem_usage":        round(mem_usage, 1),
        "timestamp":        int(time.time() * 1000),
    }


def run_sensor_loop():
    """3초마다 센서 데이터를 서버로 전송합니다."""
    while True:
        try:
            data     = get_pc_status()
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
                      f"(CPU: {data['cpu_temperature']}%, 메모리: {data['mem_usage']}%)")
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

    # 1단계: API 키 확인 → 없으면 자동 등록
    register_device_if_needed()

    # 2단계: 시작 시 DB에 쌓인 PENDING 명령 처리
    flush_pending_commands()

    # 3단계: SSE 명령 수신 스레드 (daemon=True → 메인 스레드 종료 시 함께 종료)
    sse_thread = threading.Thread(target=listen_command_stream, daemon=True, name="SSE-Command-Listener")
    sse_thread.start()

    # 4단계: 메인 스레드: 센서 데이터 전송 루프
    run_sensor_loop()
