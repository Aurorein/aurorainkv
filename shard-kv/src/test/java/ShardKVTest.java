import com.aurorain.shardkv.Client;
import com.aurorain.shardkv.ShardKVRaftApplication;
import com.aurorain.shardmaster.ShardClient;
import com.aurorain.shardmaster.ShardConfig;
import com.aurorain.shardmaster.ShardMasterApplication;
import com.aurorain.shardkv.KVRaftConfig;
import com.aurorain.myrpc.config.RpcConfig;
import com.aurorain.shardmaster.config.ShardServerConfig;
import com.aurorain.myrpc.registry.Registry;
import com.aurorain.myrpc.registry.RegistryFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.*;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ShardKVTest {

    private static final Random random = new Random();

//    @Test
//    @Order(1)
//    void simpleTest() {
//        ShardKVRaftApplication application = new ShardKVRaftApplication();
//        KVRaftConfig config = application.init(3, -1, null);
//    }

//    @Test
//    @Order(3)
//    void simpleTest2() {
//        ShardMasterApplication shardMasterApplication = new ShardMasterApplication();
//        ShardServerConfig shardServerConfig = shardMasterApplication.init(3, -1);
//    }

    @Test
    @Order(1)
    void raftTest() throws InterruptedException {
        ShardMasterApplication shardMasterApplication = new ShardMasterApplication();
        ShardServerConfig shardServerConfig = shardMasterApplication.init(3, -1);

        Thread.sleep(1000);

        shardMasterApplication.cleanup();
    }

    @Test
    @Order(2)
    void shardMasterTest() throws InterruptedException {
        ShardMasterApplication shardMasterApplication = new ShardMasterApplication();
        ShardServerConfig shardServerConfig = shardMasterApplication.init(3, -1);
        ShardClient shardClient = shardMasterApplication.makeClient();

        // 初始化进行查询
        ShardConfig config0 = shardClient.query(-1);
        log.info("shardMasterTest: shardConfig0: {} ", config0);

        Thread.sleep(5000);
        // join gid 1, gid 2, gid 3
        HashMap<Integer, List<Integer>> joinMap = new HashMap<>();
        joinMap.put(1, new ArrayList<Integer>(Arrays.asList(1, 2, 3)));
        shardClient.join(joinMap);
//        Thread.sleep(5000);
        joinMap.clear();
        joinMap.put(2, new ArrayList<Integer>(Arrays.asList(4, 5, 6)));
        shardClient.join(joinMap);
//        Thread.sleep(5000);
        joinMap.clear();
        joinMap.put(3, new ArrayList<Integer>(Arrays.asList(7, 8, 9, 10)));
        shardClient.join(joinMap);

//        Thread.sleep(1000);
        ShardConfig config1 = shardClient.query(-1);
        log.info("shardMasterTest: shardConfig1: {} ", config1);

        // move shard 7 -> gid 1
        shardClient.move(0, 2);
//        Thread.sleep(1000);
        ShardConfig config2 = shardClient.query(-1);
        log.info("shardMasterTest: shardConfig2: {} ", config2);

        shardMasterApplication.cleanup();
    }

    @Test
    @Order(3)
    void genericTest() throws InterruptedException {
        ShardMasterApplication shardMasterApplication = new ShardMasterApplication();
        ShardServerConfig shardServerConfig = shardMasterApplication.init(3, -1);
        ShardClient shardClient = shardMasterApplication.makeClient();

        Thread.sleep(1000);

        Client client = new Client(shardClient);

        ShardKVRaftApplication application1 = new ShardKVRaftApplication();
        KVRaftConfig config1 = application1.init(new int[]{1, 2, 3}, 1,-1, shardClient,client);

        Thread.sleep(1000);

        ShardKVRaftApplication application2 = new ShardKVRaftApplication();
        KVRaftConfig config2 = application2.init(new int[]{4, 5, 6}, 2,-1, shardClient,client);

        HashMap<Integer, List<Integer>> joinMap = new HashMap<>();
        joinMap.put(1, new ArrayList<Integer>(Arrays.asList(1, 2, 3)));
        joinMap.put(2, new ArrayList<Integer>(Arrays.asList(4, 5, 6)));
        shardClient.join(joinMap);

        RpcConfig rpcConfig = RpcConfig.getRpcConfig();
        Registry registry = RegistryFactory.getInstance(rpcConfig.getRegistryConfig().getRegistry());

        Thread.sleep(1000);

        client.put("abc", "abc");
        client.put("eabc", "eabc");
        client.put("fabc", "fabc");
        client.put("gabc", "ggbef");

        String abc = client.get("abc");
        String gabc = client.get("gabc");

        ShardConfig shardConfig1 = shardClient.query(-1);
        log.info("genericTest: shardConfig1: {} ", shardConfig1);

        log.info("genericTest:  abc的结果是 {} , 属于的分片是 {}", abc, client.key2shard(abc));
        log.info("genericTest:  gabc的结果是 {} , 属于的分片是 {}", gabc, client.key2shard(gabc));

        Iterator<Map.Entry<String, String>> iterator1 = config1.getShardKvServers().get(1).getState().getStore().iterator();
        while(iterator1.hasNext()) {
            Map.Entry<String, String> entry = iterator1.next();
            log.info("genericTest: gid:{} raft集群中的所有键值对有:{} ", config1.getGid(), entry);
        }

        Iterator<Map.Entry<String, String>> iterator2 = config2.getShardKvServers().get(4).getState().getStore().iterator();
        while(iterator2.hasNext()) {
            Map.Entry<String, String> entry = iterator2.next();
            log.info("genericTest: gid:{} raft集群中的所有键值对有:{} ", config2.getGid(), entry);
        }

        shardClient.move(0, 2);
        Thread.sleep(2000);

        ShardConfig shardConfig2 = shardClient.query(-1);
        log.info("genericTest: shardConfig2: {} ", shardConfig2);

        shardClient.move(1, 2);
//        Thread.sleep(1000);
        ShardConfig config3 = shardClient.query(-1);
        log.info("shardMasterTest: shardConfig3: {} ", config3);

        shardClient.move(3, 2);
//        Thread.sleep(1000);
        ShardConfig config4 = shardClient.query(-1);
        log.info("shardMasterTest: shardConfig4: {} ", config4);

        Iterator<Map.Entry<String, String>> iterator11 = config1.getShardKvServers().get(1).getState().getStore().iterator();
        while(iterator11.hasNext()) {
            Map.Entry<String, String> entry = iterator11.next();
            log.info("genericTest: gid:{} raft集群中的所有键值对有:{} ", config1.getGid(), entry);
        }

        Iterator<Map.Entry<String, String>> iterator22 = config2.getShardKvServers().get(4).getState().getStore().iterator();
        while(iterator22.hasNext()) {
            Map.Entry<String, String> entry = iterator22.next();
            log.info("genericTest: gid:{} raft集群中的所有键值对有:{} ", config2.getGid(), entry);
        }

        application1.cleanup();
        application2.cleanup();
        shardMasterApplication.cleanup();

    }

    @Test
    @Order(4)
    void genericTest2() throws InterruptedException {
        ShardMasterApplication shardMasterApplication = new ShardMasterApplication();
        ShardServerConfig shardServerConfig = shardMasterApplication.init(3, -1);
        ShardClient shardClient = shardMasterApplication.makeClient();

        HashMap<Integer, List<Integer>> joinMap = new HashMap<>();
        joinMap.put(1, new ArrayList<Integer>(Arrays.asList(1, 2, 3)));

        shardClient.join(joinMap);

        joinMap = new HashMap<>();
        joinMap.put(2, new ArrayList<Integer>(Arrays.asList(4, 5, 6)));
        shardClient.join(joinMap);

        shardClient.move(1, 1);
        ShardConfig query = shardClient.query(-1);

        log.info("{}",query);



        shardMasterApplication.cleanup();
    }
}
