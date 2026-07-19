# MySQL 8.4 配置说明

## 项目中MySQL配置详情

### 当前连接配置
在 `application.yml` 中定义的MySQL连接配置：
```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:mysql://localhost:3306/lottery_ai?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai}
    username: ${SPRING_DATASOURCE_USERNAME:lottery}
    password: ${SPRING_DATASOURCE_PASSWORD:lottery_pass}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### 依赖版本
- 项目使用 `mysql-connector-j` 驱动
- JDBC URL参数已包含MySQL 8.x兼容性参数

## MySQL 8.4 兼容性说明

### 连接参数分析
当前的JDBC URL参数适合MySQL 8.4：
- `useSSL=false`: 禁用SSL连接（开发环境）
- `allowPublicKeyRetrieval=true`: 允许公钥检索（MySQL 8.x必需）
- `serverTimezone=Asia/Shanghai`: 设置服务器时区

### 用户权限设置
对于MySQL 8.4，需要确保用户使用合适的认证插件：

```sql
-- 创建用户时指定认证插件
CREATE USER 'lottery'@'localhost' IDENTIFIED WITH mysql_native_password BY 'lottery_pass';

-- 或者修改现有用户的认证插件
ALTER USER 'lottery'@'localhost' IDENTIFIED WITH mysql_native_password BY 'lottery_pass';
FLUSH PRIVILEGES;
```

### 数据库创建语句
```sql
CREATE DATABASE lottery_ai CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON lottery_ai.* TO 'lottery'@'localhost';
FLUSH PRIVILEGES;
```

## MySQL 8.4 安装与配置建议

### 1. 安装MySQL 8.4
- 从 [MySQL官方网站](https://dev.mysql.com/downloads/mysql/8.4.html) 下载
- 按照Windows安装向导进行安装

### 2. 初始化数据库
执行以下步骤导入项目数据库结构：
```bash
# 导航到项目数据库初始化目录
cd D:\gavin\spaces\lottery-ai\database\init

# 执行SQL文件创建数据库结构
mysql -u root -p < 001_schema.sql
mysql -u root -p < 002_seed.sql
mysql -u root -p < 003_seed_extended.sql
mysql -u root -p < 004_sprint2.sql
```

### 3. 验证连接
安装完成后，可以使用以下命令验证连接：
```bash
mysql -u lottery -p -h localhost -P 3306 lottery_ai
```

## 如何启动MySQL服务

### 方法1：作为Windows服务启动
```bash
# 启动MySQL服务
net start mysql

# 停止MySQL服务
net stop mysql
```

### 方法2：手动启动
```bash
# 通常位于MySQL安装目录的bin文件夹
"C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqld.exe" --defaults-file="C:\ProgramData\MySQL\MySQL Server 8.4\my.ini"
```

## 注意事项

1. **字符集设置**: 项目使用utf8mb4字符集，确保MySQL配置文件中包含：
   ```
   [client]
   default-character-set = utf8mb4
   
   [mysql]
   default-character-set = utf8mb4
   
   [mysqld]
   character-set-client-handshake = FALSE
   character-set-server = utf8mb4
   collation-server = utf8mb4_unicode_ci
   ```

2. **时区配置**: 确保MySQL服务器时区设置正确，或使用连接字符串中的时区参数

3. **安全设置**: 生产环境中建议启用SSL连接并使用强密码策略

4. **内存配置**: 根据系统资源调整MySQL内存参数，如innodb_buffer_pool_size

## 故障排除

如果遇到连接问题，请检查：
1. MySQL服务是否正在运行
2. 防火墙是否阻止了3306端口
3. 用户权限是否正确设置
4. 连接参数是否与MySQL 8.4兼容

完成MySQL 8.4的安装和配置后，您就可以继续进行项目的本地运行了。