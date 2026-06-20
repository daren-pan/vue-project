#!/bin/bash

# 1. 启动基础服务（MySQL、Redis、Nacos 等）
docker-compose up -d ruoyi-mysql ruoyi-redis ruoyi-nacos

# 2. 等待 MySQL 就绪（使用 wait-for-it 或直接轮询）
echo "Waiting for MySQL to be ready..."
while ! docker exec ruoyi-mysql mysqladmin ping -h localhost -u root -ppassword --silent; do
    sleep 2
done

# 3. 等待 Redis 就绪
echo "Waiting for Redis to be ready..."
while ! docker exec ruoyi-redis redis-cli ping | grep -q PONG; do
    sleep 2
done

# 4. 等待 Nacos 就绪（例如通过 HTTP 健康检查）
echo "Waiting for Nacos to be ready..."
while ! curl.exe -s http://localhost:8848/nacos/actuator/health | grep -q '"status":"UP"'; do
    sleep 2
done

# 5. 所有基础服务就绪，再启动其他业务服务
docker-compose up -d ruoyi-nginx ruoyi-gateway ruoyi-auth ruoyi-modules-system