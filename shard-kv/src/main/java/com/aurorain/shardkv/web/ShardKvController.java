package com.aurorain.shardkv.web;

import com.aurorain.myrpc.constant.RpcConstant;
import com.aurorain.myrpc.proxy.ServiceProxyFactory;
import com.aurorain.shardkv.KVRaftConfig;
import com.aurorain.shardkv.ShardKVServer;
import com.aurorain.shardkv.web.dto.AddClusterReq;
import com.aurorain.shardkv.web.dto.ConnectReq;
import com.aurorain.shardkv.web.dto.IteratorReq;
import com.aurorain.shardkv.web.util.ShardKvConfigUtil;
import com.aurorain.shardkv.web.util.ShardKvWebUtil;
import com.aurorain.shardmaster.ShardClient;
import com.aurorain.shardkv.ShardKVRaftApplication;
import com.aurorain.commonmodule.model.ResultData;
import com.aurorain.shardmaster.service.ShardServerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@CrossOrigin("*")
@RequestMapping("/shardkv")
public class ShardKvController {

    @Autowired
    ShardKvWebUtil shardKvWebUtil;

    @Autowired
    ShardKvConfigUtil shardKvConfigUtil;

    @GetMapping("/setshardclient")
    public ResultData<Object> setShardClient(int n) {
        ShardServerService[] shardServerServices = new ShardServerService[n];
        for(int i = 0; i < n; ++i) {
            shardServerServices[i] = ServiceProxyFactory.getProxy(ShardServerService.class, i, RpcConstant.SHARDMASTER_SERVICE_VERSION);
        }

        ShardClient shardClient = new ShardClient(shardServerServices);
        shardKvWebUtil.setShardClient(shardClient);
        return ResultData.success(null);
    }

    @PostMapping("/addcluster")
    public ResultData<Map<Integer, KVRaftConfig>> addCluster(@RequestBody AddClusterReq addClusterReq) {
        // 参数校验addClusterReq
        int gid = addClusterReq.getGid();
        if(shardKvConfigUtil.getConfigs().containsKey(gid)) {
            return ResultData.fail(201, "gid已存在!");
        }
        ShardKVRaftApplication application = new ShardKVRaftApplication();
        KVRaftConfig config = application.init(addClusterReq.getServers(), addClusterReq.getGid(), addClusterReq.getMaxRaftState(), shardKvWebUtil.getShardClient(), shardKvWebUtil.getClient());
        shardKvConfigUtil.getConfigs().put(gid, config);
        shardKvConfigUtil.getApplications().put(gid, application);
        Map<Integer, KVRaftConfig> configs = shardKvConfigUtil.getConfigs();
        return ResultData.success(configs);
    }

    @GetMapping("/getnodes")
    public ResultData<Map<Integer, KVRaftConfig>> getNodes() {
        Map<Integer, KVRaftConfig> configs = shardKvConfigUtil.getConfigs();
        return ResultData.success(configs);
    }

    @GetMapping("/shutdown")
    public ResultData<Object> shutdown(@RequestParam int i) {
        Map<Integer, ShardKVRaftApplication> applications = shardKvConfigUtil.getApplications();
        ShardKVRaftApplication application = applications.get(i);
        application.cleanup();

        applications.remove(i);
        Map<Integer, KVRaftConfig> configs = shardKvConfigUtil.getConfigs();
        configs.remove(i);
        if(applications.keySet().isEmpty()) {
            shardKvWebUtil.clearClient();
        }
        return ResultData.success(null);
    }

    @PostMapping("/iterator")
    public ResultData<List<Map.Entry<String, String>>> iterator(@RequestBody IteratorReq iteratorReq) {
        List<Map.Entry<String, String>> kvPairs = new ArrayList<>();
        KVRaftConfig kvRaftConfig = shardKvConfigUtil.getConfigs().get(iteratorReq.getGid());
        ShardKVServer shardKVServer = kvRaftConfig.getShardKvServers().get(iteratorReq.getSid());
        Iterator<Map.Entry<String, String>> iterator = shardKVServer.getState().getStore().iterator();
        while(iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            kvPairs.add(entry);
        }
        return ResultData.success(kvPairs);
    }

    @PostMapping("/disconnect")
    public ResultData<Object> disconnect(@RequestBody ConnectReq req) {
        int gid = req.getGid();
        ShardKVRaftApplication shardKVRaftApplication = shardKvConfigUtil.getApplications().get(gid);
        int id = req.getId();
        KVRaftConfig kvRaftConfig = shardKvConfigUtil.getConfigs().get(gid);
        int[] from = kvRaftConfig.getIds();
        shardKVRaftApplication.disconnect(id, from);
        return ResultData.success(null);
    }

    @PostMapping("/connect")
    public ResultData<Object> connect(@RequestBody ConnectReq req) {
        int gid = req.getGid();
        ShardKVRaftApplication shardKVRaftApplication = shardKvConfigUtil.getApplications().get(gid);
        int id = req.getId();
        KVRaftConfig kvRaftConfig = shardKvConfigUtil.getConfigs().get(gid);
        int[] to = kvRaftConfig.getIds();
        shardKVRaftApplication.connect(id, to);
        return ResultData.success(null);
    }
}
