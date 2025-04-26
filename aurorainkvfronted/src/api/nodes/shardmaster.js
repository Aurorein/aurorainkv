import {service1, service2} from '../../utils/request.js'

// 新增集群
export function addCluster(n) {
    return service2({
      url: '/shardmaster/addcluster', // 修改为后端接口的路径
      method: 'get',      // 使用 GET 方法
      params: { n },      // 将节点数作为参数传递
    })
  }

// 获取节点
export function getNodes() {
    return service2({
        url: '/shardmaster/getnodes',
        method: 'get'
    })
}  

export function shutdown() {
  return service2({
        url: '/shardmaster/shutdown',
        method: 'get'
  })
}

export function disconnect(id) {
  return service2({
    url: '/shardmaster/disconnect',
    method: 'get',
    params: { id }
})
}

export function connect(id) {
  return service2({
    url: '/shardmaster/connect',
    method: 'get',
    params: { id }
  })
}