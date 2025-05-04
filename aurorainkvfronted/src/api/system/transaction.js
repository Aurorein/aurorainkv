import {service1} from '../../utils/request.js'

export function transactionTest(data) {
    return service1({
        url: '/transaction/transactionTest', 
        method: 'post',              
        data: data,        
    })
}