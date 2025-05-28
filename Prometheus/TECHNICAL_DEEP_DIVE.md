# Prometheus 模块技术深度解析 (Technical Deep Dive)

## 1. 模块概述 (Module Overview)

此模块的目的是演示如何使用 Prometheus 监控 Spring Boot 和 Spring Cloud 微服务应用。它展示了如何集成 Micrometer 和 Spring Boot Actuator 来暴露应用指标，以便 Prometheus 服务器可以抓取这些指标，并可选择性地通过 Grafana 进行可视化。

## 2. 架构设计 (Architecture Design)

### 核心组件 (Core Components)

*   **Spring Boot 应用 (Spring Boot Applications)**:
    *   `leon-springboot-prometheus`: 一个独立的 Spring Boot 应用，用于演示基本的 Prometheus 集成。
    *   `leon-provider`: 一个 Spring Cloud 微服务，扮演服务提供者的角色。
    *   `leon-consumer`: 一个 Spring Cloud 微服务，扮演服务消费者的角色。
*   **服务注册与发现 (Service Registry and Discovery)**:
    *   `leon-eureka-server`: 作为服务注册中心，`leon-provider` 和 `leon-consumer` 会注册到此服务。
*   **指标收集 (Metrics Collection)**:
    *   Spring Boot Actuator: 提供 `/actuator/prometheus` 端点来暴露应用指标。
    *   Micrometer: 一个应用指标外观，允许将应用内部的指标（如 JVM 指标、Tomcat 指标、自定义业务指标）转换为 Prometheus 兼容的格式。
*   **Prometheus 服务器 (External Prometheus Server)**: 负责定期从已配置的应用端点抓取指标数据并存储。
*   **Grafana (External, Optional Grafana)**: 用于连接 Prometheus 数据源，查询指标数据并创建可视化仪表盘。

### 数据流 (Data Flow)

1.  Spring Boot 应用 (`leon-springboot-prometheus`, `leon-provider`, `leon-consumer`) 启动时，Spring Boot Actuator 会自动配置。
2.  Micrometer (通过 `micrometer-registry-prometheus` 依赖) 收集应用内部的各种指标 (JVM, CPU, HTTP 请求等)，并将其转换为 Prometheus 期望的文本格式。
3.  这些指标通过 Actuator 的 `/actuator/prometheus` HTTP 端点暴露出来。
4.  外部的 Prometheus 服务器配置了抓取作业 (scrape jobs)，定期轮询这些应用的 `/actuator/prometheus` 端点。
5.  Prometheus 服务器存储抓取到的时间序列数据。
6.  (可选) Grafana 配置 Prometheus 作为数据源。用户可以在 Grafana 中创建仪表盘，通过 PromQL 查询 Prometheus 中的数据并以图表形式展示。
7.  对于 `leon-provider` 和 `leon-consumer`，它们还会向 `leon-eureka-server` 注册自身，以便进行服务发现。

### 架构图 (Architecture Diagram)

```mermaid
graph LR
    subgraph Monitored Applications
        A1[leon-springboot-prometheus]
        A2[leon-provider]
        A3[leon-consumer]
    end

    subgraph Monitoring Infrastructure
        C[Prometheus Server]
        D[Grafana (Optional)]
    end
    
    subgraph Service Discovery
        E[leon-eureka-server]
    end

    A1 -- exposes metrics --> B1[/actuator/prometheus]
    A2 -- exposes metrics --> B2[/actuator/prometheus]
    A3 -- exposes metrics --> B3[/actuator/prometheus]
    
    C -- scrapes --> B1
    C -- scrapes --> B2
    C -- scrapes --> B3
    
    D -- queries --> C
    
    A2 -- registers with --> E
    A3 -- registers with --> E
    
    F[User/Client] --> A1
    F -- via Eureka/LoadBalancer --> A2
    F -- via Eureka/LoadBalancer --> A3
end
```

## 3. 技术栈 (Technology Stack)

*   **Spring Boot**: 2.0.5.RELEASE (或类似版本)
*   **Spring Cloud**: Finchley.SR1 (或类似版本, 用于 `leon-provider` 和 `leon-consumer`)
*   **Micrometer**: 1.0.6 (核心库，以及 `micrometer-registry-prometheus`)
*   **Spring Boot Actuator**: 内置于 Spring Boot
*   **Build Tool**: Maven
*   **Programming Language**: Java 8
*   **Service Discovery**: Eureka (用于 `leon-provider` 和 `leon-consumer`)
*   **Monitoring Tools (External)**:
    *   Prometheus
    *   Grafana (可选)

