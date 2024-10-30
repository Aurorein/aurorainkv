package storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@PropertySource(value = "classpath:conf/rocksdb.properties", encoding = "UTF-8")
public class RocksDBProperties {

    @Value("${rocksdb.dbpath}")
    public String dbPath;

    @Value("${rocksdb.dataCreateIfMissing}")
    public boolean dataCreateIfMissing;

    @Value("${rocksdb.raftCreateIfMissing}")
    public boolean raftCreateIfMissing;
}
