package cn.leon.leonconsumer.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * @author Leon
 * @Description: TODO
 * @date 2019/5/2710:17
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @GetMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password) {
        if ("leon".equals(username) && "888".equals(password)) {
            return "登录成功";
        }
        return "登录失败";
    }

    @GetMapping("/info")
    public String info(HttpServletRequest request) {
        String header = request.getHeader("X-Request-Foo");
        return "获取信息成功:" + header;
    }
}
