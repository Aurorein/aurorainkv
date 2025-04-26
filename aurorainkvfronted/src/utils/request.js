import axios from 'axios'
import { ElMessage, ElNotification } from 'element-plus'

axios.defaults.headers['Content-Type'] = 'application/json;charset=utf-8'
export const errorCode = {
  401: '认证失败，无法访问系统资源',
  403: '当前操作没有权限',
  404: '访问资源不存在',
  default: '系统未知错误，请反馈给管理员',
}

// 创建第一个 axios 实例，用于访问第一个服务
const service1 = axios.create({
  timeout: 20000,
  baseURL: 'http://127.0.0.1:8082', // 第一个服务的地址
})

// 创建第二个 axios 实例，用于访问第二个服务
const  service2 = axios.create({
  timeout: 20000,
  baseURL: 'http://127.0.0.1:8081', // 第二个服务的地址
})

// 请求拦截器
const requestInterceptor = (config) => {
  // 在这里可以添加一些通用的请求配置，比如添加 token 等
  return config
}

// 响应拦截器
const responseInterceptor = (res) => {
  const code = res.data.code || 200
  const msg = res.data.msg || errorCode[code] || errorCode.default

  if (
    res.request.responseType === 'blob' ||
    res.request.responseType === 'arraybuffer'
  ) {
    return res.data
  }

  if (code === 401) {
    return Promise.reject(new Error(msg))
  }

  if (code === 500) {
    ElMessage({ message: msg, type: 'error' })
    return Promise.reject(new Error(msg))
  }

  if (code !== 200) {
    ElNotification.error({ title: msg })
    return Promise.reject(new Error('error'))
  }

  return Promise.resolve(res.data)
}

// 错误拦截器
const errorInterceptor = (error) => {
  return Promise.reject(error)
}

// 为每个实例添加拦截器
service1.interceptors.request.use(requestInterceptor, errorInterceptor)
service1.interceptors.response.use(responseInterceptor, errorInterceptor)

service2.interceptors.request.use(requestInterceptor, errorInterceptor)
service2.interceptors.response.use(responseInterceptor, errorInterceptor)

// 导出不同的服务实例
export { service1, service2 }