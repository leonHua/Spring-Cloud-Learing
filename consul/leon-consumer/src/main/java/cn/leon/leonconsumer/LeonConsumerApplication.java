package cn.leon.leonconsumer;

import cn.leon.leonconsumer.config.ConfigBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.netflix.feign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
@EnableConfigurationProperties(ConfigBean.class)
public class LeonConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeonConsumerApplication.class, args);
    }
}
