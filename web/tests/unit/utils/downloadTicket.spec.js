import {
  createDownloadObjectUrl,
  normalizeDownloadTicketResponse,
  openDownloadTicket,
  revokeDownloadObjectUrl
} from '@/utils/downloadTicket'

describe('downloadTicket', () => {
  const ticket = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ'
  const response = {
    data: {
      ticket,
      downloadUrl: `/api/media/download/${ticket}`,
      fileName: 'record.mp4'
    }
  }

  beforeEach(() => {
    window.baseUrl = '/wvp'
    global.fetch = jest.fn()
    URL.createObjectURL = jest.fn(() => 'blob:secured-download')
    URL.revokeObjectURL = jest.fn()
  })

  afterEach(() => {
    delete window.baseUrl
    delete global.fetch
  })

  it('只接受受控短票据路径，拒绝原始媒体 URL', () => {
    expect(normalizeDownloadTicketResponse(response).ticket).toBe(ticket)
    expect(() => normalizeDownloadTicketResponse({
      downloadUrl: 'https://media.example/index/api/downloadFile?file_path=/secret'
    })).toThrow('下载地址不是受控票据地址')
  })

  it('公开消费票据时不携带用户凭证并转成可复用 blob URL', async () => {
    const blob = new Blob(['image'], { type: 'image/jpeg' })
    global.fetch.mockResolvedValue({
      ok: true,
      status: 200,
      blob: jest.fn().mockResolvedValue(blob)
    })

    await expect(createDownloadObjectUrl(response)).resolves.toBe('blob:secured-download')
    expect(global.fetch).toHaveBeenCalledWith(
      `/wvp/api/media/download/${ticket}`,
      expect.objectContaining({
        credentials: 'omit',
        cache: 'no-store',
        referrerPolicy: 'no-referrer'
      }))
    expect(URL.createObjectURL).toHaveBeenCalledWith(blob)
  })

  it('下载链接不附加文件路径或 access-token', () => {
    const click = jest.fn()
    const remove = jest.fn()
    const link = { style: {}, click, remove }
    const createElement = jest.spyOn(document, 'createElement').mockReturnValue(link)
    const appendChild = jest.spyOn(document.body, 'appendChild').mockImplementation(() => link)

    openDownloadTicket(response)

    expect(link.href).toBe(`/wvp/api/media/download/${ticket}`)
    expect(link.href).not.toContain('access-token')
    expect(link.href).not.toContain('file_path')
    expect(click).toHaveBeenCalled()
    expect(remove).toHaveBeenCalled()

    createElement.mockRestore()
    appendChild.mockRestore()
  })

  it('只回收 blob URL', () => {
    revokeDownloadObjectUrl('blob:secured-download')
    revokeDownloadObjectUrl('/api/media/download/opaque')
    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(1)
  })
})
