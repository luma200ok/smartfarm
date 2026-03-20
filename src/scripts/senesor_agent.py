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
    cpu_temp = 0.0
    if hasattr(psutil, "sensors_temperatures"):
        temps = psutil.sensors_temperatures()
        if temps:
            for name, entries in temps.items():
                if entries:
                    cpu_temp = entries[0].current # 현재 온도 추출
                    break

    # 2. 만약 psutil로 온도를 읽어오지 못했다면 가짜 수식 적용
    if cpu_temp == 0.0:
        cpu_usage = psutil.cpu_percent(interval=1)
        cpu_temp = 40.0 + (cpu_usage * 0.5)

    mem_usage = psutil.virtual_memory().percent

    return {
        "deviceId": "WINDOWS_PC_SUB",
        "cpu_temperature": round(cpu_temp, 1),
        "mem_usage": mem_usage,
        "timestamp": int(time.time() * 1000)
    }

print(f"--- 스마트팜 센서 에이전트 시작 (Target: {SERVER_URL}) ---")
while True:
    try:
        data = get_pc_status()

        # 데이터를 맥북 서버로 전송
        response = requests.post(SERVER_URL, json=data, timeout=5)

        # HTTP 상태 코드가 200번대(성공)가 아니면 직접 예외를 발생시키지 않고 분기 처리합니다.
        if response.status_code != 200:
            # 방금 만든 ErrorResponse 형식의 JSON을 파싱해서 출력합니다.
            try:
                error_msg = response.json()
                print(f"[{time.strftime('%H:%M:%S')}] ❌ 서버 에러 거부 ({response.status_code}): {error_msg.get('message')}")
                if 'details' in error_msg and error_msg['details']:
                    print(f"    └─ 상세 이유: {error_msg['details']}")
            except ValueError:
                # 서버가 JSON이 아닌 단순 텍스트나 html을 뱉은 경우 (예: 404 Not Found)
                print(f"[{time.strftime('%H:%M:%S')}] ❌ 서버 통신 에러 ({response.status_code}): {response.text}")
            time.sleep(3)
            continue

        # 200 성공 시에만 아래 로직을 탑니다.
        server_response = response.json()
        print(f"[{time.strftime('%H:%M:%S')}] ✅ 데이터 전송 성공 (온도: {data['cpu_temperature']}도, 습도: {data['mem_usage']}%)")
        print(f"  └─ 서버 응답 상태: {server_response.get('status')} - {server_response.get('message')}")

        if server_response.get("coolingFanOn"):
            print("  🚨🚨 [제어 명령 수신] 온도가 높아 쿨링팬 가동을 시작합니다!!! 🚨🚨")

        if server_response.get("heaterOn"):
            print("  🔥 [제어 명령 수신] 히터 가동을 시작합니다!")

    except requests.exceptions.RequestException as e:
        print(f"[{time.strftime('%H:%M:%S')}] ❌ 네트워크 전송 실패 (서버 꺼짐 등): {e}")
    except Exception as e:
        print(f"[{time.strftime('%H:%M:%S')}] ❌ 파이썬 스크립트 내부 에러 발생: {e}")

    time.sleep(3) # 3초 간격
