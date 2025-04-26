import {service1, service2} from '../../utils/request.js'

export function get(data) {
    return service1({
        url: '/kvclient/get', 
        method: 'post',              
        data: data,        
    })
}

export function put(data) {
    return service1({
        url: '/kvclient/put', 
        method: 'post',              
        data: data,        
    })
}

export function batch(data) {
    return service1({
        url: '/kvclient/batch', 
        method: 'post',              
        data: data,        
    })
}

export function concurrentTest() {
    return service1({
        url: '/kvclient/consistency-test', 
        method: 'get'             
    })
}