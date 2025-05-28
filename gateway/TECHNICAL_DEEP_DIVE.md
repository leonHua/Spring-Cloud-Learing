# Spring Cloud Gateway 模块技术深度解析 (Technical Deep Dive)

## 1. 模块概述 (Module Overview)

此模块演示了如何使用 Spring Cloud Gateway 作为 API 网关，并与 Netflix Eureka 服务发现机制集成。API 网关是微服务架构中的一个关键组件，它为外部客户端提供了一个统一的入口点来访问内部的微服务。Spring Cloud Gateway 提供了灵活的路由机制、过滤器链以及与服务发现的无缝集成。

核心组件包括：
*   **`leon-gateway`**: 网关应用，负责处理所有入站请求，根据配置的路由规则将它们转发到适当的后端服务。它本身也是一个 Eureka 客户端，以便能够发现其他服务。
*   **`leon-consumer`**: 一个示例后端 Spring Boot 服务，它向 Eureka 注册，并提供一些 API 端点供网关路由。
*   **`leon-eureka`**: Eureka 服务注册中心，`leon-gateway` 和 `leon-consumer` 都会向其注册，并从中发现其他服务。

## 2. 架构设计 (Architecture Design)

### 核心组件 (Core Components)

*   **`leon-gateway`**:
    *   **类型**: Spring Cloud Gateway 应用。
    *   **职责**:
        *   **路由 (Routing)**: 根据请求的属性（如路径、HTTP 方法、查询参数、头部信息等）将请求动态路由到后端服务。
        *   **过滤 (Filtering)**: 在请求被路由之前或之后应用一系列过滤器。这些过滤器可以用于身份验证、日志记录、请求转换等。本模块包含一个自定义的全局过滤器 (`GlobalFilter.java`) 进行 token 验证。
        *   **服务发现**: 作为 Eureka 客户端，它可以从 `leon-eureka` 服务注册中心动态获取后端服务 (`leon-consumer`) 的实例地址。
*   **`leon-consumer`**:
    *   **类型**: Spring Boot 微服务应用。
    *   **职责**:
        *   提供业务 API 端点 (例如 `/user/login`, `/user/info`)。
        *   向 `leon-eureka` 服务注册中心注册自身，以便其他服务（如 `leon-gateway`）可以发现它。
*   **`leon-eureka`**:
    *   **类型**: Spring Cloud Netflix Eureka Server。
    *   **职责**: 维护一个注册了所有可用服务实例的服务清单。服务提供者（如 `leon-consumer`）和服务消费者（如 `leon-gateway`）都依赖它。

### 请求流程 (Request Flow)

1.  **客户端请求 (Client Request)**: 外部客户端向 `leon-gateway` 发起 API 请求。
2.  **网关处理 (Gateway Processing)**:
    *   `leon-gateway` 接收到请求。
    *   **全局过滤器 (Global Filters)**: 请求首先经过全局过滤器。在本例中，`GlobalFilter.java` 会检查请求头中是否包含有效的 `token`。如果 token 无效或缺失，请求将被拒绝。
    *   **路由匹配 (Route Matching)**: 如果请求通过了全局过滤器，网关会根据其配置的路由规则（基于谓词，如路径、查询参数等）来确定将请求转发到哪个后端服务。
    *   **服务发现 (Service Discovery)**: 对于配置为使用服务发现的路由 (例如 `uri: lb://leon-consumer`)，网关会查询 `leon-eureka` 以获取 `leon-consumer` 服务的可用实例列表，并进行客户端负载均衡。
    *   **请求转发 (Request Forwarding)**: 网关将请求转发到选定的后端服务实例 (`leon-consumer`)。
3.  **后端服务处理 (Backend Service Processing)**: `leon-consumer` 接收到请求，处理它，并返回响应。
4.  **响应返回 (Response Return)**: 响应沿着相同的路径返回给客户端，途中可能会经过网关的响应过滤器（如果已配置）。

### 架构图 (Architecture Diagram)

```mermaid
graph TD
    Client --> GW[leon-gateway]
    GW -- Routes Request (based on Predicates & Filters) --> BE[leon-consumer]
    GW -- Registers & Discovers --> E[leon-eureka]
    BE[leon-consumer] -- Registers --> E
end
```

## 3. 技术栈 (Technology Stack)

*   **Spring Boot**: 2.0.5.RELEASE
*   **Spring Cloud**: Finchley.SR1
*   **Spring Cloud Gateway**: 核心网关实现
*   **Spring Cloud Netflix Eureka Server**: 服务注册中心
*   **Spring Cloud Netflix Eureka Client**: 用于服务注册与发现
*   **Build Tool**: Maven
*   **Programming Language**: Java 8

## 4. 实现逻辑与关键代码 (Implementation Logic and Key Code)

### 4.1. Eureka 服务 (`leon-eureka`)

