jest.mock('@/utils/mediaSource', () => ({
  createMediaSourceCancelSource: jest.fn(),
  isMediaSourceCancelled: jest.fn(),
  isMediaSourceForbidden: jest.fn(),
  issueMediaSource: jest.fn(),
  normalizePlayableRef: jest.fn(value => value)
}))

import PlayerTabs from '@/views/common/playerTabs.vue'
import {
  createMediaSourceCancelSource,
  isMediaSourceCancelled,
  isMediaSourceForbidden,
  issueMediaSource,
  normalizePlayableRef
} from '@/utils/mediaSource'

describe('PlayerTabs ticket lifecycle', () => {
  const playable = { resourceType: 'CHANNEL', resourceId: 42 }
  let mockCancelSources

  beforeEach(() => {
    jest.useFakeTimers()
    mockCancelSources = []
    issueMediaSource.mockReset()
    normalizePlayableRef.mockClear()
    isMediaSourceCancelled.mockReset().mockReturnValue(false)
    isMediaSourceForbidden.mockReset().mockReturnValue(false)
    createMediaSourceCancelSource.mockReset().mockImplementation(() => {
      const source = { token: {}, cancel: jest.fn() }
      mockCancelSources.push(source)
      return source
    })
  })

  afterEach(() => {
    jest.clearAllTimers()
    jest.useRealTimers()
  })

  it('首次播放申请 WS-FLV 票据并只把短期 URL 交给播放器', async () => {
    issueMediaSource.mockResolvedValue(lease('one', 'WS_FLV'))
    const context = createContext()

    await context.setStreamInfo(playable)

    expect(normalizePlayableRef).toHaveBeenCalledWith(playable)
    expect(issueMediaSource).toHaveBeenCalledWith(
      playable, 'WS_FLV', mockCancelSources[0].token)
    expect(context.$refs.jessibuca.play).toHaveBeenCalledWith(
      'wss://media.example/live?ticket=one')
    expect(context.$emit).toHaveBeenCalledWith('playerChanged',
      expect.objectContaining({ schema: 'WS_FLV' }))
  })

  it('切换 WebRTC 会取消旧请求并申请对应协议', async () => {
    issueMediaSource.mockResolvedValueOnce(lease('flv', 'WS_FLV'))
      .mockResolvedValueOnce(lease('rtc', 'WEBRTC'))
    const context = createContext()
    await context.setStreamInfo(playable)

    await context.switchPlayer('webRTC')

    expect(issueMediaSource.mock.calls[1][1]).toBe('WEBRTC')
    expect(context.$refs.webRTC.play).toHaveBeenCalledWith(
      'wss://media.example/live?ticket=rtc')
  })

  it('切换 H265web 会重新申请 HLS 票据', async () => {
    issueMediaSource.mockResolvedValueOnce(lease('flv', 'WS_FLV'))
      .mockResolvedValueOnce(lease('hls', 'HLS'))
    const context = createContext()
    await context.setStreamInfo(playable)

    await context.switchPlayer('h265web')

    expect(issueMediaSource.mock.calls[1][1]).toBe('HLS')
    expect(context.$refs.h265web.play).toHaveBeenCalledWith(
      'wss://media.example/live?ticket=hls')
  })

  it('快速切换资源会取消旧请求，迟到响应不能覆盖新流', async () => {
    const first = deferred()
    const second = deferred()
    issueMediaSource.mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)
    const context = createContext()

    const firstPlay = context.setStreamInfo({ resourceType: 'CHANNEL', resourceId: 1 })
    const secondPlay = context.setStreamInfo({ resourceType: 'CHANNEL', resourceId: 2 })
    second.resolve(lease('new', 'WS_FLV'))
    await secondPlay
    first.resolve(lease('old', 'WS_FLV'))
    await firstPlay

    expect(mockCancelSources[0].cancel).toHaveBeenCalled()
    expect(context.$refs.jessibuca.play).toHaveBeenCalledTimes(1)
    expect(context.$refs.jessibuca.play).toHaveBeenCalledWith(
      'wss://media.example/live?ticket=new')
  })

  it('票据过期前自动续领，播放器重连也会申请新票据', async () => {
    const now = Date.now()
    issueMediaSource.mockResolvedValue(lease(
      'renewable', 'WS_FLV', new Date(now + 20000).toISOString()))
    const context = createContext()
    await context.setStreamInfo(playable)

    jest.advanceTimersByTime(5000)
    await flushPromises()
    expect(issueMediaSource).toHaveBeenCalledTimes(2)

    context.handlePlayerStatus(false)
    jest.advanceTimersByTime(1000)
    await flushPromises()
    expect(issueMediaSource).toHaveBeenCalledTimes(3)
  })

  it('403 后停止自动重试并向上报告错误', async () => {
    const forbidden = { response: { status: 403 } }
    issueMediaSource.mockRejectedValue(forbidden)
    isMediaSourceForbidden.mockReturnValue(true)
    const context = createContext()

    await context.setStreamInfo(playable)
    jest.advanceTimersByTime(10000)
    await flushPromises()

    expect(context.retryBlocked).toBe(true)
    expect(context.retryTimer).toBe(null)
    expect(issueMediaSource).toHaveBeenCalledTimes(1)
    expect(context.$emit).toHaveBeenCalledWith('play-error', forbidden)
  })

  it('组件销毁会取消请求并阻止迟到响应播放', async () => {
    const pending = deferred()
    issueMediaSource.mockReturnValue(pending.promise)
    const context = createContext()

    const playback = context.setStreamInfo(playable)
    context.teardown(true)
    pending.resolve(lease('late', 'WS_FLV'))
    await playback

    expect(mockCancelSources[0].cancel).toHaveBeenCalled()
    expect(context.$refs.jessibuca.play).not.toHaveBeenCalled()
    expect(context.destroyedState).toBe(true)
  })

  function createContext() {
    const context = {
      ...PlayerTabs.data(),
      $refs: {
        jessibuca: playerMock(),
        webRTC: playerMock(),
        h265web: playerMock()
      },
      $emit: jest.fn(),
      $nextTick: jest.fn(() => Promise.resolve())
    }
    Object.keys(PlayerTabs.methods).forEach(name => {
      context[name] = PlayerTabs.methods[name].bind(context)
    })
    return context
  }

  function playerMock() {
    return {
      play: jest.fn(),
      pause: jest.fn(),
      destroy: jest.fn()
    }
  }

  function lease(ticket, schema, expiresAt) {
    return {
      url: `wss://media.example/live?ticket=${ticket}`,
      schema,
      expiresAt: expiresAt || new Date(Date.now() + 120000).toISOString()
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
