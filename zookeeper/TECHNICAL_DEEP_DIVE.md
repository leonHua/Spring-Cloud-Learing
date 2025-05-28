# Zookeeper 模块技术深度解析 (Technical Deep Dive)

## 1. 模块概述 (Module Overview)

此模块旨在演示如何使用 Apache Zookeeper 作为服务注册与发现中心以及配置中心。它包含一个服务消费者 (`leon-consumer`) 和一个服务提供者 (`leon-provider`)。通过这两个组件，模块展示了 Spring Cloud 应用如何利用 Zookeeper 实现服务的动态发现、客户端负载均衡以及外部化配置的集中管理和动态刷新。

## 2. 架构设计 (Architecture Design)

### 核心组件 (Core Components)

*   **`leon-consumer`**:
    *   **类型**: Spring Boot 应用。
    *   **集成**:
        *   **Zookeeper Discovery Client**: 用于从 Zookeeper Ensemble 发现 `leon-provider` 服务的实例。
        *   **Zookeeper Config Client**: 用于从 Zookeeper Ensemble 的特定 ZNode 路径中加载和动态刷新应用配置。
        *   **Spring Cloud Feign**: 用于以声明方式调用在 Zookeeper 中注册的 `leon-provider` 服务。
*   **`leon-provider`**:
    *   **类型**: Spring Boot 应用。
    *   **集成**:
        *   **Zookeeper Discovery Client**: 用于向 Zookeeper Ensemble 注册自身，以便其他服务可以发现它。
        *   (虽然 `spring-cloud-starter-zookeeper-config` 依赖也存在于 `leon-provider`，但在此场景中，它主要作为服务提供者，可能未显式使用 Zookeeper 进行自身的配置管理，而是依赖于 `leon-consumer` 的配置能力。)
    *   **职责**: 提供一个 `/login` HTTP 端点作为示例服务。
*   **Zookeeper Ensemble (外部)**:
    *   **类型**: 独立的 Zookeeper 服务实例或集群。
    *   **功能**:
        *   **服务注册表**: 存储所有已注册服务实例的信息（服务名、IP、端口等）在临时的 ZNode 下。
        *   **服务发现机制**: 允许客户端查询可用的服务实例列表，并监听服务实例的变化。
        *   **配置管理服务**: 提供存储、管理和分发应用配置的功能，通过在 ZNode 中存储数据实现，支持配置的动态更新。

### 数据流与交互 (Data Flow and Interactions)

1.  **服务注册**: `leon-provider` 在启动时，通过 Zookeeper Discovery Client 在 Zookeeper Ensemble 的服务路径下 (例如 `/services/leon-provider/<instance-id>`) 创建一个临时的 ZNode 来注册其服务信息。
2.  **配置加载**: `leon-consumer` 在启动时，通过 Zookeeper Config Client 连接到 Zookeeper Ensemble，并根据其应用名 (`leon-consumer`) 和配置的根路径 (例如 `/config`)，从 Zookeeper 的特定 ZNode (例如 `/config/leon-consumer` 或 `/config/leon-consumer.properties`) 拉取其配置信息。
3.  **服务发现**: 当 `leon-consumer` 需要调用 `leon-provider` 服务时，它会向 Zookeeper Ensemble 查询路径 `/services/leon-provider` 下的子节点，以获取所有健康的服务实例列表。
4.  **客户端负载均衡**: `leon-consumer` 中的 `UserFeignClient` (一个 Spring Cloud Feign 客户端) 利用 Zookeeper 提供的服务实例列表，在客户端进行负载均衡（通常是轮询或随机选择），将请求分发到 `leon-provider` 的 `/login` 端点。
5.  **动态配置更新**: Zookeeper Config 允许 `leon-consumer` 应用的配置在运行时动态更新。通过在相关的 Bean (例如 `UserController` 或专门的配置 Bean) 上使用 `@RefreshScope` 注解，当 Zookeeper 中对应的配置 ZNode 数据发生变化时，这些 Bean 可以被刷新以获取最新的配置值，而无需重启应用。

### 架构图 (Architecture Diagram)

```mermaid
graph TD
    User --> C[leon-consumer]
    C -- Feign (Load Balanced) --> P[leon-provider]
    C -- Fetches Config / Discovers Services --> ZK[Zookeeper Ensemble]
    P -- Registers --> ZK
end
```

## 3. 技术栈 (Technology Stack)

*   **Spring Boot**: 1.5.21.RELEASE
*   **Spring Cloud**: Edgware.SR5
*   **Spring Cloud Zookeeper Discovery**: (版本由 Spring Cloud Edgware.SR5 管理)
*   **Spring Cloud Zookeeper Config**: (版本由 Spring Cloud Edgware.SR5 管理)
*   **Spring Cloud Feign**: (版本由 Spring Cloud Edgware.SR5 管理)
*   **Build Tool**: Maven
*   **Programming Language**: Java 8
*   **Service Discovery & Configuration**: Apache Zookeeper (外部运行)