## 4. 实现逻辑与关键代码 (Implementation Logic and Key Code)

### 4.1. Maven 依赖配置 (Maven Dependencies)

为了集成 Prometheus，主要需要以下依赖项：

*   `spring-boot-starter-actuator`: 提供 Actuator 功能，包括健康检查、信息端点和指标端点。
*   `micrometer-registry-prometheus`: Micrometer 的 Prometheus 注册表实现，负责将 Micrometer 收集的指标转换为 Prometheus 格式。
*   `spring-boot-starter-web`: 对于构建 Web 应用是必需的，Prometheus 端点通过 HTTP 暴露。

以下是相关 `pom.xml` 文件中的依赖片段示例 (版本可能略有不同，但核心依赖一致):

**`leon-springboot-prometheus/pom.xml` (类似配置也存在于 `leon-provider/pom.xml` 和 `leon-consumer/pom.xml`)**
```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <!-- Version typically managed by Spring Boot parent POM -->
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
        <!-- Version typically managed by Spring Boot parent POM -->
    </dependency>
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
        <!-- Version may be specified, e.g., 1.0.6, or managed by Spring Boot -->
    </dependency>
    
    <!-- For leon-provider and leon-consumer, Spring Cloud and Eureka client dependencies would also be present -->
    <!-- Example for leon-provider/consumer:
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    </dependency>
    -->
</dependencies>
```

### 4.2. 应用配置 (Application Configuration)

通过 `application.yml` (或 `application.properties`) 文件配置 Actuator 端点的暴露以及为指标添加通用标签。

**`leon-springboot-prometheus/src/main/resources/application.yml` (类似配置也适用于 `leon-provider` 和 `leon-consumer`)**
```yaml
spring:
  application:
    name: leon-springboot-prometheus # Or leon-provider, leon-consumer

server:
  port: 6060 # leon-provider might be 8081, leon-consumer 8082

management:
  endpoints:
    web:
      exposure:
        include: "*" # Exposes all actuator endpoints, including /actuator/prometheus. For production, be selective (e.g., "health,info,prometheus").
  metrics:
    tags: # Common tags applied to all metrics exported by Micrometer
      application: ${spring.application.name} # Adds a tag 'application' with the value of spring.application.name
      # You can add other common tags like region, environment, etc.
      # instance: ${HOSTNAME}:${server.port} # Example of another useful tag
```
*   `management.endpoints.web.exposure.include: "*"`: 这个配置使得所有的 Actuator 端点都通过 HTTP 暴露出来，包括 `/actuator/prometheus`。在生产环境中，出于安全考虑，通常建议只暴露必要的端点 (例如: `health,info,prometheus`)。
*   `management.metrics.tags.application: ${spring.application.name}`: 这个配置会给所有通过 Micrometer 导出的指标自动添加一个名为 `application` 的标签，其值为该应用的 `spring.application.name`。这对于在 Prometheus 中区分和聚合来自不同应用的指标非常有用。

### 4.3. Java 配置 (Optional Java Configuration)

除了在 `application.yml` 中配置通用标签，还可以通过 Java Bean 以编程方式实现。

**`leon-springboot-prometheus/src/main/java/com/leon/Application.java` (或类似的主应用类)**
```java
package com.leon; // Package name may vary

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer; // Correct import
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application { // Class name might be LeonSpringbootPrometheusApplication

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    MeterRegistryCustomizer<MeterRegistry> configurer(
            @Value("${spring.application.name}") String applicationName) {
        return (registry) -> registry.config().commonTags("application", applicationName);
    }
}
```
这个 `MeterRegistryCustomizer` Bean 的作用与在 `application.yml` 中配置 `management.metrics.tags.application` 相同。它为注册到 `MeterRegistry` 的所有 meter 添加了一个通用标签 `application`。如果同时存在 YAML 配置和 Java Bean 配置，它们的效果可能会合并，或者根据具体情况其中一个优先。通常，选择一种方式来配置通用标签以避免混淆。

### 4.4. Eureka 集成 (Eureka Integration - for Provider/Consumer)

对于 `leon-provider` 和 `leon-consumer` 这样的微服务，它们需要注册到 Eureka 服务注册中心。这通常通过在 `pom.xml` 中添加 `spring-cloud-starter-netflix-eureka-client` 依赖，并在 `application.yml` 中配置 Eureka 服务器的地址。

