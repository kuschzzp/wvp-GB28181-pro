import UserApiKeyManager from '@/views/user/apiKeyManager.vue'

describe('UserApiKeyManager.vue', () => {
  it('列表只拼接前缀和末四位', () => {
    const row = {
      prefix: 'wvp_abcd1234',
      last4: 'WXYZ'
    }

    expect(UserApiKeyManager.methods.formatApiKey(row)).toBe('wvp_abcd1234••••WXYZ')
    expect(UserApiKeyManager.methods.formatApiKey({})).toBe('-')
  })

  it('展示后端 API Key 状态而不是旧 enable 字段', () => {
    expect(UserApiKeyManager.methods.statusText('ACTIVE')).toBe('启用')
    expect(UserApiKeyManager.methods.statusText('DISABLED')).toBe('停用')
    expect(UserApiKeyManager.methods.statusText('REVOKED')).toBe('已撤销')
    expect(UserApiKeyManager.methods.statusText('LEGACY')).toBe('待轮换')
  })

  it('摘要展示功能权限、视频资源组和 IP 白名单数量', () => {
    const summary = UserApiKeyManager.methods.scopeSummary({
      permissionMenuIds: [1, 2],
      resourceGroupIds: [3],
      ipAllowlist: []
    })

    expect(summary).toBe('功能 2 · 资源组 1 · IP 不限')
  })
})
