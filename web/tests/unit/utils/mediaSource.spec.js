jest.mock('@/api/mediaAccess', () => ({
  issuePlayTicket: jest.fn()
}))

import { issuePlayTicket } from '@/api/mediaAccess'
import {
  createMediaSourceCancelSource,
  isMediaSourceCancelled,
  isMediaSourceForbidden,
  issueMediaSource,
  normalizeMediaSchema,
  normalizePlayableRef,
  resolveMediaSource
} from '@/utils/mediaSource'

describe('mediaSource', () => {
  const playable = {
    resourceType: 'channel',
    resourceId: 42,
    mediaServerId: 'zlm-1',
    app: 'rtp',
    stream: 'stream-1'
  }

  beforeEach(() => {
    issuePlayTicket.mockReset()
  })

  it('用稳定资源引用和协议申请票据，不把节点字段当成授权输入', async () => {
    const cancelToken = { reason: 'test' }
    issuePlayTicket.mockResolvedValue({
      data: {
        issuedAt: '2026-07-15T08:00:00Z',
        expiresAt: '2026-07-15T08:02:00Z',
        sources: { WS_FLV: 'wss://media.example/live?ticket=opaque' }
      }
    })

    const lease = await issueMediaSource(playable, 'ws-flv', cancelToken)

    expect(issuePlayTicket).toHaveBeenCalledWith({
      resourceType: 'CHANNEL',
      resourceId: 42,
      schema: 'WS_FLV'
    }, cancelToken)
    expect(lease).toEqual(expect.objectContaining({
      schema: 'WS_FLV',
      url: 'wss://media.example/live?ticket=opaque',
      expiresAt: '2026-07-15T08:02:00Z'
    }))
  })

  it('resolveMediaSource 只向播放器返回临时 URL', async () => {
    issuePlayTicket.mockResolvedValue({
      expiresAt: '2026-07-15T08:02:00Z',
      sources: { WEBRTC: 'https://media.example/rtc?ticket=opaque' }
    })

    await expect(resolveMediaSource(playable, 'rtc')).resolves.toBe(
      'https://media.example/rtc?ticket=opaque')
  })

  it('拒绝长期地址对象、无效资源和未知协议', async () => {
    expect(() => normalizePlayableRef({ ws_flv: 'ws://unsafe/live' }))
      .toThrow('缺少有效的可播放资源引用')
    expect(() => normalizePlayableRef({ resourceType: 'CHANNEL', resourceId: 0 }))
      .toThrow('缺少有效的可播放资源引用')
    expect(() => normalizeMediaSchema('fmp4')).toThrow('不支持的媒体协议')
    expect(issuePlayTicket).not.toHaveBeenCalled()
  })

  it('缺少协议 URL 或有效期时 fail closed', async () => {
    issuePlayTicket.mockResolvedValueOnce({
      expiresAt: '2026-07-15T08:02:00Z',
      sources: {}
    })
    await expect(issueMediaSource(playable, 'HLS')).rejects.toMatchObject({
      code: 'MEDIA_SOURCE_MISSING'
    })

    issuePlayTicket.mockResolvedValueOnce({
      sources: { HLS: 'https://media.example/live.m3u8?ticket=opaque' }
    })
    await expect(issueMediaSource(playable, 'HLS')).rejects.toMatchObject({
      code: 'MEDIA_SOURCE_EXPIRY_MISSING'
    })
  })

  it('识别取消和 403，供播放器停止错误重试', () => {
    const cancelSource = createMediaSourceCancelSource()
    cancelSource.cancel('destroyed')

    expect(isMediaSourceCancelled(cancelSource.token.reason)).toBe(true)
    expect(isMediaSourceForbidden({ response: { status: 403 } })).toBe(true)
    expect(isMediaSourceForbidden({ code: 403 })).toBe(true)
    expect(isMediaSourceForbidden({ response: { status: 500 } })).toBe(false)
  })
})
