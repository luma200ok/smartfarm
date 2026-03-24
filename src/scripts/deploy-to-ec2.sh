#!/bin/bash

# 센서 에이전트 EC2 배포 스크립트
# 사용법: ./deploy-to-ec2.sh EC2_HOST EC2_USER EC2_KEY_PATH DEVICE_ID
# 예: ./deploy-to-ec2.sh ec2-12-34-56-78.compute-1.amazonaws.com ec2-user ~/keys/smartfarm.pem EC2_PROD_01

set -e

if [ $# -lt 4 ]; then
    echo "사용법: $0 <EC2_HOST> <EC2_USER> <EC2_KEY_PATH> <DEVICE_ID>"
    echo ""
    echo "예시:"
    echo "  $0 ec2-12-34-56-78.compute-1.amazonaws.com ec2-user ~/keys/smartfarm.pem EC2_PROD_01"
    exit 1
fi

EC2_HOST=$1
EC2_USER=$2
EC2_KEY=$3
DEVICE_ID=$4
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "========================================="
echo "센서 에이전트 EC2 배포"
echo "========================================="
echo "EC2 Host: $EC2_HOST"
echo "User: $EC2_USER"
echo "Device ID: $DEVICE_ID"
echo "========================================="
echo ""

# 1. EC2 연결 테스트
echo "[1/5] EC2 연결 테스트..."
if ! ssh -i "$EC2_KEY" -o ConnectTimeout=5 "$EC2_USER@$EC2_HOST" "echo OK" > /dev/null 2>&1; then
    echo "❌ EC2 연결 실패"
    exit 1
fi
echo "✅ EC2 연결 성공"
echo ""

# 2. 원격 디렉토리 생성
echo "[2/5] 원격 디렉토리 생성..."
ssh -i "$EC2_KEY" "$EC2_USER@$EC2_HOST" << REMOTE_COMMANDS
mkdir -p /home/$EC2_USER/smartfarm-sensor-agent
cd /home/$EC2_USER/smartfarm-sensor-agent
echo "✅ 디렉토리 생성 완료"
REMOTE_COMMANDS
echo ""

# 3. 파일 업로드
echo "[3/5] 파일 업로드 (scp)..."
scp -i "$EC2_KEY" -q \
    "$SCRIPT_DIR/sensor_agent.py" \
    "$SCRIPT_DIR/requirements.txt" \
    "$SCRIPT_DIR/.env.example" \
    "$SCRIPT_DIR/sensor-agent.service" \
    "$EC2_USER@$EC2_HOST:/home/$EC2_USER/smartfarm-sensor-agent/"
echo "✅ 파일 업로드 완료"
echo ""

# 4. Python 패키지 설치
echo "[4/5] Python 패키지 설치..."
ssh -i "$EC2_KEY" "$EC2_USER@$EC2_HOST" << REMOTE_COMMANDS
cd /home/$EC2_USER/smartfarm-sensor-agent

# Python 버전 확인
python3 --version

# 패키지 설치
pip3 install --upgrade pip -q
pip3 install -r requirements.txt -q

echo "✅ Python 패키지 설치 완료"
REMOTE_COMMANDS
echo ""

# 5. .env 파일 생성 및 DEVICE_ID 설정
echo "[5/5] 환경 설정 (.env) 생성..."
ssh -i "$EC2_KEY" "$EC2_USER@$EC2_HOST" << REMOTE_COMMANDS
cd /home/$EC2_USER/smartfarm-sensor-agent

# .env 파일 생성 (DEVICE_ID 설정)
cat > .env << ENV_EOF
DEVICE_ID=$DEVICE_ID
API_KEY=
ENV_EOF

echo "✅ .env 파일 생성 완료"
echo "  └─ DEVICE_ID: $DEVICE_ID"
REMOTE_COMMANDS
echo ""

echo "========================================="
echo "✅ 배포 완료!"
echo "========================================="
echo ""
echo "다음 단계:"
echo ""
echo "1️⃣  수동 테스트 (선택사항):"
echo "   ssh -i '$EC2_KEY' $EC2_USER@$EC2_HOST"
echo "   cd /home/$EC2_USER/smartfarm-sensor-agent"
echo "   python3 sensor_agent.py"
echo ""
echo "2️⃣  systemd 서비스 설치:"
echo "   ssh -i '$EC2_KEY' $EC2_USER@$EC2_HOST"
echo "   sudo cp /home/$EC2_USER/smartfarm-sensor-agent/sensor-agent.service /etc/systemd/system/smartfarm-sensor-agent.service"
echo "   sudo systemctl daemon-reload"
echo "   sudo systemctl start smartfarm-sensor-agent"
echo "   sudo systemctl enable smartfarm-sensor-agent"
echo ""
echo "3️⃣  로그 확인:"
echo "   ssh -i '$EC2_KEY' $EC2_USER@$EC2_HOST"
echo "   sudo journalctl -u smartfarm-sensor-agent -f"
echo ""
