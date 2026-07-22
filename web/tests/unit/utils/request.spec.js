jest.mock('element-ui', () => ({
  MessageBox: {
    confirm: jest.fn(() => Promise.resolve())
  },
  Message: {
    error: jest.fn(),
    warning: jest.fn()
  }
}))

jest.mock('@/store', () => ({
  getters: {
    token: '',
    showConfirmBoxForLoginLose: true
  },
  dispatch: jest.fn(() => Promise.resolve())
}))

jest.mock('@/utils/auth', () => ({
  getToken: jest.fn(() => 'signed-token')
}))

import request from '@/utils/request'
import { Message, MessageBox } from 'element-ui'

function successAdapter(data, status = 200) {
  return config => Promise.resolve({
    data,
    status,
    statusText: 'OK',
    headers: {},
    config
  })
}

function httpErrorAdapter(status, data) {
  return config => Promise.reject({
    message: 'Request failed with status code ' + status,
    config,
    response: {
      data,
      status,
      statusText: String(status),
      headers: {},
      config
    }
  })
}

function networkErrorAdapter(message, code) {
  return config => {
    const error = new Error(message)
    error.config = config
    if (code) error.code = code
    return Promise.reject(error)
  }
}

describe('request error prompt', () => {
  let consoleLog

  beforeEach(() => {
    jest.clearAllMocks()
    MessageBox.confirm.mockReturnValue(new Promise(() => {}))
    consoleLog = jest.spyOn(console, 'log').mockImplementation(() => {})
  })

  afterEach(() => {
    consoleLog.mockRestore()
  })

  it('业务失败响应显示后端返回的具体提示', async() => {
    await expect(request({
      url: '/api/system/dept/2',
      method: 'delete',
      adapter: successAdapter({
        code: 100,
        msg: '部门存在子部门，不能删除'
      })
    })).rejects.toMatchObject({
      message: '部门存在子部门，不能删除',
      code: 100,
      status: 200
    })

    expect(Message.error).toHaveBeenCalledWith({
      message: '部门存在子部门，不能删除',
      showClose: true
    })
  })

  it('HTTP 错误优先显示响应体中的业务提示', async() => {
    await expect(request({
      url: '/api/system/dept/2',
      method: 'delete',
      adapter: httpErrorAdapter(409, {
        code: 100,
        msg: '部门存在子部门，不能删除'
      })
    })).rejects.toMatchObject({
      message: '部门存在子部门，不能删除',
      code: 100,
      status: 409
    })

    expect(Message.error).toHaveBeenCalledWith({
      message: '部门存在子部门，不能删除',
      showClose: true
    })
  })

  it('静默请求仍返回规范化错误但不弹全局提示', async() => {
    await expect(request({
      url: '/api/system/dept/2',
      method: 'delete',
      suppressGlobalError: true,
      adapter: successAdapter({
        code: 100,
        msg: '部门存在子部门，不能删除'
      })
    })).rejects.toMatchObject({
      message: '部门存在子部门，不能删除'
    })

    expect(Message.error).not.toHaveBeenCalled()
  })

  it('网络异常不把 axios 英文错误直接展示给用户', async() => {
    await expect(request({
      url: '/api/system/dept/tree',
      method: 'get',
      adapter: networkErrorAdapter('Network Error')
    })).rejects.toMatchObject({
      message: '网络连接异常，请检查服务是否可用'
    })

    expect(Message.error).toHaveBeenCalledWith({
      message: '网络连接异常，请检查服务是否可用',
      showClose: true
    })
  })

  it('登录接口 401 不触发登录过期确认弹窗', async() => {
    await expect(request({
      url: '/api/auth/login',
      method: 'post',
      adapter: httpErrorAdapter(401, {
        code: 401,
        msg: '用户名或密码错误'
      })
    })).rejects.toMatchObject({
      message: '用户名或密码错误',
      code: 401,
      status: 401
    })

    expect(MessageBox.confirm).not.toHaveBeenCalled()
    expect(Message.error).not.toHaveBeenCalled()
  })

  it('非登录接口 401 仍触发登录过期确认弹窗', async() => {
    await expect(request({
      url: '/api/auth/profile',
      method: 'get',
      adapter: httpErrorAdapter(401, {
        code: 401,
        msg: '未认证'
      })
    })).rejects.toMatchObject({
      message: '未认证',
      code: 401,
      status: 401
    })

    expect(MessageBox.confirm).toHaveBeenCalledWith('登录已经到期， 是否重新登录', '登录确认', {
      confirmButtonText: '重新登录',
      cancelButtonText: '取消',
      type: 'warning'
    })
  })
})
