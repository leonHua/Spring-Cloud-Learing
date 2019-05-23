package cn.leon.leonconsumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.feign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class LeonConsumerApplication {

	public static void main(String[] args) {
		SpringApplication.run(LeonConsumerApplication.class, args);
	}

}
