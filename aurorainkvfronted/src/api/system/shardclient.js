import {service1, service2} from '../../utils/request.js'

export function query(n) {
    return service2({
        url: '/shardmaster/query', 
        method: 'get',      
        params: { n },      
    })
}

export function join(data) {
    return service2({
        url: '/shardmaster/join',  
        method: 'post',              
        data: data,                  
    });
}

export function leave(data) {
    return service2({
        url: '/shardmaster/leave',  
        method: 'post',              
        data: data,                  
    });
}

export function move(data) {
    return service2({
        url: '/shardmaster/move',  
        method: 'post',              
        data: data,                  
    });
}


