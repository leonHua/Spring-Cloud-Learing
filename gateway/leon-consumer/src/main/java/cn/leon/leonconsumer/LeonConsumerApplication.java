package cn.leon.leonconsumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

@SpringBootApplication
@EnableEurekaClient
public class LeonConsumerApplication {

	public static void main(String[] args) {
		SpringApplication.run(LeonConsumerApplication.class, args);
	}

}
