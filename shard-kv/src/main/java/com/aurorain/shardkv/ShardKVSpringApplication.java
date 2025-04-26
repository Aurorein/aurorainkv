package com.aurorain.shardkv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.bind.annotation.CrossOrigin;

@CrossOrigin("*")
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
@ComponentScan("com.aurorain.raft.web")
@ComponentScan("com.aurorain.shardkv.web")
public class ShardKVSpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShardKVSpringApplication.class, args);
    }
}
