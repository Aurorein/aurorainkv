package com.aurorain.shardkv.web.util;

import com.aurorain.shardkv.Client;
import com.aurorain.shardmaster.ShardClient;
import com.aurorain.commonmodule.exceptions.ShardClientEmptyException;
import org.springframework.stereotype.Component;

@Component
public class ShardKvWebUtil {
    private ShardClient shardClient;

    private Client client;

    public Client getClient() {
        if(client == null) {
            synchronized (this) {
                if(shardClient == null) {
                    throw new ShardClientEmptyException();
                } else {
                    return makeClient();
                }
            }
        }
        return client;
    }

    public Client makeClient() {
        if(client == null) {
            synchronized (this) {
                if(client == null) {
                    client = new Client(shardClient);
                }
            }
        }
        return client;
    }

    public Client newClient() {
        Client client1 = new Client(shardClient);
        synchronized (this) {
            for(Integer gid : this.client.getServices().keySet()) {
                client1.addGroup(gid, this.client.getKvServices(gid));
            }
        }
        return client1;
    }

    public void clearClient() {
        this.client = null;
    }

    public ShardClient setShardClient(ShardClient shardClient) {
        if(this.shardClient == null) {
            synchronized (this) {
                if(this.shardClient == null) {
                    this.shardClient = shardClient;
                }
            }
        }

        return shardClient;
    }

    public ShardClient getShardClient() {
        if(shardClient == null) {
            throw new ShardClientEmptyException();
        }
        return shardClient;
    }
}
