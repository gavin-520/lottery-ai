# 彩票AI平台本地运行环境检查报告

## 当前环境状态

### 已安装组件
- Java: 1.8.0_202 ❌ (需要Java 17或更高版本)
- Maven: 3.9.11 ✓
- Python: 3.14.0 ✓
- Node.js: v24.12.0 ✓

### 待安装/配置组件
- MySQL 8.0 (未检测到)
- Redis (未检测到)

## 环境升级步骤

### 1. 升级Java版本至17或更高版本

1. **卸载旧版Java (可选)**:
   - 从控制面板卸载Java 8

2. **下载并安装Java 17或更高版本**:
   - 访问 [Oracle JDK](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html) 或
   - 使用 [OpenJDK](https://adoptium.net/) (推荐免费版本)

3. **更新JAVA_HOME环境变量**:
   - 设置JAVA_HOME指向新的Java安装路径
   - 将%JAVA_HOME%\bin添加到PATH环境变量

4. **验证Java版本**:
   ```bash
   java -version
   javac -version
   ```

### 2. 安装并启动MySQL 8.0

1. **下载并安装MySQL**:
   - 访问 [MySQL官方下载页面](https://dev.mysql.com/downloads/mysql/8.0.html)
   - 下载适合Windows的版本并安装

2. **启动MySQL服务**:
   ```bash
   # 作为Windows服务启动
   net start mysql
   
   # 或使用MySQL自带的命令
   mysqld --console
   ```

3. **创建数据库和用户**:
   ```sql
   CREATE DATABASE lottery_ai CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'lottery'@'localhost' IDENTIFIED BY 'lottery_pass';
   GRANT ALL PRIVILEGES ON lottery_ai.* TO 'lottery'@'localhost';
   FLUSH PRIVILEGES;
   ```

4. **导入数据库结构**:
   - 执行 `database/init/` 目录下的SQL文件

### 3. 安装并启动Redis

1. **下载并安装Redis**:
   - 访问 [Redis for Windows](https://github.com/tporadowski/redis/releases)
   - 下载并安装Redis

2. **启动Redis服务**:
   ```bash
   # 以服务方式启动
   net start redis
   
   # 或直接运行Redis服务器
   redis-server.exe
   ```

### 4. 启动后端服务

在升级Java并安装好MySQL和Redis后:

1. **打开命令提示符或PowerShell**
2. **导航到后端目录**:
   ```bash
   cd D:\gavin\spaces\lottery-ai\backend
   ```
3. **运行Spring Boot应用**:
   ```bash
   mvn spring-boot:run
   ```

### 5. 启动AI服务

1. **导航到AI服务目录**:
   ```bash
   cd D:\gavin\spaces\lottery-ai\ai-service
   ```
2. **安装Python依赖**:
   ```bash
   pip install -r requirements.txt
   ```
3. **运行AI服务**:
   ```bash
   uvicorn app.main:app --host 0.0.0.0 --port 8000
   ```

### 6. 启动前端服务

1. **导航到前端目录**:
   ```bash
   cd D:\gavin\spaces\lottery-ai\frontend
   ```
2. **安装依赖并运行**:
   ```bash
   npm install
   npm run dev
   ```

## 注意事项

1. 确保所有服务使用的端口未被占用 (MySQL: 3306, Redis: 6379, Backend: 8080, AI Service: 8000, Frontend: 5173)
2. 在启动服务之前，请确保数据库服务已先行启动
3. 如果使用OpenAI API，请设置OPENAI_API_KEY环境变量

完成以上步骤后，您就可以在本地成功运行彩票AI平台了。