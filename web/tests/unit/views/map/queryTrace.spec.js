import QueryTrace from '@/views/map/queryTrace.vue'

function createContext(overrides = {}) {
  return {
    channel: {
      deviceId: 'channel-a',
      channelId: 101
    },
    searchFrom: '2026-07-15 08:00:00',
    searchTo: '2026-07-15 09:00:00',
    isLoging: false,
    callback: jest.fn(),
    close: jest.fn(),
    $message: {
      error: jest.fn()
    },
    $axios: {
      get: jest.fn().mockResolvedValue({
        data: {
          code: 0,
          data: []
        }
      })
    },
    ...overrides
  }
}

describe('map queryTrace request', () => {
  it('通过 Axios params 传递真实 channelId 和时间范围', async () => {
    const context = createContext()

    const request = QueryTrace.methods.onSubmit.call(context)

    expect(context.$axios.get).toHaveBeenCalledWith(
      '/api/position/history/channel-a',
      {
        params: {
          channelId: 101,
          start: '2026-07-15 08:00:00',
          end: '2026-07-15 09:00:00'
        }
      }
    )
    expect(typeof request.then).toBe('function')
    await request
  })

  it('请求失败后恢复 loading，保留当前对话框供用户重试', async () => {
    const error = new Error('network unavailable')
    const context = createContext({
      $axios: {
        get: jest.fn().mockRejectedValue(error)
      }
    })
    const consoleError = jest.spyOn(console, 'error').mockImplementation(() => {})

    try {
      await QueryTrace.methods.onSubmit.call(context)
      expect(context.isLoging).toBe(false)
      expect(context.close).not.toHaveBeenCalled()
      expect(consoleError).toHaveBeenCalledWith(error)
    } finally {
      consoleError.mockRestore()
    }
  })
})
