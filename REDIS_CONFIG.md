# Redis 连接配置说明

## 配置更改概述

已将Redis连接配置从默认的localhost更改为192.168.0.10。

## 配置文件说明

### 1. application-local.yml
- 新增了本地配置文件，明确指定了Redis主机为192.168.0.10
- 包含了连接池和其他Redis相关配置

### 2. .env 文件
- 添加了 `REDIS_HOST=192.168.0.10` 配置
- 保留了原来的端口和密码设置

### 3. 启动脚本 (start-local.bat)
- 设置了环境变量 `SPRING_DATA_REDIS_HOST=192.168.0.10`
- 可以用来快速启动连接到指定Redis的服务

## 如何使用

### 方法1：使用启动脚本
```bash
# 在项目根目录运行
start-local.bat
```

### 方法2：手动设置环境变量后启动
```bash
# 设置环境变量
export SPRING_DATA_REDIS_HOST=192.168.0.10
export SPRING_DATA_REDIS_PORT=6379

# 在后端目录运行
cd D:\gavin\spaces\lottery-ai\backend
mvn clean spring-boot:run
```

### 方法3：使用Maven命令行参数
```bash
cd D:\gavin\spaces\lottery-ai\backend
mvn clean spring-boot:run -Dspring.data.redis.host=192.168.0.10 -Dspring.data.redis.port=6379
```

## 验证连接

在启动服务前，请验证Redis服务器是否可以从您的机器访问：

```bash
# 使用telnet测试连接
telnet 192.168.0.10 6379

# 或使用redis-cli测试（如果已安装）
redis-cli -h 192.168.0.10 -p 6379 ping
```

## 注意事项

1. 确保192.168.0.10上的Redis服务正在运行
2. 确保防火墙允许访问6379端口
3. 确保Redis服务器配置允许来自您客户端IP的连接
4. 如果Redis设置了密码认证，请在配置中添加相应的密码设置

## 故障排除

如果遇到连接问题：

1. 检查Redis服务器是否在192.168.0.10上运行
2. 检查网络连通性
3. 检查Redis配置文件(bind和protected-mode设置)
4. 查看应用日志中的具体错误信息