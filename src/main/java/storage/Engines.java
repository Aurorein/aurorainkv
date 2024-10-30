package storage;

import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;

@Component
public class Engines {
    private RocksDB dataDB;

    private RocksDB raftDB;

    private String dataPath;

    private String raftPath;

    @Autowired
    private RocksDBProperties rocksDBProperties;

    public Engines() {
        this.dataPath = "data";
        this.raftPath = "raft";
    }

    public Engines(String dataPath, String raftPath) {
        this.dataPath = dataPath;
        this.raftPath = raftPath;
    }


    public RocksDB createDataDB() {
        Options options = new Options();
        options.setCreateIfMissing(rocksDBProperties.dataCreateIfMissing);
        String dPath = rocksDBProperties.dbPath + File.separator + dataPath;
        System.out.println(dPath);
        try {
            dataDB = RocksDB.open(options, dPath);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
        return dataDB;
    }


    public RocksDB createRaftDB() {
        Options options = new Options();
        options.setCreateIfMissing(rocksDBProperties.raftCreateIfMissing);
        String rPath = rocksDBProperties.dbPath + File.separator + raftPath;
        try {
            raftDB = RocksDB.open(options, rPath);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
        return raftDB;
    }


}
