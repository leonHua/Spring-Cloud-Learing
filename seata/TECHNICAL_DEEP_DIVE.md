# Seata 模块技术深度解析 (Technical Deep Dive)

## 1. 模块概述 (Module Overview)

此模块演示了如何使用 Seata (具体为 AT 模式 - Automatic Transaction Mode) 实现分布式事务管理。场景涉及一个订单服务 (`order-service`) 和一个库存服务 (`storage-service`)。当客户端请求创建订单时，`order-service` 会启动一个全局事务，它负责（概念上的）订单创建，并调用 `storage-service` 来扣减相应商品的库存。这两个操作要么都成功，要么都失败回滚，从而保证了跨多个服务的事务一致性。

## 2. 架构设计 (Architecture Design)

### 核心组件 (Core Components)

*   **`order-service`**:
    *   **角色**: 业务发起方，事务管理器 (TM - Transaction Manager) 的角色由 Seata 客户端承担。
    *   **职责**:
        *   使用 `@GlobalTransactional` 注解开启 Seata 全局事务。
        *   通过 Spring Cloud Feign 调用 `storage-service` 的接口。
        *   (在本示例中，`order-service` 的本地数据库操作被简化，未实际写入订单数据，重点在于演示跨服务调用)。
*   **`storage-service`**:
    *   **角色**: 业务参与者，资源管理器 (RM - Resource Manager) 的角色由 Seata 客户端承担。
    *   **职责**:
        *   其本地数据库操作（库存扣减）作为分支事务加入到由 `order-service` 发起的全局事务中。
        *   如果操作失败（如库存不足），抛出异常，通知 Seata 进行回滚。
*   **Seata Server (外部)**:
    *   **角色**: 事务协调器 (TC - Transaction Coordinator)。
    *   **职责**: 维护全局事务和分支事务的状态，协调全局事务的提交或回滚。
*   **H2 Databases**:
    *   **角色**: 每个服务 (`order-service` 和 `storage-service`) 使用其各自独立的 H2 内存数据库。
    *   **集成**: Seata 通过代理数据源 (`DataSourceProxy`) 来管理这些数据库上的事务分支，以便能够记录 UNDO_LOG 并执行回滚。

### 事务流程 (Transaction Flow - AT Mode)

1.  **TM - 开启全局事务**: `order-service` 的 `@GlobalTransactional` 方法被调用时，Seata TM 向 TC 注册一个全局事务，获取全局事务 ID (XID)。XID 会通过 `RootContext` 绑定到当前线程。
2.  **TM - 执行本地操作**: `order-service` 执行其本地业务逻辑（本示例中简化）。
3.  **TM - 远程调用**: `order-service` 通过 Feign 客户端调用 `storage-service`。Seata 的 Feign 拦截器会自动将 XID 注入到请求头中，传播到下游服务。
4.  **RM - 处理分支事务**:
    *   `storage-service` 接收到请求，Seata RM 从请求头中提取 XID，并将其与当前线程绑定。
    *   `storage-service` 执行本地数据库操作（扣减库存）。这是一个分支事务。
    *   在执行 SQL 前，`DataSourceProxy` 会解析 SQL，查询前置镜像数据，并生成 UNDO_LOG。
    *   执行业务 SQL，提交本地事务。
    *   RM 向 TC 注册此分支事务，并报告其状态。
5.  **TC - 协调事务**:
    *   如果所有分支事务都成功（`order-service` 完成且 `storage-service` 报告成功），TC 会向所有相关的 RM 发送提交分支事务的指令。
    *   如果任何分支事务失败（例如 `storage-service` 抛出异常，或 `order-service` 自身异常），TC 会向所有相关的 RM 发送回滚分支事务的指令。RM 会根据之前记录的 UNDO_LOG 来恢复数据。
6.  **TM - 结束全局事务**: `order-service` 的 `@GlobalTransactional` 方法执行完毕（无论成功或失败），全局事务结束。

### 架构图 (Architecture Diagram)

```mermaid
graph TD
    Client --> OS[order-service (TM)]
    OS -- 1. Begin Global Tx --> TC[Seata Server (TC)]
    OS -- 2. Feign Call with XID --> SS[storage-service (RM)]
    SS -- 3. Execute Local Tx (Branch) --> DB_Storage[Storage DB (H2)]
    SS -- 4. Report Branch Status --> TC
    TC -- 5. Coordinate Commit/Rollback --> OS
    TC -- 5. Coordinate Commit/Rollback --> SS
    OS --> DB_Order[Order DB (H2)]
end
```

## 3. 技术栈 (Technology Stack)

