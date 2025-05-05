import com.aurorain.shardkv.Client;
import com.aurorain.shardkv.KVRaftConfig;
import com.aurorain.shardkv.ShardKVRaftApplication;
import com.aurorain.shardkv.common.CommandType;
import com.aurorain.shardkv.constant.CFConstants;
import com.aurorain.shardkv.model.dto.CommitArgs;
import com.aurorain.shardkv.model.dto.GetArgs;
import com.aurorain.shardkv.model.dto.PreWriteArgs;
import com.aurorain.shardkv.service.KVServerService;
import com.aurorain.shardkv.store.KV;
import com.aurorain.shardkv.store.RocksDBEntry;
import com.aurorain.shardkv.store.RocksDBKV;
import com.aurorain.shardkv.store.RocksDBReader;
import com.aurorain.shardkv.transaction.Lock;
import com.aurorain.shardkv.transaction.TransactionExecutorManager;
import com.aurorain.shardkv.tso.TimestampOracle;
import com.aurorain.shardmaster.ShardClient;
import com.aurorain.shardmaster.ShardMasterApplication;
import com.aurorain.shardmaster.config.ShardServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.rocksdb.RocksIterator;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TransactionTest {

    @Test
    @Order(1)
    void testTxn() throws InterruptedException {
        ShardMasterApplication shardMasterApplication = new ShardMasterApplication();
        ShardServerConfig shardServerConfig = shardMasterApplication.init(3, -1);
        ShardClient shardClient = shardMasterApplication.makeClient();

        Thread.sleep(1000);

        Client client = new Client(shardClient);

        ShardKVRaftApplication application1 = new ShardKVRaftApplication();
        KVRaftConfig config1 = application1.init(new int[]{1, 2, 3}, 1,-1, shardClient,client);

        Thread.sleep(1000);

        HashMap<Integer, List<Integer>> joinMap = new HashMap<>();
        joinMap.put(1, new ArrayList<Integer>(Arrays.asList(1, 2, 3)));
        shardClient.join(joinMap);

        PreWriteArgs preWriteArgs = new PreWriteArgs();
        List<RocksDBEntry> entries = new ArrayList<>();
        entries.add(new RocksDBEntry("abc", "123", CFConstants.CfDefault, CommandType.PUT));
        entries.add(new RocksDBEntry("dhay", "34", CFConstants.CfDefault, CommandType.PUT));
        preWriteArgs.setEntries(entries);
        preWriteArgs.setStartTs(100);
        preWriteArgs.setLockTtl(10000);
        preWriteArgs.setPrimary("abc");
        client.kvPreWrite("key", preWriteArgs);

        PreWriteArgs preWriteArgs1 = new PreWriteArgs();
        List<RocksDBEntry> entries1 = new ArrayList<>();
        entries1.add(new RocksDBEntry("abc", "544", CFConstants.CfDefault, CommandType.PUT));
        entries1.add(new RocksDBEntry("dbeyh", "43", CFConstants.CfDefault, CommandType.PUT));
        preWriteArgs1.setEntries(entries1);
        preWriteArgs1.setStartTs(105);
        preWriteArgs1.setLockTtl(10000);
        preWriteArgs1.setPrimary("abc");
        client.kvPreWrite("key1", preWriteArgs1);

        CommitArgs commitArgs = new CommitArgs();
        List<String> keys = new ArrayList<>();
        keys.add("abc");
        keys.add("dhay");
        commitArgs.setKeys(keys);
        commitArgs.setStartTs(100);
        commitArgs.setCommitTs(110);
        client.kvCommit("key", commitArgs);

        PreWriteArgs preWriteArgs2 = new PreWriteArgs();
        List<RocksDBEntry> entries2 = new ArrayList<>();
        entries2.add(new RocksDBEntry("abcd", "599", CFConstants.CfDefault, CommandType.PUT));
        entries2.add(new RocksDBEntry("dbeyh", "443", CFConstants.CfDefault, CommandType.PUT));
        preWriteArgs2.setEntries(entries2);
        preWriteArgs2.setStartTs(115);
        preWriteArgs2.setLockTtl(10000);
        preWriteArgs2.setPrimary("abcd");
        client.kvPreWrite("key2", preWriteArgs2);

        PreWriteArgs preWriteArgs3 = new PreWriteArgs();
        List<RocksDBEntry> entries3 = new ArrayList<>();
        entries3.add(new RocksDBEntry("dhay", "666", CFConstants.CfDefault, CommandType.PUT));
        entries3.add(new RocksDBEntry("fut", "8776", CFConstants.CfDefault, CommandType.PUT));
        preWriteArgs3.setEntries(entries3);
        preWriteArgs3.setStartTs(108);
        preWriteArgs3.setLockTtl(10000);
        preWriteArgs3.setPrimary("dhay");
        client.kvPreWrite("key3", preWriteArgs3);

        RocksDBKV store = (RocksDBKV) config1.getShardKvServers().get(1).getState().getStore();

        store.iterateColumnFamily(CFConstants.CfDefault);
        store.iterateColumnFamily(CFConstants.CfLock);
        store.iterateColumnFamily(CFConstants.CfWrite);

        GetArgs getArgs = new GetArgs();
        getArgs.setTs(112);
        String val = (String)client.kvGet("abc", getArgs).getValue();
        log.info("get abc ts=112 : {}", val);

        GetArgs getArgs1 = new GetArgs();
        getArgs1.setTs(107);
        String val1 = (String)client.kvGet("abc", getArgs1).getValue();
        log.info("get abc ts=107 : {}", val1);

        GetArgs getArgs2 = new GetArgs();
        getArgs2.setTs(117);
        String val2 = (String)client.kvGet("abcd", getArgs2).getValue();
        log.info("get abcd ts=117 : {}", val2);

//        Thread.sleep(3000);
        application1.cleanup();
        shardMasterApplication.cleanup();

    }

    /**
     * 使用TwoPhaseCommitter测试
     * @throws InterruptedException
     */
    @Test
    @Order(2)
    void testTxn2() throws Exception {
        ShardMasterApplication shardMasterApplication = new ShardMasterApplication();
        ShardServerConfig shardServerConfig = shardMasterApplication.init(3, -1);
        ShardClient shardClient = shardMasterApplication.makeClient();

        HashMap<Integer, List<Integer>> joinMap = new HashMap<>();
        joinMap.put(1, new ArrayList<Integer>(Arrays.asList(1, 2, 3)));
        joinMap.put(2, new ArrayList<Integer>(Arrays.asList(4, 5, 6)));
        shardClient.join(joinMap);

        Thread.sleep(1000);

        Client client = new Client(shardClient);

        ShardKVRaftApplication application1 = new ShardKVRaftApplication();
        KVRaftConfig config1 = application1.init(new int[]{1, 2, 3}, 1,-1, shardClient,client);

        Thread.sleep(1000);

        ShardKVRaftApplication application2 = new ShardKVRaftApplication();
        KVRaftConfig config2 = application2.init(new int[]{4, 5, 6}, 2,-1, shardClient,client);

        Thread.sleep(1000);

        TimestampOracle timestampOracle = new TimestampOracle();

//        long startTs1 = timestampOracle.getTimestamp();
//        long commitTs1 = timestampOracle.getTimestamp();
//        long startTs2 = timestampOracle.getTimestamp();
//        long startTs3 = timestampOracle.getTimestamp();
//        long commitTs3 = timestampOracle.getTimestamp();
//        long commitTs2 = timestampOracle.getTimestamp();

        Map<Integer, KVServerService> kvServerServices1 = config1.getKvServerServices();
        Map<Integer, KVServerService> kvServerServices2 = config2.getKvServerServices();
        Map<Integer, Map<Integer, KVServerService>> services = new HashMap<>();
        services.put(config1.getGid(), kvServerServices1);
        services.put(config2.getGid(), kvServerServices2);
        Client client1 = newClient(shardClient, services);
        Client client2 = newClient(shardClient, services);
        Client client3 = newClient(shardClient, services);

        TransactionExecutorManager transactionExecutorManager1 = new TransactionExecutorManager(client1, timestampOracle, 1);
        TransactionExecutorManager transactionExecutorManager2 = new TransactionExecutorManager(client2, timestampOracle, 2);
        TransactionExecutorManager transactionExecutorManager3 = new TransactionExecutorManager(client3, timestampOracle, 3);

        ExecutorService newFixedThreadPool = Executors.newFixedThreadPool(3);
        ArrayList<RocksDBEntry> entries1 = new ArrayList<>();
        entries1.add(new RocksDBEntry("abc", "544", CFConstants.CfDefault, CommandType.PUT));
        entries1.add(new RocksDBEntry("def", "43", CFConstants.CfDefault, CommandType.PUT));

        ArrayList<RocksDBEntry> entries2 = new ArrayList<>();
        entries2.add(new RocksDBEntry("ffg", "6534", CFConstants.CfDefault, CommandType.PUT));
        entries2.add(new RocksDBEntry("abc", "45", CFConstants.CfDefault, CommandType.PUT));
        entries2.add(new RocksDBEntry("gg", "15", CFConstants.CfDefault, CommandType.PUT));

        ArrayList<RocksDBEntry> entries3 = new ArrayList<>();
        entries3.add(new RocksDBEntry("uu", "666", CFConstants.CfDefault, CommandType.PUT));
        entries3.add(new RocksDBEntry("def", "999", CFConstants.CfDefault, CommandType.PUT));
        entries3.add(new RocksDBEntry("ww", "110", CFConstants.CfDefault, CommandType.PUT));

//        CountDownLatch latch = new CountDownLatch(3);
        newFixedThreadPool.submit(() -> {
            try {
                transactionExecutorManager1.crash("abc", entries1);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
//                latch.countDown();
            }
        });

        Thread.sleep(50);
        newFixedThreadPool.submit(() -> {
            try {
                transactionExecutorManager2.execute2PC("ffg", entries2);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
//                latch.countDown();
            }
        });

        Thread.sleep(50);
        newFixedThreadPool.submit(() -> {
            try {
                transactionExecutorManager3.execute2PC("uu", entries3);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
//                latch.countDown();
            }
        });

//        latch.await();
        Thread.sleep(10000);
        application1.cleanup();
        shardMasterApplication.cleanup();


    }

    /**
     * 使用TwoPhaseCommitter测试
     * @throws InterruptedException
     */
    @Test
    @Order(3)
    void testTxn3() throws Exception {
        ShardMasterApplication shardMasterApplication = new ShardMasterApplication();
        ShardServerConfig shardServerConfig = shardMasterApplication.init(3, -1);
        ShardClient shardClient = shardMasterApplication.makeClient();

        Thread.sleep(1000);

        Client client = new Client(shardClient);

        ShardKVRaftApplication application1 = new ShardKVRaftApplication();
        KVRaftConfig config1 = application1.init(new int[]{1, 2, 3}, 1,-1, shardClient,client);

        Thread.sleep(1000);

        HashMap<Integer, List<Integer>> joinMap = new HashMap<>();
        joinMap.put(1, new ArrayList<Integer>(Arrays.asList(1, 2, 3)));
        shardClient.join(joinMap);

        TimestampOracle timestampOracle = new TimestampOracle();

        Map<Integer, KVServerService> kvServerServices = config1.getKvServerServices();
        Map<Integer, Map<Integer, KVServerService>> services = new HashMap<>();
        services.put(config1.getGid(), kvServerServices);
        Client client1 = newClient(shardClient, services);
        Client client2 = newClient(shardClient, services);
        Client client3 = newClient(shardClient, services);

        TransactionExecutorManager transactionExecutorManager1 = new TransactionExecutorManager(client1, timestampOracle, 1);
        TransactionExecutorManager transactionExecutorManager2 = new TransactionExecutorManager(client2, timestampOracle, 2);
        TransactionExecutorManager transactionExecutorManager3 = new TransactionExecutorManager(client3, timestampOracle, 3);

        ExecutorService newFixedThreadPool = Executors.newFixedThreadPool(3);
        ArrayList<RocksDBEntry> entries1 = new ArrayList<>();
        entries1.add(new RocksDBEntry("abc", "544", CFConstants.CfDefault, CommandType.PUT));
        entries1.add(new RocksDBEntry("dbeyh", "43", CFConstants.CfDefault, CommandType.PUT));

        ArrayList<RocksDBEntry> entries2 = new ArrayList<>();
        entries2.add(new RocksDBEntry("ffg", "6534", CFConstants.CfDefault, CommandType.PUT));
        entries2.add(new RocksDBEntry("ppv", "45", CFConstants.CfDefault, CommandType.PUT));
        entries2.add(new RocksDBEntry("gg", "15", CFConstants.CfDefault, CommandType.PUT));

        ArrayList<RocksDBEntry> entries3 = new ArrayList<>();
        entries3.add(new RocksDBEntry("abc", "666", CFConstants.CfDefault, CommandType.PUT));
        entries3.add(new RocksDBEntry("ffg", "999", CFConstants.CfDefault, CommandType.PUT));
        entries3.add(new RocksDBEntry("gg", "110", CFConstants.CfDefault, CommandType.PUT));

//        CountDownLatch latch = new CountDownLatch(3);
        newFixedThreadPool.submit(() -> {
            try {
                transactionExecutorManager3.crash("gg", entries3);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
            }
        });
        Thread.sleep(50);

        newFixedThreadPool.submit(() -> {
            try {
                transactionExecutorManager1.execute2PC("abc", entries1);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
            }
        });

        newFixedThreadPool.submit(() -> {
            try {
                transactionExecutorManager2.execute2PC("ffg", entries2);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
            }
        });

//        latch.await();
        Thread.sleep(10000);

        RocksDBKV store = (RocksDBKV) config1.getShardKvServers().get(1).getState().getStore();

        store.iterateColumnFamily(CFConstants.CfDefault);
        store.iterateColumnFamily(CFConstants.CfLock);
        store.iterateColumnFamily(CFConstants.CfWrite);

//        RocksDBReader rocksDBReader = store.openReader();
//        RocksIterator iterator = rocksDBReader.iterCf(CFConstants.CfLock);
//        for(iterator.seekToFirst();iterator.isValid();iterator.next()) {
//            String value = new String(iterator.value());
//            Lock lock = Lock.parse(value);
////            log.info("5555 lock: {}", lock);
//
//        }
        application1.cleanup();
        shardMasterApplication.cleanup();

    }

    public Client newClient(ShardClient shardClient, Map<Integer, Map<Integer, KVServerService>> services) {
        Client client1 = new Client(shardClient);
        synchronized (this) {
            for(Integer gid : services.keySet()) {
                client1.addGroup(gid, services.get(gid));
            }
        }
        return client1;
    }

}
