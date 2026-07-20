jest.mock('@/api/mediaAccess', () => ({
  exchangeStreamShareTicket: jest.fn()
}))

jest.mock('@/utils/mediaSource', () => ({
  createMediaSourceCancelSource: jest.fn(),
  isMediaSourceCancelled: jest.fn()
}))

import SharePlayer from '@/views/common/share.vue'
import { exchangeStreamShareTicket } from '@/api/mediaAccess'
import {
  createMediaSourceCancelSource,
  isMediaSourceCancelled
} from '@/utils/mediaSource'

describe('SharePlayer', () => {
  const shareId = 'abcdefghijklmnopqrstuvwxyzABCDEF'
  let cancelSources

  beforeEach(() => {
    jest.useFakeTimers()
    cancelSources = []
    exchangeStreamShareTicket.mockReset()
    isMediaSourceCancelled.mockReset().mockReturnValue(false)
    createMediaSourceCancelSource.mockReset().mockImplementation(() => {
      const source = { token: {}, cancel: jest.fn() }
      cancelSources.push(source)
      return source
    })
  })

  afterEach(() => {
    jest.clearAllTimers()
    jest.useRealTimers()
  })

  it('只从动态路由读取 shareId，不读取查询参数中的旧媒体 URL', async () => {
    exchangeStreamShareTicket.mockResolvedValue(lease('one'))
    const context = createContext({
      params: { shareId },
      query: { url: 'https://attacker.example/raw.flv', type: '2' }
    })

    await context.initializeShare()

    expect(exchangeStreamShareTicket).toHaveBeenCalledWith(
      shareId,
      { schema: 'WS_FLV', passcode: null },
      cancelSources[0].token
    )
    expect(context.$refs.jessibuca.play).toHaveBeenCalledWith(
      'wss://media.example/live?ticket=one')
    expect(JSON.stringify(exchangeStreamShareTicket.mock.calls))
      .not.toContain('attacker.example')
  })

  it('需要提取码时暂停重试，正确提交后才播放', async () => {
    exchangeStreamShareTicket.mockRejectedValueOnce({
      response: { status: 403, data: { msg: 'PASSCODE_REQUIRED' } }
    }).mockResolvedValueOnce(lease('protected'))
    const context = createContext({ params: { shareId }, query: {} })

    await context.initializeShare()
    expect(context.passcodeVisible).toBe(true)
    expect(context.retryTimer).toBe(null)

    context.passcode = '2468'
    await context.submitPasscode()

    expect(exchangeStreamShareTicket.mock.calls[1][1]).toEqual({
      schema: 'WS_FLV',
      passcode: '2468'
    })
    expect(context.passcodeVisible).toBe(false)
    expect(context.$refs.jessibuca.play).toHaveBeenCalledTimes(1)
  })

  it('撤销、过期或次数耗尽属于终态，不会自动重试', async () => {
    exchangeStreamShareTicket.mockRejectedValue({
      response: { status: 410, data: { msg: 'REVOKED' } }
    })
    const context = createContext({ params: { shareId }, query: {} })

    await context.initializeShare()
    jest.advanceTimersByTime(30000)
    await flushPromises()

    expect(context.terminalError).toBe(true)
    expect(context.errorMessage).toBe('分享已被撤销')
    expect(exchangeStreamShareTicket).toHaveBeenCalledTimes(1)
  })

  it('在票据过期前续领，销毁时取消未完成请求', async () => {
    const now = Date.now()
    exchangeStreamShareTicket.mockResolvedValueOnce(lease(
      'short', new Date(now + 20000).toISOString()))
      .mockResolvedValueOnce(lease('renewed'))
    const context = createContext({ params: { shareId }, query: {} })

    await context.initializeShare()
    jest.advanceTimersByTime(5000)
    await flushPromises()
    expect(exchangeStreamShareTicket).toHaveBeenCalledTimes(2)

    const pending = deferred()
    exchangeStreamShareTicket.mockReturnValueOnce(pending.promise)
    const activeRequest = context.exchangeAndPlay()
    context.teardown()
    pending.resolve(lease('late'))
    await activeRequest

    expect(cancelSources[2].cancel).toHaveBeenCalled()
    expect(context.destroyedState).toBe(true)
    expect(context.$refs.jessibuca.play).toHaveBeenCalledTimes(1)
  })

  function createContext(route) {
    const context = {
      ...SharePlayer.data(),
      $route: route,
      $refs: {
        jessibuca: player(),
        webRTC: player(),
        h265web: player()
      },
      $nextTick: jest.fn(() => Promise.resolve())
    }
    Object.keys(SharePlayer.methods).forEach(name => {
      context[name] = SharePlayer.methods[name].bind(context)
    })
    return context
  }

  function player() {
    return { play: jest.fn(), pause: jest.fn() }
  }

  function lease(ticket, expiresAt) {
    return {
      data: {
        expiresAt: expiresAt || new Date(Date.now() + 120000).toISOString(),
        sources: {
          WS_FLV: `wss://media.example/live?ticket=${ticket}`
        }
      }
    }
  }

  function deferred() {
    let resolve
    let reject
    const promise = new Promise((res, rej) => {
      resolve = res
      reject = rej
    })
    return { promise, resolve, reject }
  }

  async function flushPromises() {
    await Promise.resolve()
    await Promise.resolve()
  }
})
