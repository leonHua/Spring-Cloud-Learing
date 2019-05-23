package cn.leon.leonconsumer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Leon
 * @Description: TODO
 * @date 2019/5/2310:20
 */
@ConfigurationProperties()
public class ConfigBean {
    private String dbName;
    private String dbPassword;
    private User user;

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public void setDbPassword(String dbPassword) {
        this.dbPassword = dbPassword;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
