import { normalizeLogWebSocketTicket } from '@/utils/logWebSocket'

describe('logWebSocket', () => {
  const ticket = 'A'.repeat(43)

  it('只接受固定协议加一次性握手票据', () => {
    const normalized = normalizeLogWebSocketTicket({
      protocols: ['wvp-log-v1', `wvp-log-ticket.${ticket}`],
      expiresAt: '2026-07-16T00:00:30Z'
    })

    expect(normalized.protocols).toEqual([
      'wvp-log-v1',
      `wvp-log-ticket.${ticket}`
    ])
  })

  it('拒绝把长期 access-token 当作子协议', () => {
    expect(() => normalizeLogWebSocketTicket({
      protocols: ['wvp-log-v1', 'access-token.long-lived-jwt']
    })).toThrow('实时日志握手协议无效')
  })

  it('拒绝外部协议或缺失票据', () => {
    expect(() => normalizeLogWebSocketTicket({
      protocols: ['chat', `wvp-log-ticket.${ticket}`]
    })).toThrow('实时日志握手协议无效')
    expect(() => normalizeLogWebSocketTicket({ protocols: ['wvp-log-v1'] }))
      .toThrow('实时日志握手票据无效')
  })
})
