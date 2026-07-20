const DOWNLOAD_PATH = /^\/api\/media\/download\/([A-Za-z0-9_-]{40,128})$/

export function normalizeDownloadTicketResponse(response) {
  const payload = response && response.data ? response.data : response
  if (!payload || typeof payload.downloadUrl !== 'string') {
    throw downloadError('DOWNLOAD_TICKET_MISSING', '下载票据响应无效')
  }
  const match = DOWNLOAD_PATH.exec(payload.downloadUrl)
  if (!match || (payload.ticket && payload.ticket !== match[1])) {
    throw downloadError('DOWNLOAD_TICKET_URL_INVALID', '下载地址不是受控票据地址')
  }
  return payload
}

export function resolveDownloadTicketUrl(response) {
  const payload = normalizeDownloadTicketResponse(response)
  const base = apiBaseUrl()
  return `${base}${payload.downloadUrl}`
}

export async function createDownloadObjectUrl(response, signal) {
  const url = resolveDownloadTicketUrl(response)
  const fetchResponse = await fetch(url, {
    method: 'GET',
    credentials: 'omit',
    cache: 'no-store',
    referrerPolicy: 'no-referrer',
    signal
  })
  if (!fetchResponse.ok) {
    throw downloadError(
      'DOWNLOAD_TICKET_CONSUME_FAILED',
      `下载票据读取失败（HTTP ${fetchResponse.status}）`)
  }
  return URL.createObjectURL(await fetchResponse.blob())
}

export function revokeDownloadObjectUrl(url) {
  if (typeof url === 'string' && url.startsWith('blob:')) {
    URL.revokeObjectURL(url)
  }
}

export function openDownloadTicket(response) {
  const payload = normalizeDownloadTicketResponse(response)
  const link = document.createElement('a')
  link.href = resolveDownloadTicketUrl(payload)
  link.target = '_blank'
  link.rel = 'noopener noreferrer'
  link.referrerPolicy = 'no-referrer'
  if (payload.fileName) link.download = payload.fileName
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  link.remove()
}

export async function downloadTicketAsFile(response, signal, fileName) {
  const payload = normalizeDownloadTicketResponse(response)
  const objectUrl = await createDownloadObjectUrl(payload, signal)
  try {
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = fileName || payload.fileName || 'download.bin'
    link.style.display = 'none'
    document.body.appendChild(link)
    link.click()
    link.remove()
  } finally {
    revokeDownloadObjectUrl(objectUrl)
  }
}

function apiBaseUrl() {
  let base = ''
  if (process.env.NODE_ENV === 'development') {
    base = process.env.VUE_APP_BASE_API || ''
  } else if (typeof window !== 'undefined') {
    base = window.baseUrl || process.env.VUE_APP_BASE_API || ''
  }
  return String(base).replace(/\/$/, '')
}

function downloadError(code, message) {
  const error = new Error(message)
  error.code = code
  return error
}
