jest.mock('@/api/mediaAccess', () => ({
  createStreamShare: jest.fn(),
  revokeStreamShare: jest.fn()
}))

import StreamMediaPanel from '@/views/common/streamMediaPanel.vue'
import {
  createStreamShare,
  revokeStreamShare
} from '@/api/mediaAccess'

describe('StreamMediaPanel controlled shares', () => {
  const playableRef = { resourceType: 'CHANNEL', resourceId: 42 }

  beforeEach(() => {
    jest.clearAllMocks()
  })

  it('用稳定资源引用创建 shareId 链接，不复制媒体 URL', async () => {
    createStreamShare.mockResolvedValue({
      data: {
        shareId: 'abcdefghijklmnopqrstuvwxyzABCDEF',
        expiresAt: '2026-07-15T10:00:00Z',
        maxViews: 3
      }
    })
    const context = createContext(playableRef)
    context.durationMinutes = 60
    context.maxViews = 3
    context.passcode = '2468'

    await context.createShare()

    expect(createStreamShare).toHaveBeenCalledWith(expect.objectContaining({
      resourceType: 'CHANNEL',
      resourceId: 42,
      maxViews: 3,
      passcode: '2468'
    }))
    expect(context.shareUrl).toBe(
      'http://localhost/#/play/share/abcdefghijklmnopqrstuvwxyzABCDEF')
    expect(JSON.stringify(createStreamShare.mock.calls))
      .not.toMatch(/\.flv|\.m3u8|ticket=/)
  })

  it('只有撤销权限持有者能撤销已创建的分享', async () => {
    revokeStreamShare.mockResolvedValue({ code: 0 })
    const context = createContext(playableRef)
    context.createdShare = { shareId: 'abcdefghijklmnopqrstuvwxyzABCDEF' }

    await context.revokeShare()

    expect(revokeStreamShare).toHaveBeenCalledWith(
      'abcdefghijklmnopqrstuvwxyzABCDEF')
    expect(context.createdShare).toBe(null)
  })

  function createContext(ref) {
    const context = {
      ...StreamMediaPanel.data(),
      playableRef: ref,
      expiresAt: '2026-07-15T09:02:00Z',
      $auth: {
        hasPermi: jest.fn(() => true)
      },
      $message: {
        success: jest.fn()
      },
      $copyText: jest.fn(() => Promise.resolve())
    }
    Object.keys(StreamMediaPanel.computed).forEach(name => {
      Object.defineProperty(context, name, {
        configurable: true,
        get: StreamMediaPanel.computed[name].bind(context)
      })
    })
    Object.keys(StreamMediaPanel.methods).forEach(name => {
      context[name] = StreamMediaPanel.methods[name].bind(context)
    })
    return context
  }
})
