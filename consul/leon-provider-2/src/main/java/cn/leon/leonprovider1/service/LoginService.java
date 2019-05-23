package cn.leon.leonprovider1.service;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Leon
 * @Description: TODO
 * @date 2019/5/2215:43
 */
@RestController
public class LoginService {

    @RequestMapping("/login")
    public String login(@RequestParam("userName") String userName, @RequestParam("passWord") String passWord) {
        if (userName.equals("leon") && passWord.equals("888")) {
            return "leon-provider-2: login success";
        }
        return "leon-provider-2: login fail";
    }
}
