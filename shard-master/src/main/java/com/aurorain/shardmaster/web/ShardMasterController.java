package com.aurorain.shardmaster.web;

import com.aurorain.shardmaster.ShardClient;
import com.aurorain.shardmaster.ShardConfig;
import com.aurorain.shardmaster.ShardMasterApplication;
import com.aurorain.shardmaster.config.ShardServerConfig;
import com.aurorain.commonmodule.model.ResultData;

import com.aurorain.shardmaster.web.dto.JoinReq;
import com.aurorain.shardmaster.web.dto.LeaveReq;
import com.aurorain.shardmaster.web.dto.MoveReq;
import com.aurorain.shardmaster.web.dto.TestDemo;
import com.aurorain.shardmaster.web.util.ShardMasterConfigUtil;
import com.aurorain.shardmaster.web.util.ShardMasterWebUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/shardmaster")
@CrossOrigin("*")
@Slf4j
public class ShardMasterController {

    @Autowired
    ShardMasterConfigUtil shardMasterConfigUtil;

    @Autowired
    ShardMasterWebUtil shardMasterWebUtil;

    @Autowired
    private CommandExecutor commandExecutor;

    @GetMapping("/addcluster")
    public ResultData<ShardServerConfig> addCluster(@RequestParam int n) {
        try {
            return commandExecutor.execute(() -> {
                ShardMasterApplication application = new ShardMasterApplication();
                ShardServerConfig config = application.init(n, -1);
                ShardClient client = application.makeClient();

                shardMasterConfigUtil.setConfig(config);
                shardMasterConfigUtil.setApplication(application);
                shardMasterWebUtil.setShardClient(client);
                return ResultData.success(config);
            });
        } catch (Exception e) {
            log.error("Add cluster failed", e);
            return ResultData.fail(201, "Failed to add cluster: " + e.getMessage());
        }
    }

    @GetMapping("/getnodes")
    public ResultData<ShardServerConfig> getNodes() {
        try {
            return commandExecutor.execute(() ->
                    ResultData.success(shardMasterConfigUtil.getConfig())
            );
        } catch (Exception e) {
            return ResultData.fail(203, "Failed to get nodes");
        }
    }

    @GetMapping("/shutdown")
    public ResultData<Object> shutdown() {
        try {
            return commandExecutor.execute(() -> {
                ShardMasterApplication application = shardMasterConfigUtil.getApplication();
                if (application != null) {
                    application.cleanup();
                }
                shardMasterConfigUtil.setConfig(null);
                shardMasterConfigUtil.setApplication(null);
                shardMasterWebUtil.setShardClient(null);
                return ResultData.success(null);
            });
        } catch (Exception e) {
            return ResultData.fail(204, "Failed to shutdown");
        }
    }

    @GetMapping("/query")
    public ResultData<ShardConfig> query(@RequestParam int n) {
        try {
            return commandExecutor.execute(() -> {
                ShardClient shardClient = shardMasterWebUtil.getShardClient();
                ShardConfig shardConfig = shardClient.query(n);
                return ResultData.success(shardConfig);
            });
        } catch (Exception e) {
            log.error("Query failed", e);
            return ResultData.fail(201, e.getMessage());
        }
    }

    @PostMapping("/join")
    public ResultData<ShardConfig> join(@RequestBody JoinReq join) {
        try {
            return commandExecutor.execute(() -> {
                ShardClient shardClient = shardMasterWebUtil.getShardClient();
                shardClient.join(join.getServers());
                ShardConfig shardConfig = shardClient.query(-1);
                return ResultData.success(shardConfig);
            });
        } catch (Exception e) {
            log.error("Join failed", e);
            return ResultData.fail(201, e.getMessage());
        }
    }

    @PostMapping("/leave")
    public ResultData<ShardConfig> leave(@RequestBody LeaveReq leave) {
        try {
            return commandExecutor.execute(() -> {
                ShardClient shardClient = shardMasterWebUtil.getShardClient();
                shardClient.leave(leave.getGids());
                ShardConfig shardConfig = shardClient.query(-1);
                return ResultData.success(shardConfig);
            });
        } catch (Exception e) {
            log.error("Leave failed", e);
            return ResultData.fail(201, e.getMessage());
        }
    }

    @PostMapping("/move")
    public ResultData<ShardConfig> move(@RequestBody MoveReq move) {
        try {
            return commandExecutor.execute(() -> {
                ShardClient shardClient = shardMasterWebUtil.getShardClient();
                shardClient.move(move.getShard(), move.getGid());
                ShardConfig shardConfig = shardClient.query(-1);
                return ResultData.success(shardConfig);
            });
        } catch (Exception e) {
            log.error("Move failed", e);
            return ResultData.fail(201, e.getMessage());
        }
    }

    @GetMapping("/disconnect")
    public ResultData<Object> disconnect(@RequestParam int id) {
        try {
            return commandExecutor.execute(() -> {
                ShardMasterApplication application = shardMasterConfigUtil.getApplication();
                int n = shardMasterConfigUtil.getConfig().getCount();
                int[] from = new int[n];
                for(int i = 0; i < n; ++i) {
                    from[i] = i;
                }
                application.disconnect(id, from);
                return ResultData.success(null);
            });
        } catch (Exception e) {
            log.error("Move failed", e);
            return ResultData.fail(201, e.getMessage());
        }
    }

    @GetMapping("/connect")
    public ResultData<Object> connect(@RequestParam int id) {
        try {
            return commandExecutor.execute(() -> {
                ShardMasterApplication application = shardMasterConfigUtil.getApplication();
                int n = shardMasterConfigUtil.getConfig().getCount();
                int[] to = new int[n];
                for(int i = 0; i < n; ++i) {
                    to[i] = i;
                }
                application.connect(id, to);
                return ResultData.success(null);
            });
        } catch (Exception e) {
            log.error("Move failed", e);
            return ResultData.fail(201, e.getMessage());
        }
    }

    @GetMapping("/test1")
    public ResultData<Object> test1() {
        new TestDemo().start();
        return ResultData.success(null);
    }

    @GetMapping("/addclusterTest")
    public ResultData<ShardServerConfig> addClusterTest() {
        ShardMasterApplication shardMasterApplication = new ShardMasterApplication();
        ShardServerConfig shardServerConfig = shardMasterApplication.init(3, -1);
        ShardClient shardClient = shardMasterApplication.makeClient();
        shardMasterConfigUtil.setConfig(shardServerConfig);
        shardMasterConfigUtil.setApplication(shardMasterApplication);
        shardMasterWebUtil.setShardClient(shardClient);

        // 初始化进行查询
        ShardConfig config0 = shardClient.query(-1);
        log.info("shardMasterTest: shardConfig0: {} ", config0);

        // join gid 1, gid 2, gid 3
        HashMap<Integer, List<Integer>> joinMap = new HashMap<>();
        joinMap.put(1, new ArrayList<Integer>(Arrays.asList(1, 2, 3)));
        shardClient.join(joinMap);

        joinMap.clear();
        joinMap.put(2, new ArrayList<Integer>(Arrays.asList(4, 5, 6)));
        shardClient.join(joinMap);

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

        shardClient.move(1, 2);
//        Thread.sleep(1000);
        ShardConfig config3 = shardClient.query(-1);
        log.info("shardMasterTest: shardConfig3: {} ", config3);

        shardClient.move(3, 2);
//        Thread.sleep(1000);
        ShardConfig config4 = shardClient.query(-1);
        log.info("shardMasterTest: shardConfig4: {} ", config4);

        shardMasterApplication.cleanup();

        return ResultData.success(shardServerConfig);
    }
}
