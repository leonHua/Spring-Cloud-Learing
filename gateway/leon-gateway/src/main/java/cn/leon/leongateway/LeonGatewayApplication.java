package cn.leon.leongateway;

import cn.leon.leongateway.filter.GlobalFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableEurekaClient
public class LeonGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeonGatewayApplication.class, args);
    }

    @Bean
    public GlobalFilter globalFilter() {
        return new GlobalFilter();
    }

}
