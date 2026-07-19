@echo off
echo Starting Lottery AI Backend with Redis at 192.168.0.10...

REM 设置环境变量
set SPRING_DATA_REDIS_HOST=192.168.0.10
set SPRING_DATA_REDIS_PORT=6379

REM 启动后端服务
cd /d D:\gavin\spaces\lottery-ai\backend

echo Building and starting the backend...
call mvn clean spring-boot:run -Dspring-boot.run.profiles=local

pause