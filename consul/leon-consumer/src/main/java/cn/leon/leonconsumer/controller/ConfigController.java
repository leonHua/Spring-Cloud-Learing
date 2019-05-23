package cn.leon.leonconsumer.controller;

import cn.leon.leonconsumer.config.ConfigBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Leon
 * @Description: TODO
 * @date 2019/5/239:08
 */
@RestController
@RefreshScope
public class ConfigController {

    @Value("${dbName}")
    private String dbName;

    @Value("${dbPassword}")
    private String dbPassword;

    @Value("${user.name}")
    private String name;

    @Value("${user.age}")
    private int age;

    @Value("${user.desc}")
    private String desc;

    @Autowired
    private ConfigBean configBean;

    @GetMapping("/getuserinfo")
    public String getUserInfo() {
        return name + " | " + age + " | " + desc;
    }

    @GetMapping("/getdbinfo")
    public String getDBInfo() {
        return "数据库用户名：" + dbName + " | 数据库密码: " + dbPassword;
    }

    @GetMapping("/getallinfo")
    public String getAllInfo() {
        StringBuilder allInfo = new StringBuilder();
        allInfo.append(configBean.getDbName() + " | ");
        allInfo.append(configBean.getDbPassword() + " | ");
        allInfo.append(configBean.getUser().getName() + " | ");
        allInfo.append(configBean.getUser().getAge() + " | ");
        allInfo.append(configBean.getUser().getDesc() + " | ");
        return allInfo.toString();
    }
}
