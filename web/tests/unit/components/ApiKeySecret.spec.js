import { shallowMount } from '@vue/test-utils'
import ApiKeySecret from '@/views/user/dialog/apiKeySecret.vue'

describe('ApiKeySecret.vue', () => {
  const createWrapper = (copyText = jest.fn().mockResolvedValue()) => {
    const message = jest.fn()
    return {
      message,
      copyText,
      wrapper: shallowMount(ApiKeySecret, {
        mocks: {
          $copyText: copyText,
          $message: message
        },
        stubs: ['el-dialog', 'el-alert', 'el-input', 'el-button']
      })
    }
  }

  it('只在创建响应包含 secret 时打开', () => {
    const { wrapper, message } = createWrapper()

    expect(wrapper.vm.open({ id: 1 })).toBe(false)
    expect(wrapper.vm.showDialog).toBe(false)
    expect(message).toHaveBeenCalledWith(expect.objectContaining({ type: 'error' }))

    expect(wrapper.vm.open({ id: 1, secret: 'wvp_once-only' })).toBe(true)
    expect(wrapper.vm.showDialog).toBe(true)
    expect(wrapper.vm.secret).toBe('wvp_once-only')
  })

  it('关闭时立即清空 secret', () => {
    const { wrapper } = createWrapper()
    wrapper.vm.open({ secret: 'wvp_once-only' })

    wrapper.vm.close()

    expect(wrapper.vm.showDialog).toBe(false)
    expect(wrapper.vm.secret).toBe('')
  })

  it('复制成功后不持久化 secret', async() => {
    const { wrapper, copyText, message } = createWrapper()
    wrapper.vm.open({ secret: 'wvp_once-only' })

    await wrapper.vm.copySecret()

    expect(copyText).toHaveBeenCalledWith('wvp_once-only')
    expect(message).toHaveBeenCalledWith(expect.objectContaining({ type: 'success' }))
    expect(wrapper.vm.secret).toBe('wvp_once-only')
    expect(wrapper.vm.copying).toBe(false)
  })
})
