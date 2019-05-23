package cn.leon.leonconsumer.controller;

import cn.leon.leonconsumer.client.UserFeignClient;
import cn.leon.leonconsumer.config.User;
import cn.leon.leonconsumer.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Leon
 * @Description: TODO
 * @date 2019/5/2216:00
 */
@RestController
public class UserController {

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private UserMapper userMapper;

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

    @RequestMapping("/getUser")
    public String getUser() throws Exception {
       User user =  userMapper.getDmUserById(1);
       return user.toString();
    }

}
