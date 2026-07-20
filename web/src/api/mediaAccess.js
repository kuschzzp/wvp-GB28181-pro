import request from '@/utils/request'

export function issuePlayTicket(data, cancelToken) {
  return request({
    url: '/api/media/play-ticket',
    method: 'post',
    data,
    cancelToken
  })
}

export function issueDownloadTicket(data, cancelToken) {
  return request({
    url: '/api/media/download-ticket',
    method: 'post',
    data,
    cancelToken
  })
}

export function issueAlarmSnapshotTicket(alarmId, cancelToken) {
  return request({
    url: `/api/alarm/snap/${encodeURIComponent(alarmId)}`,
    method: 'get',
    cancelToken
  })
}

export function issueGbSnapshotTicket(deviceId, channelId, cancelToken) {
  return request({
    url: `/api/device/query/snap/${encodeURIComponent(deviceId)}/${encodeURIComponent(channelId)}`,
    method: 'get',
    cancelToken
  })
}

export function issueJtSnapshotTicket(phoneNumber, channelId, cancelToken) {
  return request({
    url: '/api/jt1078/snap',
    method: 'get',
    params: { phoneNumber, channelId },
    cancelToken
  })
}

export function createStreamShare(data) {
  return request({
    url: '/api/media/share',
    method: 'post',
    data
  })
}

export function revokeStreamShare(shareId) {
  return request({
    url: `/api/media/share/${encodeURIComponent(shareId)}`,
    method: 'delete'
  })
}

export function exchangeStreamShareTicket(shareId, data, cancelToken) {
  return request({
    url: `/api/public/share/${encodeURIComponent(shareId)}/play-ticket`,
    method: 'post',
    data,
    cancelToken,
    skipAuth: true,
    suppressGlobalError: true
  })
}
