package com.aurorain.shardkv.transaction;

import com.aurorain.shardkv.constant.TxnConstants;
import lombok.Data;

@Data
public class TxnStatus {
    private TxnState state;
    private long commitTS;
    private long ttl;
    private int txnAction;

    public TxnStatus() {
    }

    public TxnStatus(TxnState state, long commitTS, int ttl) {
        this.state = state;
        this.commitTS = commitTS;
        this.ttl = ttl;
    }

}
