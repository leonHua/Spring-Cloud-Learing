package cn.leon.leoneureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class LeonEurekaApplication {

	public static void main(String[] args) {
		SpringApplication.run(LeonEurekaApplication.class, args);
	}

}
