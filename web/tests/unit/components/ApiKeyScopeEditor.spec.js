import { parseIpAllowlist } from '@/views/user/dialog/apiKeyScopeEditor.vue'

describe('ApiKeyScopeEditor.vue', () => {
  it('按逗号和换行拆分、去空白并保持首次出现顺序', () => {
    expect(parseIpAllowlist('10.0.0.0/8, 192.168.1.10\n10.0.0.0/8')).toEqual([
      '10.0.0.0/8',
      '192.168.1.10'
    ])
  })

  it('兼容后端返回数组和空值', () => {
    expect(parseIpAllowlist(['2001:db8::/32', '127.0.0.1'])).toEqual([
      '2001:db8::/32',
      '127.0.0.1'
    ])
    expect(parseIpAllowlist(null)).toEqual([])
  })
})
