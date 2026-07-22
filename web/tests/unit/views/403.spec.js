import { shallowMount } from '@vue/test-utils'
import Page403 from '@/views/403.vue'

function mountPage403(dispatch = jest.fn(() => Promise.resolve())) {
  const replace = jest.fn()
  const wrapper = shallowMount(Page403, {
    stubs: {
      'el-button': {
        template: '<button :disabled="loading" @click="$emit(\'click\')"><slot /></button>',
        props: ['loading']
      }
    },
    mocks: {
      $router: { replace },
      $store: { dispatch }
    }
  })

  return { wrapper, dispatch, replace }
}

describe('Page403.vue', () => {
  it('返回首页时跳转到授权首页入口', () => {
    const { wrapper, replace } = mountPage403()

    wrapper.vm.goHome()

    expect(replace).toHaveBeenCalledWith('/')
  })

  it('退出登录成功后回到登录页', async() => {
    const { wrapper, dispatch, replace } = mountPage403()

    await wrapper.vm.logout()

    expect(dispatch).toHaveBeenCalledWith('user/logout')
    expect(replace).toHaveBeenCalledWith('/login')
  })

  it('退出接口失败时仍清除本地登录态并回到登录页', async() => {
    const dispatch = jest.fn(action => {
      if (action === 'user/logout') return Promise.reject(new Error('logout failed'))
      return Promise.resolve()
    })
    const { wrapper, replace } = mountPage403(dispatch)

    await wrapper.vm.logout()

    expect(dispatch).toHaveBeenCalledWith('user/logout')
    expect(dispatch).toHaveBeenCalledWith('user/resetToken')
    expect(replace).toHaveBeenCalledWith('/login')
  })
})