## 4. 实现逻辑与关键代码 (Implementation Logic and Key Code)

### 4.1. 服务提供者 (`leon-provider`)

#### Maven 依赖 (`pom.xml`)

`leon-provider/pom.xml` 关键依赖：
```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-zookeeper-discovery</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-zookeeper-config</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

#### 应用配置 (`application.yml`)

**`zookeeper/leon-provider/src/main/resources/application.yml`**
```yaml
server:
  port: 8081
spring:
  application:
    name: leon-provider
  cloud:
    zookeeper:
      connect-string: localhost:2181 # Zookeeper ensemble address(es)
      discovery:
        enabled: true
        register: true # Ensure the service registers itself
        # root: /services # Default root path for services
        # instance-id: can be customized
```

#### 主应用类 (`LeonProviderApplication.java`)

**`zookeeper/leon-provider/src/main/java/com/leon/LeonProviderApplication.java`** (Package may vary)
```java
package com.leon; // Adjust package if necessary

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient // Enables Zookeeper service registration and discovery
public class LeonProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeonProviderApplication.class, args);
    }
}
```

#### 服务类 (`LoginService.java`)

**`zookeeper/leon-provider/src/main/java/com/leon/service/LoginService.java`** (Package and class name may vary)
```java
package com.leon.service; // Adjust package if necessary

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletRequest;

@RestController
public class LoginService {
    
    @Value("${server.port}")
    private String port;

    @GetMapping("/login")
    public String login(@RequestParam String userName, @RequestParam String passWord, HttpServletRequest request) {
        System.out.println("Provider (" + port + ") received login request for: " + userName);
        return "Login successful for " + userName + "! Processed by provider instance on port " + port;
    }
}
```

### 4.2. 服务消费者 (`leon-consumer`)

#### Maven 依赖 (`pom.xml`)

`leon-consumer/pom.xml` 关键依赖：
```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-zookeeper-discovery</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-zookeeper-config</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-feign</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

#### Bootstrap 配置 (`bootstrap.yml`)

Zookeeper 配置需要在 `bootstrap.yml` (或 `bootstrap.properties`) 中定义，以便在应用上下文早期加载。

**`zookeeper/leon-consumer/src/main/resources/bootstrap.yml`**
```yaml
server:
  port: 8080
spring:
  application:
    name: leon-consumer
  cloud:
    zookeeper:
      connect-string: localhost:2181 # Zookeeper ensemble address(es)
      discovery:
        enabled: true
        register: true # Consumer can also register itself if needed (e.g., for admin purposes)
      config:
        enabled: true
        root: /config # ZNode root path for configurations
        # defaultContext: application # Default context is spring.application.name (leon-consumer)
        # profileSeparator: '-' # Default profile separator
        # Zookeeper Config will look for paths like /config/leon-consumer, /config/leon-consumer-<profile>, /config/application, etc.
```

#### 主应用类 (`LeonConsumerApplication.java`)

**`zookeeper/leon-consumer/src/main/java/com/leon/LeonConsumerApplication.java`** (Package may vary)
```java
package com.leon; // Adjust package if necessary

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.feign.EnableFeignClients; // Correct Feign import for Edgware

@SpringBootApplication
@EnableDiscoveryClient // Enables Zookeeper service discovery
@EnableFeignClients   // Scans for Feign client interfaces
public class LeonConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeonConsumerApplication.class, args);
    }
}
```

#### Feign 客户端 (`UserFeignClient.java`)

**`zookeeper/leon-consumer/src/main/java/com/leon/feign/UserFeignClient.java`** (Package may vary)
```java
package com.leon.feign; // Adjust package if necessary

import org.springframework.cloud.netflix.feign.FeignClient; // Correct Feign import for Edgware
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "leon-provider") // Must match the spring.application.name of the provider service
public interface UserFeignClient {

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    String login(@RequestParam("userName") String userName, @RequestParam("passWord") String passWord);
}
```

#### Controller (`UserController.java`)

