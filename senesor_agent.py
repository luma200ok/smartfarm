import psutil
import requests
import time
import os
from dotenv import load_dotenv
load_dotenv()

MAC_IP = os.getenv("MAC_IP")

# 3. f-string을 사용하여 URL을 완성합니다. (f가 붙어야 {MAC_IP}가 변수로 치환됩니다.)
SERVER_URL = f"http://{MAC_IP}:8080/api/sensor/data"


def get_pc_status():
    # 윈도우 CPU 사용률과 메모리 사용률을 각각 온도와 습도로 가정합니다.
    cpu_usage = psutil.cpu_percent(interval=1)
    mem_usage = psutil.virtual_memory().percent

    return {
        "deviceId": "WINDOWS_PC_01",
        "cpu_temperature": cpu_usage,  # 임시 온도값
        "mem_usage": mem_usage,     # 임시 습도값
        "timestamp": int(time.time() * 1000)
    }

print(f"--- 스마트팜 센서 에이전트 시작 (Target: {SERVER_URL}) ---")
while True:
    try:
        data = get_pc_status()
        # 데이터를 맥북 서버로 전송
        response = requests.post(SERVER_URL, json=data, timeout=5)

        # 수정됨: data['temperature'] -> data['cpu_temperature']
        print(f"[{time.strftime('%H:%M:%S')}] 전송 성공! (CPU: {data['cpu_temperature']}%, MEM: {data['mem_usage']}%)")

    except Exception as e:
        # 수정됨: 실제 발생한 에러 원인(e)을 함께 출력하도록 변경
        print(f"[{time.strftime('%H:%M:%S')}] 전송 실패: {e}")

time.sleep(3) # 3초 간격