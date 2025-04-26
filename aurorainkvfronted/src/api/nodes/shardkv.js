import {service1, service2} from '../../utils/request.js'

export function setShardClient(n) {
    return service1({
      url: '/shardkv/setshardclient', // 修改为后端接口的路径
      method: 'get',      // 使用 GET 方法
      params: { n },      // 将节点数作为参数传递
    })
  }

// 新增集群
export function addKVCluster(data) {
    return service1({
      url: '/shardkv/addcluster',  // 修改为后端接口的路径
      method: 'post',              // 使用 POST 方法
      data: data,                  // 将请求数据传递到请求体
    });
  }
  

// 获取节点
export function getKVNodes() {
    return service1({
        url: '/shardkv/getnodes',
        method: 'get'
    })
}    

export function shutdown(i) {
  return service1({
        url: '/shardkv/shutdown',
        method: 'get',
        params: { i },
  })
}

export function iterator(data) {
  return service1({
    url: '/shardkv/iterator',  
    method: 'post',              
    data: data,                  
  });
}

export function disconnect(data) {
  return service1({
    url: '/shardkv/disconnect',
    method: 'post',
    data: data,
})
}

export function connect(data) {
  return service1({
    url: '/shardkv/connect',
    method: 'post',
    data: data,
  })
}