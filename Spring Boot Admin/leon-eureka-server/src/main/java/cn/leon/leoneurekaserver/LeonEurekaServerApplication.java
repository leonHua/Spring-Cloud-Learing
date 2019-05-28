package cn.leon.leoneurekaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class LeonEurekaServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(LeonEurekaServerApplication.class, args);
	}
}