#### Maven 依赖 (`pom.xml`)
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
</dependency>
```

#### 应用配置 (`application.yml`)
**`gateway/leon-eureka/src/main/resources/application.yml`**
```yaml
server:
  port: 8080 # Eureka server port
eureka:
  instance:
    hostname: localhost
  client:
    register-with-eureka: false # Eureka server itself does not register
    fetch-registry: false       # Eureka server itself does not fetch registry
    service-url:
      defaultZone: http://${eureka.instance.hostname}:${server.port}/eureka/
```

#### 主应用类 (`LeonEurekaApplication.java`)
**`gateway/leon-eureka/src/main/java/com/leon/EurekaApplication.java`** (Package and class name might vary slightly based on actual project structure)
```java
package com.leon; // Adjust package if necessary

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer // Enables this application to act as a Eureka server
public class EurekaApplication { // Class name might be LeonEurekaApplication

    public static void main(String[] args) {
        SpringApplication.run(EurekaApplication.class, args);
    }
}
```

### 4.2. 后端服务 (`leon-consumer`)

#### Maven 依赖 (`pom.xml`)
```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    </dependency>
</dependencies>
```

#### 应用配置 (`application.yml`)
**`gateway/leon-consumer/src/main/resources/application.yml`**
```yaml
server:
  port: 8081
spring:
  application:
    name: leon-consumer # Service name used for registration in Eureka
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8080/eureka/ # Address of the Eureka server
  instance:
    prefer-ip-address: true # Optional: register with IP address
```

#### 主应用类 (`LeonConsumerApplication.java`)
**`gateway/leon-consumer/src/main/java/com/leon/ConsumerApplication.java`** (Package and class name might vary)
```java
package com.leon; // Adjust package if necessary

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient; // Or @EnableDiscoveryClient

@SpringBootApplication
@EnableEurekaClient // Enables this application as a Eureka client
public class ConsumerApplication { // Class name might be LeonConsumerApplication

    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class, args);
    }
}
```

#### Controller (`UserController.java`)
**`gateway/leon-consumer/src/main/java/com/leon/controller/UserController.java`** (Package might vary)
```java
package com.leon.controller; // Adjust package if necessary

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/user")
public class UserController {

    @GetMapping("/login")
    public String login(@RequestParam String name, HttpServletRequest request) {
        String token = request.getHeader("token");
        return "User '" + name + "' logged in successfully via port " + request.getLocalPort() + ". Token: " + token;
    }

    @GetMapping("/info")
    public String info(@RequestParam String name, HttpServletRequest request) {
        String token = request.getHeader("token");
        return "Info for user '" + name + "' from port " + request.getLocalPort() + ". Token: " + token;
    }
}
```

### 4.3. API 网关 (`leon-gateway`)

#### Maven 依赖 (`pom.xml`)
```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-gateway</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    </dependency>
</dependencies>
```

#### 应用配置 (`application.yml`)
**`gateway/leon-gateway/src/main/resources/application.yml`**
```yaml
server:
  port: 8085 # Gateway port
spring:
  application:
    name: leon-gateway
  cloud:
    gateway:
      routes:
        # Example route using Query predicate
        - id: query_route 
          uri: http://localhost:8081/info # Hardcoded URI for leon-consumer's /info endpoint
          # IMPORTANT: In production, prefer service discovery:
          # uri: lb://leon-consumer/info 
          # (assuming leon-consumer's spring.application.name is 'leon-consumer')
          predicates:
            - Query=name, leon. # Matches if query param 'name' has value 'leon.' (note the trailing dot)
          # filters:
            # - AddRequestHeader=X-Request-Foo, Bar

        # Other example predicate types (commented out but illustrate options):
        # - id: cookie_route
        #   uri: http://localhost:8081/login
        #   predicates:
        #     - Cookie=mycookie, myvalue
        # - id: header_route
        #   uri: http://localhost:8081/login
        #   predicates:
        #     - Header=X-Request-Id, \d+
        # - id: host_route
        #   uri: http://localhost:8081/login
        #   predicates:
        #     - Host=**.somehost.org,**.anotherhost.org
        # - id: method_route
        #   uri: http://localhost:8081/login
        #   predicates:
        #     - Method=GET
        # - id: path_route # A common use case
        #   uri: lb://leon-consumer # Using service discovery
        #   predicates:
        #     - Path=/user/** # Routes all requests starting with /user/ to leon-consumer
        #   filters:
        #     - StripPrefix=1 # Strips /user/ before forwarding to leon-consumer (e.g., /user/login becomes /login)

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8080/eureka/
  instance:
    prefer-ip-address: true
