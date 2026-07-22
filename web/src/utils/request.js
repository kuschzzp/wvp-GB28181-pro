import axios from 'axios'
import { MessageBox, Message } from 'element-ui'
import store from '@/store'
import { getToken } from '@/utils/auth'

let showLoginConfirm = false

function extractMessage(data, fallback) {
  if (typeof data === 'string' && data.trim()) return data.trim()
  if (data && data.msg) return data.msg
  if (data && data.message) return data.message
  return fallback
}

function fallbackMessage(error, status) {
  if (error && (error.code === 'ECONNABORTED' || /timeout/i.test(error.message || ''))) {
    return '请求超时，请稍后重试'
  }
  if (!status) {
    return '网络连接异常，请检查服务是否可用'
  }
  if (status === 403) {
    return '无权限访问'
  }
  if (status === 404) {
    return '请求资源不存在'
  }
  if (status >= 500) {
    return '系统异常'
  }
  return '操作失败，请稍后重试'
}

function createRequestError({ message, code, status, response, config, originalError }) {
  const error = originalError instanceof Error ? originalError : new Error(message || '请求失败')
  error.message = message || error.message || '请求失败'
  if (typeof code !== 'undefined') error.code = code
  if (typeof status !== 'undefined') error.status = status
  if (response) error.response = response
  if (config) error.config = config
  return error
}

function notifyError(message) {
  Message.error({
    message: message || '操作失败，请稍后重试',
    showClose: true
  })
}

function isLoginRequest(config) {
  return config && config.url && config.url.indexOf('/api/auth/login') >= 0
}

// create an axios instance
const service = axios.create({
  baseURL: process.env.VUE_APP_BASE_API, // url = base url + request url
  // withCredentials: true, // send cookies when cross-domain requests
  timeout: 30000 // request timeout
})

// request interceptor
service.interceptors.request.use(
  config => {
    // do something before request is sent
    if (store.getters.token && !isLoginRequest(config) && !config.skipAuth) {
      config.headers['access-token'] = getToken()
    }
    return config
  },
  error => {
    // do something with request error
    console.log(error) // for debug
    return Promise.reject(error)
  }
)

// response interceptor
service.interceptors.response.use(
  /**
   * If you want to get http information such as headers or status
   * Please return  response => response
  */

  /**
   * Determine the request status by custom code
   * Here is just an example
   * You can also judge the status by HTTP Status Code
   */
  response => {
    const res = response.data
    if (res.code && res.code !== 0) {
      const error = createRequestError({
        message: extractMessage(res, '操作失败'),
        code: res.code,
        status: response.status,
        response,
        config: response.config
      })
      if (!response.config || !response.config.suppressGlobalError) {
        notifyError(error.message)
      }
      return Promise.reject(error)
    } else {
      return res
    }
  },
  error => {
    console.log(error) // for debug
    if (error.config && error.config.suppressGlobalError) {
      return Promise.reject(error)
    }
    const status = error.response && error.response.status
    const data = error.response && error.response.data
    const normalizedError = createRequestError({
      message: extractMessage(data, fallbackMessage(error, status)),
      code: data && data.code,
      status,
      response: error.response,
      config: error.config,
      originalError: error
    })
    if (status === 401) {
      if (isLoginRequest(error.config)) {
        return Promise.reject(normalizedError)
      }
      if (!showLoginConfirm && store.getters.showConfirmBoxForLoginLose) {
        // to re-login
        showLoginConfirm = true
        MessageBox.confirm('登录已经到期， 是否重新登录', '登录确认', {
          confirmButtonText: '重新登录',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          store.dispatch('user/resetToken').then(() => {
            location.reload()
          })
        }).catch(() => {
          store.dispatch('user/closeConfirmBoxForLoginLose')
          Message.warning({
            type: 'warning',
            message: '登录过期提示已经关闭，请注销后重新登录'
          })
          // 清除token， 后续请求不再继续

        })
      }
    } else if (status === 403) {
      notifyError(normalizedError.message || '无权限')
    } else {
      if (!store.getters.showConfirmBoxForLoginLose) {
        return Promise.reject(normalizedError)
      }
      notifyError(normalizedError.message)
    }
    return Promise.reject(normalizedError)
  }
)

export default service