*   **Spring Boot**: 2.3.2.RELEASE
*   **Seata**: 1.4.2 (包括 `seata-spring-boot-starter`)
*   **Spring Cloud OpenFeign**: (版本由 Spring Boot 管理或显式定义，e.g., 2.2.9.RELEASE)
*   **H2 Database**: In-memory RDBMS
*   **Build Tool**: Maven
*   **Programming Language**: Java 8

## 4. 实现逻辑与关键代码 (Implementation Logic and Key Code)

### 4.1. Seata Server 配置 (Conceptual)

*   **独立运行**: Seata Server (TC) 需要作为独立的 Java 进程运行。可以从 Seata 官网下载预编译的服务器包或从源码构建。
*   **客户端连接配置**: 客户端服务 (`order-service`, `storage-service`) 通过其资源目录下的 `registry.conf` 和 `file.conf` 文件来配置 Seata Server 的地址和事务组信息。
    *   **`registry.conf`**: 定义注册中心和配置中心的类型。对于简单场景，两者都可设为 `file`。
        ```
        registry {
          type = "file"
          file {
            name = "file.conf"
          }
        }
        config {
          type = "file"
          file {
            name = "file.conf"
          }
        }
        ```
    *   **`file.conf`**: 当 `registry.type` 和 `config.type` 为 `file` 时，此文件提供具体配置。
        ```
        service {
          vgroup_mapping.my_test_tx_group = "default" # 事务组名称映射
          default.grouplist = "127.0.0.1:8091"       # Seata Server TC 地址
          enableDegrade = false
          disableGlobalTransaction = false
        }
        ```
        (这里的 `my_test_tx_group` 必须与应用 `application.yml` 中的 `seata.tx-service-group` 一致。)

### 4.2. `order-service`

#### Maven 依赖 (`pom.xml`)
```xml
<dependencies>
    <!-- Spring Boot Web & Actuator -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    
    <!-- Seata Starter -->
    <dependency>
        <groupId>io.seata</groupId>
        <artifactId>seata-spring-boot-starter</artifactId>
        <version>${seata.version}</version> <!-- e.g., 1.4.2 -->
    </dependency>
    
    <!-- Spring Cloud OpenFeign -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-openfeign</artifactId>
        <version>2.2.9.RELEASE</version> <!-- Ensure compatibility -->
    </dependency>
    
    <!-- H2 Database & JDBC -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

#### 应用配置 (`application.yml`)
```yaml
server:
  port: 8081
spring:
  application:
    name: order-service
  main:
    allow-bean-definition-overriding: true # Often needed for Seata
  datasource: # H2 Datasource for order-service (though not heavily used in this simplified example)
    url: jdbc:h2:mem:order_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driverClassName: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console-order
# Seata Configuration
seata:
  tx-service-group: my_test_tx_group # Must match file.conf's vgroup_mapping and TC's config
  # service.vgroup-mapping.my_test_tx_group is set in file.conf
# Feign client configuration for storage-service
storage-service-url: http://localhost:8082/storage # URL for storage-service
logging:
  level:
    io.seata: INFO # Set to DEBUG for more detailed Seata logs
    io.seata.sample.orderservice: DEBUG
```

#### Seata 文件配置 (`registry.conf`, `file.conf`)
位于 `src/main/resources/` 目录下，内容如 4.1 节所述。

#### 主应用类 (`OrderServiceApplication.java`)
```java
package io.seata.sample.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients // Enables scanning for @FeignClient interfaces
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

#### Controller (`OrderController.java`)
```java
package io.seata.sample.orderservice;

import io.seata.core.context.RootContext;
import io.seata.spring.annotation.GlobalTransactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderController.class);

    @Autowired
    private StorageServiceClient storageServiceClient;

    @PostMapping("/create")
    @GlobalTransactional // Key: Starts the Seata global transaction
    public ResponseEntity<String> createOrder(@RequestBody Order orderRequest) {
        LOGGER.info("OrderService: Received request to create order for commodity: {}. XID: {}",
                orderRequest.getCommodityCode(), RootContext.getXID());

        // Simulate local order creation (in a real app, this would involve DB writes)
        LOGGER.info("OrderService: Order created for commodity: {}, count: {}",
                orderRequest.getCommodityCode(), orderRequest.getCount());

        LOGGER.info("OrderService: Calling storage service to deduct stock...");
        boolean stockDeducted = storageServiceClient.deductStock(
                orderRequest.getCommodityCode(), orderRequest.getCount());

        if (stockDeducted) {
            LOGGER.info("OrderService: Stock deducted successfully. Global transaction will commit.");
            return ResponseEntity.ok("Order created and stock deducted successfully.");
        } else {
            // This path might not be hit if deductStock throws an exception,
            // which is the more common way to signal failure to Seata for rollback.
            LOGGER.error("OrderService: Failed to deduct stock. Initiating rollback.");
            throw new RuntimeException("Stock deduction failed, transaction will be rolled back by Seata.");
        }
    }
}
```
`@GlobalTransactional` initiates the distributed transaction. If `storageServiceClient.deductStock` throws an exception (or if an exception is thrown here), Seata will orchestrate a rollback.