**`zookeeper/leon-consumer/src/main/java/com/leon/controller/UserController.java`** (Package may vary)
```java
package com.leon.controller; // Adjust package if necessary

import com.leon.feign.UserFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RefreshScope // Allows properties injected with @Value to be updated when Zookeeper config changes
public class UserController {

    @Autowired
    private UserFeignClient userFeignClient;

    // Example: Properties loaded from Zookeeper configuration
    @Value("${name:DefaultNameFromCode}") // Default value if 'name' is not found in Zookeeper
    private String configName;

    @Value("${age:0}") // Default value if 'age' is not found in Zookeeper
    private int configAge;

    @GetMapping("/login")
    public String login(@RequestParam String userName, @RequestParam String passWord) {
        System.out.println("Consumer calling provider for login: " + userName);
        return userFeignClient.login(userName, passWord);
    }

    @GetMapping("/config")
    public String getConfig() {
        return "Name from Zookeeper: " + configName + ", Age from Zookeeper: " + configAge;
    }
}
```
`@RefreshScope` on `UserController` allows the `@Value` annotated fields (`configName`, `configAge`) to be updated if their corresponding ZNode data changes in Zookeeper. A POST request to the `/refresh` Actuator endpoint is typically needed to trigger the refresh.

## 5. 如何运行与查看 (How to Run and View)

1.  **启动 Zookeeper 服务器**:
    *   下载并启动 Apache Zookeeper. 对于单机模式, 运行 `bin/zkServer.sh start` (Linux/macOS) or `bin\zkServer.cmd start` (Windows).
    *   Zookeeper 默认监听客户端连接在端口 `2181`.

2.  **在 Zookeeper 中配置**:
    *   使用 Zookeeper 客户端 (`zkCli.sh` or `zkCli.cmd`)连接到 Zookeeper.
    *   创建配置的根路径 (如果 `spring.cloud.zookeeper.config.root` 是 `/config`):
        ```bash
        create /config ""
        ```
    *   为 `leon-consumer` 创建配置。Spring Cloud Zookeeper Config 会查找 `/config/${spring.application.name}` (e.g., `/config/leon-consumer`) 或 `/config/${spring.application.name}.${fileExtension}` (e.g., `/config/leon-consumer.properties`).
        *   假设期望的配置格式是 properties-like, 你可以创建一个 ZNode `/config/leon-consumer` 并将其数据设置为 properties 格式的字符串:
            ```bash
            create /config/leon-consumer "name=ZookeeperLeon\nage=30" 
            ```
            (Note: Multi-line properties in `zkCli` can be tricky. Alternatively, use a tool or manage as separate ZNodes if your setup expects that, e.g., `/config/leon-consumer/name` with value `ZookeeperLeon` and `/config/leon-consumer/age` with value `30`.)
        *   Spring Cloud Zookeeper Config also supports YAML. If data is stored as YAML, ensure your ZNode data reflects that.

3.  **启动服务提供者 (`leon-provider`)**:
    *   导航到 `zookeeper/leon-provider` 模块。
    *   运行 `mvn spring-boot:run` 或通过 IDE 启动 `LeonProviderApplication`。
    *   服务将运行在 `http://localhost:8081`。
    *   使用 `zkCli.sh`, 你可以查看服务注册情况，例如 `ls /services/leon-provider`，应该会列出一个或多个临时节点代表实例。

4.  **启动服务消费者 (`leon-consumer`)**:
    *   导航到 `zookeeper/leon-consumer` 模块。
    *   运行 `mvn spring-boot:run` 或通过 IDE 启动 `LeonConsumerApplication`。
    *   服务将运行在 `http://localhost:8080`。

5.  **验证与查看**:
    *   **Zookeeper CLI**:
        *   `ls /services/leon-provider`: 查看 `leon-provider` 实例。
        *   `get /config/leon-consumer`: 查看 `leon-consumer` 的配置数据。
    *   **`leon-consumer` 端点**:
        *   **调用服务**: 访问 `http://localhost:8080/login?userName=TestUserZK&passWord=secret`。
            *   预期结果: 浏览器显示 `Login successful for TestUserZK! Processed by provider instance on port 8081`。
        *   **查看配置**: 访问 `http://localhost:8080/config`。
            *   预期结果: 浏览器显示 `Name from Zookeeper: ZookeeperLeon, Age from Zookeeper: 30`。
    *   **动态配置刷新**:
        *   在 Zookeeper 中使用 `zkCli.sh` 修改配置数据，例如:
            ```bash
            set /config/leon-consumer "name=UpdatedZkLeon\nage=35"
            ```
        *   向 `leon-consumer` 的 `/refresh` Actuator 端点发送一个 POST 请求 (例如 `curl -X POST http://localhost:8080/refresh`).
        *   再次访问 `http://localhost:8080/config`。
            *   预期结果: 浏览器显示更新后的配置值: `Name from Zookeeper: UpdatedZkLeon, Age from Zookeeper: 35`。

---
此文档提供了对 `Zookeeper` 模块如何利用 Apache Zookeeper 进行服务发现和配置管理的深入理解。实际部署时，请确保 Zookeeper 集群的稳定性和高可用性，并根据环境调整安全和网络配置。
