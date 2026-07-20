const APPLICATION_PROTOCOL = 'wvp-log-v1'
const TICKET_PROTOCOL = /^wvp-log-ticket\.[A-Za-z0-9_-]{40,128}$/

export function normalizeLogWebSocketTicket(response) {
  const payload = response && response.data ? response.data : response
  if (!payload || !Array.isArray(payload.protocols) || payload.protocols.length !== 2) {
    throw ticketError('LOG_WEBSOCKET_TICKET_INVALID', '实时日志握手票据无效')
  }
  const protocols = payload.protocols.slice()
  if (protocols[0] !== APPLICATION_PROTOCOL || !TICKET_PROTOCOL.test(protocols[1])) {
    throw ticketError('LOG_WEBSOCKET_PROTOCOL_INVALID', '实时日志握手协议无效')
  }
  const serialized = protocols.join(',').toLowerCase()
  if (serialized.includes('access-token') || serialized.includes('bearer')) {
    throw ticketError('LOG_WEBSOCKET_LONG_TOKEN_REJECTED', '实时日志不能使用长期令牌')
  }
  return { ...payload, protocols }
}

function ticketError(code, message) {
  const error = new Error(message)
  error.code = code
  return error
}
