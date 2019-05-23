package cn.leon.leonconsumer.controller;

import cn.leon.leonconsumer.client.UserFeignClient;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * @author Leon
 * @Description: TODO
 * @date 2019/5/2216:00
 */
@RestController
@RefreshScope
public class UserController {
    @Value("${name:}")
    private String name;

    @Value("${age:}")
    private String age;


    @RequestMapping("/config")
    public String config() throws NacosException {
        return name + " | " + age;
    }

    @Autowired
    private UserFeignClient userFeignClient;
    /**
     * 用户账号名和密码登录
     *
     * @param userName
     * @param passWord
     * @return
     */
    @RequestMapping("/login")
    public String login(@RequestParam("userName") String userName, @RequestParam("passWord") String passWord) {
        return userFeignClient.login(userName, passWord);
    }


}
