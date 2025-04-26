package com.aurorain.shardmaster.web;

import com.aurorain.commonmodule.model.ResultData;
import com.aurorain.shardmaster.ShardClient;
import com.aurorain.shardmaster.ShardConfig;
import com.aurorain.shardmaster.ShardMasterApplication;
import com.aurorain.shardmaster.config.ShardServerConfig;
import com.aurorain.shardmaster.web.dto.JoinReq;
import com.aurorain.shardmaster.web.dto.LeaveReq;
import com.aurorain.shardmaster.web.dto.MoveReq;
import com.aurorain.shardmaster.web.util.ShardMasterWebUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shardclient")
@CrossOrigin("*")
public class ShardClientController {

    @Autowired
    ShardMasterWebUtil shardMasterWebUtil;

    @GetMapping("/query")
    public ResultData<ShardConfig> query(@RequestParam int n) {
        ShardClient shardClient = shardMasterWebUtil.getShardClient();
        ShardConfig shardConfig = shardClient.query(n);
        return ResultData.success(shardConfig);
    }

    @PostMapping("/join")
    public ResultData<ShardConfig> join(@RequestBody JoinReq join) {
        ShardClient shardClient = shardMasterWebUtil.getShardClient();
        shardClient.join(join.getServers());
        ShardConfig shardConfig = shardClient.query(-1);
        return ResultData.success(shardConfig);
    }

    @PostMapping("/leave")
    public ResultData<ShardConfig> leave(@RequestBody LeaveReq leave) {
        ShardClient shardClient = shardMasterWebUtil.getShardClient();
        shardClient.leave(leave.getGids());
        ShardConfig shardConfig = shardClient.query(-1);
        return ResultData.success(shardConfig);
    }

    @PostMapping("/move")
    public ResultData<ShardConfig> move(@RequestBody MoveReq move) {
        ShardClient shardClient = shardMasterWebUtil.getShardClient();
        shardClient.move(move.getShard(), move.getGid());
        ShardConfig shardConfig = shardClient.query(-1);
        return ResultData.success(shardConfig);
    }
}
