package kv;

import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import storage.Engines;
import storage.RocksDBProperties;

import java.util.Map;

public class KVServer {
    public static void main(String[] args) {
        ApplicationContext context = new AnnotationConfigApplicationContext(SpringAppConfig.class);

        Engines bean = context.getBean(Engines.class);
        RocksDB rdb = bean.createDataDB();
        try {
            rdb.put("hello".getBytes(), "11".getBytes());
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }

    }
}
