package com.aurorain.shardmaster;

import com.aurorain.shardmaster.config.WebSocketConfig;
import com.aurorain.shardmaster.web.ShardMasterController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.bind.annotation.CrossOrigin;

@CrossOrigin("*")
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
@ComponentScan("com.aurorain.shardmaster.web")
@ComponentScan("com.aurorain.raft.web")
@ComponentScan("com.aurorain.shardmaster")
public class ShardMatserSpringApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext applicationContext = SpringApplication.run(ShardMatserSpringApplication.class, args);
        ShardMasterController bean = applicationContext.getBean(ShardMasterController.class);
        System.out.println(bean);
        WebSocketConfig bean1 = applicationContext.getBean(WebSocketConfig.class);
        System.out.println(bean1);
    }
}
