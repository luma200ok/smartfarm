import psutil
import requests
import time
import os
import threading
import json
import sseclient
from dotenv import load_dotenv

load_dotenv()

MAC_IP      = os.getenv("MAC_IP")
DEVICE_ID   = "WINDOWS_PC_SUB"
BASE_URL    = f"http://{MAC_IP}:8080"
SENSOR_URL  = f"{BASE_URL}/api/sensor/data"
SSE_URL     = f"{BASE_URL}/api/sse/device-command-stream?deviceId={DEVICE_ID}"
ACK_URL     = f"{BASE_URL}/api/device-control/ack"
PENDING_URL = f"{BASE_URL}/api/device-control/pending?deviceId={DEVICE_ID}"

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
        resp = requests.post(ACK_URL, json={"commandId": command_id}, timeout=5)
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
            response = requests.get(SSE_URL, stream=True, timeout=None)
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
        resp = requests.get(PENDING_URL, timeout=5)
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
    """윈도우 시스템의 실제 상태를 가져옵니다."""
    cpu_temp = 0.0
    if hasattr(psutil, "sensors_temperatures"):
        temps = psutil.sensors_temperatures()
        if temps:
            for name, entries in temps.items():
                if entries:
                    cpu_temp = entries[0].current
                    break

    if cpu_temp == 0.0:
        cpu_usage = psutil.cpu_percent(interval=1)
        cpu_temp  = 40.0 + (cpu_usage * 0.5)

    return {
        "deviceId":        DEVICE_ID,
        "cpu_temperature": round(cpu_temp, 1),
        "mem_usage":       psutil.virtual_memory().percent,
        "timestamp":       int(time.time() * 1000),
    }


def run_sensor_loop():
    """3초마다 센서 데이터를 서버로 전송합니다."""
    while True:
        try:
            data     = get_pc_status()
            response = requests.post(SENSOR_URL, json=data, timeout=5)

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
                      f"(온도: {data['cpu_temperature']}°C, 메모리: {data['mem_usage']}%)")
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

    # 시작 시 DB에 쌓인 PENDING 명령 처리
    flush_pending_commands()

    # SSE 명령 수신 스레드 (daemon=True → 메인 스레드 종료 시 함께 종료)
    sse_thread = threading.Thread(target=listen_command_stream, daemon=True, name="SSE-Command-Listener")
    sse_thread.start()

    # 메인 스레드: 센서 데이터 전송 루프
    run_sensor_loop()
