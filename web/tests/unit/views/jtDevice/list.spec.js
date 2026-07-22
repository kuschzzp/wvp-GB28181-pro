jest.mock('@/utils/request', () => jest.fn())

import request from '@/utils/request'
import { queryDeviceById } from '@/api/jtDevice'
import JtDeviceList from '@/views/jtDevice/list.vue'
import JtChannelList from '@/views/jtDevice/channel/index.vue'

describe('JT1078 menu availability fallback', () => {
  beforeEach(() => {
    request.mockClear()
  })

  it('设备列表将未启用的 JT1078 接口当作空资源处理', async() => {
    const dispatch = jest.fn().mockRejectedValue({ response: { status: 404 } })
    const context = {
      currentPage: 1,
      count: 15,
      searchStr: '',
      online: '',
      deviceList: [{ id: 1 }],
      total: 1,
      getListLoading: false,
      jt1078Unavailable: false,
      $store: { dispatch },
      $message: { error: jest.fn() },
      stopAutoRefresh: jest.fn(),
      markJt1078Unavailable: JtDeviceList.methods.markJt1078Unavailable
    }

    await JtDeviceList.methods.getList.call(context)

    expect(dispatch).toHaveBeenCalledWith('jtDevice/queryDevices', expect.objectContaining({
      suppressGlobalError: true
    }))
    expect(context.deviceList).toEqual([])
    expect(context.total).toBe(0)
    expect(context.jt1078Unavailable).toBe(true)
    expect(context.getListLoading).toBe(false)
    expect(context.stopAutoRefresh).toHaveBeenCalled()
    expect(context.$message.error).not.toHaveBeenCalled()
    expect(JtDeviceList.computed.tableEmptyText.call(context)).toContain('JT1078')
  })

  it('设备列表的非 404 错误仍提示具体原因', async() => {
    const dispatch = jest.fn().mockRejectedValue({
      response: {
        status: 500,
        data: { msg: '后端处理失败' }
      }
    })
    const context = {
      currentPage: 1,
      count: 15,
      searchStr: '',
      online: '',
      deviceList: [],
      total: 0,
      getListLoading: false,
      jt1078Unavailable: false,
      $store: { dispatch },
      $message: { error: jest.fn() },
      stopAutoRefresh: jest.fn(),
      markJt1078Unavailable: JtDeviceList.methods.markJt1078Unavailable
    }

    await JtDeviceList.methods.getList.call(context)

    expect(context.$message.error).toHaveBeenCalledWith('后端处理失败')
    expect(context.jt1078Unavailable).toBe(false)
  })

  it('通道列表同样静默 JT1078 404 并显示空态', async() => {
    const dispatch = jest.fn().mockRejectedValue({ status: 404 })
    const context = {
      deviceId: '7',
      currentPage: 1,
      count: 15,
      searchSrt: '',
      device: { id: 7 },
      deviceChannelList: [{ id: 1 }],
      total: 1,
      jt1078Unavailable: false,
      $store: { dispatch },
      $message: { error: jest.fn() },
      stopAutoRefresh: jest.fn(),
      markJt1078Unavailable: JtChannelList.methods.markJt1078Unavailable,
      $nextTick: callback => callback(),
      $refs: { channelListTable: { doLayout: jest.fn() } }
    }

    await JtChannelList.methods.getDeviceChannelList.call(context)

    expect(dispatch).toHaveBeenCalledWith('jtDevice/queryChannels', expect.objectContaining({
      deviceId: '7',
      suppressGlobalError: true
    }))
    expect(context.device).toBeNull()
    expect(context.deviceChannelList).toEqual([])
    expect(context.total).toBe(0)
    expect(context.jt1078Unavailable).toBe(true)
    expect(context.$message.error).not.toHaveBeenCalled()
    expect(JtChannelList.computed.tableEmptyText.call(context)).toContain('JT1078')
  })

  it('按 ID 查询终端支持关闭全局错误弹窗', () => {
    queryDeviceById({ deviceId: 7, suppressGlobalError: true })

    expect(request).toHaveBeenCalledWith(expect.objectContaining({
      url: '/api/jt1078/terminal/query',
      suppressGlobalError: true,
      params: { deviceId: 7 }
    }))
  })
})
