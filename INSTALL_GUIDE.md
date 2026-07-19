# 彩票AI平台启动指南

## 系统要求

在启动彩票AI平台之前，请确保您的系统满足以下要求：

### 必需软件
1. **Docker Desktop for Windows** (推荐版本 4.0 或更高)
2. **Git** (用于克隆仓库，如果适用)
3. **PowerShell** 或 **命令提示符**

## 安装 Docker Desktop for Windows

1. 访问 [Docker官网](https://www.docker.com/products/docker-desktop/)
2. 下载适用于Windows的Docker Desktop
3. 运行安装程序并按照提示完成安装
4. 启动Docker Desktop并等待其完全加载(状态栏显示鲸鱼图标)

## 启动项目

完成Docker安装后，按以下步骤启动项目：

1. 打开PowerShell或命令提示符
2. 导航到项目目录：
   ```powershell
   cd D:\gavin\spaces\lottery-ai
   ```

3. 确保环境变量文件存在：
   ```powershell
   # 检查 .env 文件是否存在
   dir .env
   ```

4. 如果 .env 文件不存在，从示例文件复制：
   ```powershell
   copy .env.example .env
   ```

5. 编辑 .env 文件并设置必要的环境变量：
   - 如果您有OpenAI API密钥，请设置 `OPENAI_API_KEY=your_api_key_here`
   - 其他参数可以保持默认值

6. 启动所有服务：
   ```powershell
   docker compose up -d --build
   ```
   
   或者运行预设的启动脚本：
   ```powershell
   .\scripts\start.ps1
   ```

## 服务信息

启动后，以下服务将在相应端口上运行：

- **前端界面**: http://localhost:5173
- **后端API**: http://localhost:8080
- **AI服务**: http://localhost:8000
- **数据库(MySQL)**: localhost:3306
- **缓存(Redis)**: localhost:6379

## 默认登录信息

- 用户名: `admin`
- 密码: `admin123`

## 故障排除

1. **Docker服务未启动**: 确保Docker Desktop正在运行
2. **端口被占用**: 检查是否有其他服务占用了所需端口
3. **构建失败**: 检查Docker是否有足够的资源分配
4. **AI服务错误**: 确认已设置有效的OPENAI_API_KEY

## 停止服务

要停止所有服务，请运行：
```powershell
docker compose down
```