**`leon-provider/src/main/resources/application.yml` (类似配置在 `leon-consumer/application.yml` 中)**
```yaml
spring:
  application:
    name: leon-provider # Or leon-consumer

server:
  port: 8081 # Or 8082 for consumer

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8080/eureka/ # Assuming leon-eureka-server runs on port 8080
  instance:
    prefer-ip-address: true # Optional: register with IP address
    # metadata-map: # Exposing prometheus scrape information via Eureka metadata (optional advanced setup)
    #   prometheus.scrape: 'true'
    #   prometheus.path: '/actuator/prometheus'
    #   prometheus.port: '${server.port}'
```
这种配置使得服务能够被 Eureka 服务器发现，进而 Prometheus 可以利用 Eureka 的服务发现机制 (通过 Prometheus 的 `eureka_sd_config`) 动态地发现和抓取目标，而不是静态配置每个应用的地址。不过，本示例的重点是 Actuator 和 Micrometer 的集成，Prometheus 的服务发现配置是下一步。

## 5. 如何运行与查看 (How to Run and View)

1.  **启动服务注册中心 (Start Eureka Server)**:
    *   如果 `leon-eureka-server` 模块可用，首先构建并运行它。通常它是一个标准的 Spring Boot 应用。
    *   (假设 `leon-eureka-server` 运行在 `http://localhost:8080/`)

2.  **启动应用 (Start Applications)**:
    *   构建并运行 `leon-springboot-prometheus` (例如，监听在 `http://localhost:6060`)。
    *   构建并运行 `leon-provider` (例如，监听在 `http://localhost:8081`，并注册到 Eureka)。
    *   构建并运行 `leon-consumer` (例如，监听在 `http://localhost:8082`，并注册到 Eureka)。

3.  **查看 Actuator 端点 (View Actuator Endpoint)**:
    *   打开浏览器，访问各个应用的 `/actuator/prometheus` 端点，例如:
        *   `http://localhost:6060/actuator/prometheus` (for `leon-springboot-prometheus`)
        *   `http://localhost:8081/actuator/prometheus` (for `leon-provider`)
        *   `http://localhost:8082/actuator/prometheus` (for `leon-consumer`)
    *   你应该能看到 Prometheus 格式的指标文本数据。

4.  **配置 Prometheus 服务器 (Configure Prometheus Server)**:
    *   创建一个 `prometheus.yml` 文件 (或修改现有的) 来告诉 Prometheus 从哪里抓取指标。
    *   以下是一个基本的静态配置示例：
        ```yaml
        global:
          scrape_interval: 15s # Default is 1 minute.

        scrape_configs:
          - job_name: 'spring-micrometer-standalone'
            metrics_path: '/actuator/prometheus'
            static_configs:
              - targets: ['localhost:6060'] # For leon-springboot-prometheus
                labels:
                  application: 'leon-springboot-prometheus' # Optional: add job-level labels

          - job_name: 'spring-micrometer-provider'
            metrics_path: '/actuator/prometheus'
            static_configs:
              - targets: ['localhost:8081'] # For leon-provider
                labels:
                  application: 'leon-provider'

          - job_name: 'spring-micrometer-consumer'
            metrics_path: '/actuator/prometheus'
            static_configs:
              - targets: ['localhost:8082'] # For leon-consumer
                labels:
                  application: 'leon-consumer'
        
        # 更高级的配置可以使用 Eureka 服务发现:
        # - job_name: 'spring-eureka-apps'
        #   metrics_path: '/actuator/prometheus'
        #   eureka_sd_configs:
        #     - server: 'http://localhost:8080/eureka' # Address of your Eureka server
        #   relabel_configs:
        #     - source_labels: [__meta_eureka_app_name]
        #       target_label: application
        #     # You might need other relabeling rules to get the correct port and path
        ```
    *   使用此配置文件启动你的 Prometheus 服务器实例。

5.  **查询与可视化 (Query and Visualize)**:
    *   打开 Prometheus UI (通常是 `http://localhost:9090`)。
    *   在 "Expression" 输入框中，你可以查询指标，例如 `jvm_memory_used_bytes{application="leon-provider"}` 或 `http_server_requests_seconds_count`。
    *   (可选) 配置 Grafana 连接到 Prometheus 数据源，并创建仪表盘来可视化这些指标。常用的 Grafana 仪表盘 ID 如 `4701` (Spring Boot 2.1 Statistics) 或 `12900` (Spring Boot Actuator Metrics by Pivotal) 可以作为起点。

---
此文档提供了对 `Prometheus` 模块如何集成 Spring Boot/Cloud 应用与 Prometheus 监控的基本理解。实际部署时，请根据具体环境调整端口、服务发现机制和安全配置。
