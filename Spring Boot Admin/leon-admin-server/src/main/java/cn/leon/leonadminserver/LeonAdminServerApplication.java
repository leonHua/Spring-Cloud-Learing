package cn.leon.leonadminserver;

import de.codecentric.boot.admin.server.config.EnableAdminServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@SpringBootApplication
@Configuration
@EnableAutoConfiguration
@EnableAdminServer
@EnableDiscoveryClient
public class LeonAdminServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(LeonAdminServerApplication.class, args);
	}

//	@Configuration
//	public static class SecurityPermitAllConfig extends WebSecurityConfigurerAdapter {
//		@Override
//		protected void configure(HttpSecurity http) throws Exception {
//			http.authorizeRequests().anyRequest().permitAll()
//					.and().csrf().disable();
//		}
//	}

}