#### Feign 客户端 (`StorageServiceClient.java`)
```java
package io.seata.sample.orderservice;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "storage-service", url = "${storage-service-url}")
public interface StorageServiceClient {
    @PostMapping("/storage/deduct")
    boolean deductStock(@RequestParam("commodityCode") String commodityCode, @RequestParam("count") Integer count);
}
```

### 4.3. `storage-service`

#### Maven 依赖 (`pom.xml`)
Similar to `order-service`, including `seata-spring-boot-starter`, `spring-boot-starter-web`, `h2`, `spring-boot-starter-jdbc`. OpenFeign is not needed here as it's the service provider.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>io.seata</groupId>
        <artifactId>seata-spring-boot-starter</artifactId>
        <version>${seata.version}</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

#### 应用配置 (`application.yml`)
```yaml
server:
  port: 8082
spring:
  application:
    name: storage-service
  main:
    allow-bean-definition-overriding: true
  datasource:
    url: jdbc:h2:mem:storage_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    driverClassName: org.h2.Driver
    username: sa
    password:
  jpa: # Ensure Seata's DataSourceProxy is used
    hibernate:
      ddl-auto: update # or create/create-drop for demo
  h2:
    console:
      enabled: true
      path: /h2-console-storage
seata:
  tx-service-group: my_test_tx_group # Must match order-service and TC config
  # For AT mode, Seata requires a table `undo_log` in the business database.
  # It's usually created automatically by Seata if auto-creation is enabled,
  # or you might need to create it manually:
  # CREATE TABLE IF NOT EXISTS `undo_log` (
  #   `branch_id`     BIGINT       NOT NULL COMMENT 'branch transaction id',
  #   `xid`           VARCHAR(100) NOT NULL COMMENT 'global transaction id',
  #   `context`       VARCHAR(128) NOT NULL COMMENT 'undo_log context,such as serialization',
  #   `rollback_info` LONGBLOB     NOT NULL COMMENT 'rollback info',
  #   `log_status`    INT(11)      NOT NULL COMMENT '0:normal status,1:defense status',
  #   `log_created`   DATETIME(6)  NOT NULL COMMENT 'create datetime',
  #   `log_modified`  DATETIME(6)  NOT NULL COMMENT 'modify datetime',
  #   UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`)
  # ) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COMMENT = 'AT transaction mode undo table';
logging:
  level:
    io.seata: INFO
    io.seata.sample.storageservice: DEBUG
```
**Important**: For Seata AT mode, each business database managed by Seata RM needs an `undo_log` table. `DataSourceProxy` uses this table to store data snapshots for rollback purposes.

#### Seata 文件配置 (`registry.conf`, `file.conf`)
Located in `src/main/resources/`, identical to those in `order-service` (see 4.1).

#### Controller (`StorageController.java`)
```java
package io.seata.sample.storageservice;

import io.seata.core.context.RootContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import javax.annotation.PostConstruct;