```
**关键点**:
*   `query_route` 使用 `Query` 谓词，当请求包含名为 `name` 且值为 `leon.` 的查询参数时匹配。
*   `uri: http://localhost:8081/info` 是一个硬编码的 URI。**在生产环境中，强烈建议使用服务发现的 URI 格式，例如 `uri: lb://leon-consumer/info`** (假设 `leon-consumer` 在 Eureka 中注册的服务名是 `leon-consumer`)。这将允许网关动态解析服务地址并进行负载均衡。
*   注释中列出了其他常用的谓词类型，如 `Path`, `Method`, `Header`, `Cookie`, `Host`，展示了 Spring Cloud Gateway 路由配置的灵活性。

#### 主应用类 (`LeonGatewayApplication.java`)
**`gateway/leon-gateway/src/main/java/com/leon/GatewayApplication.java`** (Package and class name might vary)
```java
package com.leon; // Adjust package if necessary

import com.leon.filter.GlobalFilter; // Assuming GlobalFilter is in this package
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableEurekaClient // Enables this application as a Eureka client
public class GatewayApplication { // Class name might be LeonGatewayApplication

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Bean
    public GlobalFilter globalFilter() {
        return new GlobalFilter();
    }
}
```

#### 全局过滤器 (`GlobalFilter.java`)
**`gateway/leon-gateway/src/main/java/com/leon/filter/GlobalFilter.java`** (Package might vary)
```java
package com.leon.filter; // Adjust package if necessary

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class GlobalFilter implements org.springframework.cloud.gateway.filter.GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(GlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        logger.info("Entering GlobalFilter: Checking for 'token' header.");
        String token = exchange.getRequest().getHeaders().getFirst("token");

        if (token == null || token.isEmpty()) {
            logger.warn("Token is missing or empty. Rejecting request.");
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        logger.info("Token found: '{}'. Allowing request to proceed.", token);
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // Filter order: lower values have higher priority.
        // Can be used to control the execution order of multiple global filters.
        return -1; // Example: give it a high priority
    }
}
```
这个全局过滤器检查所有通过网关的请求是否包含 `token` 请求头。如果 `token` 缺失或为空，请求将被拒绝并返回 401 Unauthorized 状态码。否则，请求将继续处理。`getOrder()` 方法定义了过滤器的执行顺序，值越小优先级越高。

## 5. 如何运行与查看 (How to Run and View)

1.  **启动 `leon-eureka` 服务**:
    *   导航到 `gateway/leon-eureka` 模块。
    *   运行 `mvn spring-boot:run` 或通过 IDE 启动 `EurekaApplication`。
    *   Eureka Server UI 将在 `http://localhost:8080/` 可用。

2.  **启动 `leon-consumer` 服务**:
    *   导航到 `gateway/leon-consumer` 模块。
    *   运行 `mvn spring-boot:run` 或通过 IDE 启动 `ConsumerApplication`。
    *   服务将运行在 `http://localhost:8081`。
    *   在 Eureka UI (`http://localhost:8080`) 中检查 `LEON-CONSUMER` 服务是否已注册。

3.  **启动 `leon-gateway` 服务**:
    *   导航到 `gateway/leon-gateway` 模块。
    *   运行 `mvn spring-boot:run` 或通过 IDE 启动 `GatewayApplication`。
    *   网关将运行在 `http://localhost:8085`。
    *   在 Eureka UI (`http://localhost:8080`) 中检查 `LEON-GATEWAY` 服务是否已注册。

4.  **通过网关访问服务**:
    *   **测试全局过滤器 (无 Token)**:
        *   尝试访问: `http://localhost:8085/info?name=leon.` (注意，根据 `query_route` 配置，查询参数 `name` 的值必须是 `leon.`)
        *   预期结果: HTTP 401 Unauthorized，因为请求头中没有 `token`。控制台日志中会显示 `GlobalFilter` 的警告信息。
    *   **测试全局过滤器和路由 (带 Token)**:
        *   使用 Postman 或 `curl` 构造一个请求，包含 `token` 请求头和正确的查询参数。
        *   例如，使用 `curl`:
            ```bash
            curl -H "token: my-secret-token" "http://localhost:8085/info?name=leon."
            ```
        *   预期结果: 成功从 `leon-consumer` (在端口 8081) 获取响应，内容类似: `Info for user 'leon.' from port 8081. Token: my-secret-token`。 `GlobalFilter` 日志会显示 token 已找到。
    *   **测试其他路由 (如果配置了)**:
        *   如果配置了基于路径的路由，如 `Path=/user/**` 指向 `lb://leon-consumer`，则可以尝试:
            ```bash
            curl -H "token: my-secret-token" "http://localhost:8085/user/login?name=test"
            ```
        *   预期结果: 从 `leon-consumer` 的 `/user/login` 端点获取响应。

---
此文档提供了对 `Gateway` 模块如何使用 Spring Cloud Gateway 与 Eureka 集成进行 API 路由和过滤的深入理解。实际部署时，请确保对路由规则、过滤器逻辑和安全配置进行仔细审查和调整。
