import axios from 'axios'
import { issuePlayTicket } from '@/api/mediaAccess'

export const MEDIA_SCHEMAS = Object.freeze({
  FLV: 'FLV',
  WS_FLV: 'WS_FLV',
  HLS: 'HLS',
  WEBRTC: 'WEBRTC',
  RTSP: 'RTSP',
  RTMP: 'RTMP'
})

const SCHEMA_ALIASES = Object.freeze({
  FLV: MEDIA_SCHEMAS.FLV,
  HTTP_FLV: MEDIA_SCHEMAS.FLV,
  WS_FLV: MEDIA_SCHEMAS.WS_FLV,
  WSS_FLV: MEDIA_SCHEMAS.WS_FLV,
  HLS: MEDIA_SCHEMAS.HLS,
  WEBRTC: MEDIA_SCHEMAS.WEBRTC,
  RTC: MEDIA_SCHEMAS.WEBRTC,
  RTCS: MEDIA_SCHEMAS.WEBRTC,
  RTSP: MEDIA_SCHEMAS.RTSP,
  RTMP: MEDIA_SCHEMAS.RTMP
})

export function normalizeMediaSchema(schema) {
  const key = String(schema || '').trim().replace(/-/g, '_').toUpperCase()
  const normalized = SCHEMA_ALIASES[key]
  if (!normalized) {
    throw mediaSourceError('MEDIA_SCHEMA_UNSUPPORTED', '不支持的媒体协议')
  }
  return normalized
}

export function normalizePlayableRef(value) {
  const ref = value && value.playable ? value.playable : value
  const resourceType = String(ref && ref.resourceType || '').trim().toUpperCase()
  const resourceId = Number(ref && ref.resourceId)
  if (!resourceType || !Number.isSafeInteger(resourceId) || resourceId <= 0) {
    throw mediaSourceError('PLAYABLE_REF_REQUIRED', '缺少有效的可播放资源引用')
  }
  return Object.freeze({
    resourceType,
    resourceId,
    mediaServerId: optionalText(ref.mediaServerId),
    app: optionalText(ref.app),
    stream: optionalText(ref.stream)
  })
}

export async function issueMediaSource(playableRef, schema, cancelToken) {
  const ref = normalizePlayableRef(playableRef)
  const normalizedSchema = normalizeMediaSchema(schema)
  const response = await issuePlayTicket({
    resourceType: ref.resourceType,
    resourceId: ref.resourceId,
    schema: normalizedSchema
  }, cancelToken)
  const payload = response && response.data ? response.data : response
  const sources = payload && payload.sources ? payload.sources : {}
  const url = sources[normalizedSchema] || sources[normalizedSchema.toLowerCase()]
  if (!url || typeof url !== 'string') {
    throw mediaSourceError('MEDIA_SOURCE_MISSING', '服务端未返回所选协议的临时播放地址')
  }
  const expiresAt = payload.expiresAt
  if (!expiresAt || Number.isNaN(Date.parse(expiresAt))) {
    throw mediaSourceError('MEDIA_SOURCE_EXPIRY_MISSING', '临时播放地址缺少有效期')
  }
  return Object.freeze({
    url,
    schema: normalizedSchema,
    issuedAt: payload.issuedAt || null,
    expiresAt,
    binding: payload.binding || null
  })
}

export async function resolveMediaSource(playableRef, schema, cancelToken) {
  return (await issueMediaSource(playableRef, schema, cancelToken)).url
}

export function createMediaSourceCancelSource() {
  return axios.CancelToken.source()
}

export function isMediaSourceCancelled(error) {
  return axios.isCancel(error)
}

export function isMediaSourceForbidden(error) {
  const status = error && error.response && error.response.status
  return status === 403 || Number(error && error.code) === 403
}

function optionalText(value) {
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

function mediaSourceError(code, message) {
  const error = new Error(message)
  error.code = code
  return error
}
