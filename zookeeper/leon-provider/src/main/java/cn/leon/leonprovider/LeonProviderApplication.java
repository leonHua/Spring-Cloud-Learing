package cn.leon.leonprovider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class LeonProviderApplication {

	public static void main(String[] args) {
		SpringApplication.run(LeonProviderApplication.class, args);
	}

}