@RestController
@RequestMapping("/storage")
public class StorageController {
    private static final Logger LOGGER = LoggerFactory.getLogger(StorageController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate; // Using JdbcTemplate for simplicity

    @PostConstruct
    public void init() { // Initialize storage table and sample data
        jdbcTemplate.update("DROP TABLE IF EXISTS storage_tbl;");
        jdbcTemplate.update("CREATE TABLE storage_tbl (id BIGINT AUTO_INCREMENT PRIMARY KEY, commodity_code VARCHAR(255), count INT);");
        jdbcTemplate.update("INSERT INTO storage_tbl (commodity_code, count) VALUES ('product-1', 100);");
        jdbcTemplate.update("INSERT INTO storage_tbl (commodity_code, count) VALUES ('product-2', 0);"); // product-2 has 0 stock for testing
        LOGGER.info("StorageService: storage_tbl initialized with sample data. Current XID: {}", RootContext.getXID());
        // Note: XID might be null here if not in a global transaction context during init.
    }

    @PostMapping("/deduct")
    public ResponseEntity<Boolean> deductStock(@RequestParam String commodityCode, @RequestParam Integer count) {
        LOGGER.info("StorageService: Received request to deduct stock for commodity: {}, count: {}. Current XID: {}",
                commodityCode, count, RootContext.getXID());

        String checkStockSql = "SELECT count FROM storage_tbl WHERE commodity_code = ?";
        Integer currentStock = jdbcTemplate.queryForObject(checkStockSql, new Object[]{commodityCode}, Integer.class);

        if (currentStock == null || currentStock < count) {
            LOGGER.error("StorageService: Insufficient stock for commodity: {}. Required: {}, Available: {}. Global transaction will be rolled back.",
                    commodityCode, count, (currentStock == null ? "N/A" : currentStock));
            // Key: Throw an exception to signal Seata RM that this branch transaction failed.
            throw new RuntimeException("Insufficient stock for " + commodityCode);
        }

        String sql = "UPDATE storage_tbl SET count = count - ? WHERE commodity_code = ?";
        int updatedRows = jdbcTemplate.update(sql, count, commodityCode);

        if (updatedRows > 0) {
            LOGGER.info("StorageService: Stock deducted successfully for commodity: {}", commodityCode);
            return ResponseEntity.ok(true);
        } else {
            // This case might be redundant if the stock check above is robust.
            LOGGER.error("StorageService: Failed to deduct stock for commodity (unexpected error): {}", commodityCode);
            throw new RuntimeException("Failed to deduct stock for " + commodityCode + " (update failed)");
        }
    }
}
```
The key here is that if stock deduction is not possible (e.g., insufficient stock), the method throws a `RuntimeException`. This exception is caught by Seata's RM, which then informs the TC that this branch transaction failed, leading to a global rollback.

## 5. 如何运行与查看 (How to Run and View)

1.  **启动外部 Seata Server (TC)**:
    *   Download Seata Server (e.g., version 1.4.2) from [Seata Releases](https://github.com/seata/seata/releases).
    *   Extract and run it. Typically, you use scripts in its `bin/` directory (e.g., `seata-server.sh` or `seata-server.bat`). Ensure its configuration (e.g., `conf/file.conf`, `conf/registry.conf`) matches what clients expect, particularly the server port (default 8091) and transaction group settings.
    *   The Seata server needs to be running and accessible by `order-service` and `storage-service`.

2.  **启动 `storage-service`**:
    *   Navigate to the `seata/storage-service` module.
    *   Run `mvn spring-boot:run` or start via IDE.
    *   Observe logs for successful startup and connection to Seata TC (if logging level for `io.seata` is `INFO` or `DEBUG`).
    *   Access its H2 console (e.g., `http://localhost:8082/h2-console-storage`) to verify `storage_tbl` and `undo_log` tables.

3.  **启动 `order-service`**:
    *   Navigate to the `seata/order-service` module.
    *   Run `mvn spring-boot:run` or start via IDE.
    *   Observe logs for successful startup and connection to Seata TC.

4.  **触发分布式事务**:
    *   Use a tool like Postman or `curl` to send a POST request to `order-service`'s endpoint:
        `POST http://localhost:8081/orders/create`
    *   **Request Body (JSON)**:
        *   **Success Scenario**: `{"commodityCode": "product-1", "count": 10}`
            *   Expected: HTTP 200 OK. Logs in both services should show XID propagation. `storage_tbl` for `product-1` should have its count reduced.
        *   **Failure Scenario (Insufficient Stock)**: `{"commodityCode": "product-1", "count": 1000}` (assuming initial stock is 100)
            *   Expected: HTTP 500 Internal Server Error (or whatever your global exception handler returns for `RuntimeException`). `storage-service` log will show an error about insufficient stock and throw an exception. `order-service` log might show transaction rollback. `storage_tbl` for `product-1` should remain unchanged due to rollback.
        *   **Failure Scenario (Product with Zero Stock)**: `{"commodityCode": "product-2", "count": 1}` (assuming `product-2` has 0 stock)
            *   Expected: Similar to insufficient stock, resulting in a rollback.

5.  **观察与验证**:
    *   **Service Logs**: Check logs of `order-service`, `storage-service`, and Seata Server (TC) for transaction XIDs, branch registration, commit/rollback messages.
    *   **H2 Consoles**:
        *   `storage-service` H2 console: `http://localhost:8082/h2-console-storage` (JDBC URL: `jdbc:h2:mem:storage_db`)
        *   `order-service` H2 console: `http://localhost:8081/h2-console-order` (JDBC URL: `jdbc:h2:mem:order_db`)
        *   Verify data in `storage_tbl` after successful and failed transactions.
        *   Examine the `undo_log` table in `storage_db` after a transaction is processed by `storage-service` (before global commit/rollback completes, an entry exists; after completion, it's usually deleted for successful commits or used and deleted for rollbacks).

---
This document provides a technical overview of the Seata module, focusing on its AT mode for distributed transaction management. For production environments, careful consideration of Seata Server deployment (cluster mode), database configurations, and performance tuning is essential.
