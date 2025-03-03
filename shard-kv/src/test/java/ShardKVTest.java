import com.aurorain.*;
import com.aurorain.config.KVRaftConfig;
import com.aurorain.config.RpcConfig;
import com.aurorain.config.ShardServerConfig;
import com.aurorain.registry.Registry;
import com.aurorain.registry.RegistryFactory;
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
        ShardConfig shardConfig1 = shardClient.query(-1);
        log.info("genericTest: shardConfig1: {} ", shardConfig1);

        client.put("abc", "abc");
        client.put("eabc", "eabc");
        client.put("fabc", "fabc");
        client.put("gabc", "ggbef");

        String abc = client.get("abc");
        String gabc = client.get("gabc");

        log.info("genericTest:  abc的结果是 {} , 属于的分片是 {}", abc, client.key2shard(abc));
        log.info("genericTest:  gabc的结果是 {} , 属于的分片是 {}", gabc, client.key2shard(gabc));

        Iterator<Map.Entry<String, String>> iterator1 = config1.getShardKvServers().get(1).getState().getStore().iterator();
        while(iterator1.hasNext()) {
            Map.Entry<String, String> entry = iterator1.next();
            log.info("genericTest: shardConfig1下面的iterator1 entry is {} ", entry);
        }

        Iterator<Map.Entry<String, String>> iterator2 = config2.getShardKvServers().get(4).getState().getStore().iterator();
        while(iterator2.hasNext()) {
            Map.Entry<String, String> entry = iterator2.next();
            log.info("genericTest: shardConfig1下面的iterator2 entry is {} ", entry);
        }

        shardClient.move(0, 2);
        Thread.sleep(2000);

        ShardConfig shardConfig2 = shardClient.query(-1);
        log.info("genericTest: shardConfig2: {} ", shardConfig2);

        Iterator<Map.Entry<String, String>> iterator11 = config1.getShardKvServers().get(1).getState().getStore().iterator();
        while(iterator11.hasNext()) {
            Map.Entry<String, String> entry = iterator11.next();
            log.info("genericTest: shardConfig2下面的iterator1 entry is {} ", entry);
        }

        Iterator<Map.Entry<String, String>> iterator22 = config2.getShardKvServers().get(4).getState().getStore().iterator();
        while(iterator22.hasNext()) {
            Map.Entry<String, String> entry = iterator22.next();
            log.info("genericTest: shardConfig2下面的iterator2 entry is {} ", entry);
        }

        application1.cleanup();
        application2.cleanup();
        shardMasterApplication.cleanup();

    }
}
