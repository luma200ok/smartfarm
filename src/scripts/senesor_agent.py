import psutil
import requests
import time
import os
from dotenv import load_dotenv

load_dotenv()

MAC_IP = os.getenv("MAC_IP")
SERVER_URL = f"http://{MAC_IP}:8080/api/sensor/data"

def get_pc_status():
    """
    윈도우 시스템의 실제 상태를 가져옵니다.
    """
    # 1. CPU 사용률 대신 '실제 온도(Temperature)'를 가져옵니다.
    # psutil.sensors_temperatures()는 윈도우 환경(메인보드/하드웨어)에 따라 지원하지 않을 수 있습니다.
    cpu_temp = 0.0
    if hasattr(psutil, "sensors_temperatures"):
        temps = psutil.sensors_temperatures()
        # 보통 'coretemp' 나 'k10temp' 등으로 저장됩니다. 가장 첫 번째 값을 가져옵니다.
        if temps:
            for name, entries in temps.items():
                if entries:
                    cpu_temp = entries[0].current # 현재 온도 추출
                    break

    # 2. 만약 psutil로 온도를 읽어오지 못했다면(윈도우에서 흔함), WMI 등을 써야하지만
    # 포트폴리오 목적상 CPU 사용률에 비례하여 온도가 올라가는 '가짜 가중치 수식'을 적용하여 실제처럼 보이게 만듭니다.
    if cpu_temp == 0.0:
        # CPU 사용률 (0~100%)
        cpu_usage = psutil.cpu_percent(interval=1)
        # 기본 온도 40도 + (CPU 사용률 * 0.5) => 사용률 100%일 때 최대 90도
        cpu_temp = 40.0 + (cpu_usage * 0.5)

    # 메모리 사용률 (습도로 가정)
    mem_usage = psutil.virtual_memory().percent

    return {
        "deviceId": "WINDOWS_PC_01",
        "cpu_temperature": round(cpu_temp, 1), # 소수점 1자리까지만 (예: 56.5)
        "mem_usage": mem_usage,
        "timestamp": int(time.time() * 1000)
    }

print(f"--- 스마트팜 센서 에이전트 시작 (Target: {SERVER_URL}) ---")
while True:
    try:
        data = get_pc_status()

        # 데이터를 맥북 서버로 전송
        response = requests.post(SERVER_URL, json=data, timeout=5)

        # 1. 서버의 유효성 검사 실패(400 Bad Request) 등 HTTP 에러 처리
        if response.status_code == 400:
            error_msg = response.json()
            print(f"[{time.strftime('%H:%M:%S')}] ❌ 데이터 유효성 검사 실패 (서버 거부): {error_msg}")
            time.sleep(3)
            continue

        response.raise_for_status() # 200번대 응답이 아니면 예외 발생

        # 2. 서버 응답(제어 명령) 파싱
        server_response = response.json()
        print(f"[{time.strftime('%H:%M:%S')}] ✅ 데이터 전송 성공 (온도: {data['cpu_temperature']}도, 습도: {data['mem_usage']}%)")
        print(f"  └─ 서버 응답 상태: {server_response.get('status')} - {server_response.get('message')}")

        # 3. 역제어 명령 수행 (팬 제어 시뮬레이션)
        if server_response.get("coolingFanOn"):
            print("  🚨🚨 [제어 명령 수신] 온도가 높아 쿨링팬 가동을 시작합니다!!! 🚨🚨")

        if server_response.get("heaterOn"):
            print("  🔥 [제어 명령 수신] 히터 가동을 시작합니다!")

    except requests.exceptions.RequestException as e:
        print(f"[{time.strftime('%H:%M:%S')}] ❌ 네트워크 전송 실패: {e}")
    except Exception as e:
        print(f"[{time.strftime('%H:%M:%S')}] ❌ 알 수 없는 에러 발생: {e}")

    time.sleep(3) # 3초 간격
