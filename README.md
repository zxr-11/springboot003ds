# springboot003ds 运行说明

## 环境要求

- **JDK 1.8**
- **Maven 3.x**
- **MySQL**（需先创建数据库并导入数据）

## 运行前准备

### 1. 配置数据库

在 MySQL 中创建数据库：

```sql
CREATE DATABASE springboot003ds DEFAULT CHARACTER SET utf8mb4;
```

如有项目提供的 SQL 脚本，请导入到该数据库中。

### 2. 修改数据库连接（如需要）

编辑 `src/main/resources/application.yml`，按你的环境修改：

- `url`: 数据库地址（默认 `127.0.0.1:3306/springboot003ds`）
- `username`: 数据库用户名（默认 `root`）
- `password`: 数据库密码（默认 `linjianbin`，请改为你的密码）

## 运行方式

### 方式一：Maven 命令运行（推荐）

在项目根目录执行：

```bash
mvn spring-boot:run
```

### 方式二：先打包再运行

```bash
mvn clean package -DskipTests
java -jar target/springboot003ds-0.0.1-SNAPSHOT.jar
```

### 方式三：在 IDE 中运行

直接运行主类：`com.SpringbootSchemaApplication` 的 `main` 方法。

## 访问地址

启动成功后访问：

- 后端接口根路径：**http://localhost:8080/springboot003ds**

如有前端页面，请根据前端说明访问对应地址。

## 常见问题

- **数据库连接失败**：确认 MySQL 已启动，数据库已创建，且 `application.yml` 中的地址、用户名、密码正确。
- **端口被占用**：默认端口为 8080，可在 `application.yml` 的 `server.port` 中修改。
