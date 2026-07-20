jest.mock('@/api/user', () => ({
  add: jest.fn(),
  changePassword: jest.fn(),
  changePasswordForAdmin: jest.fn(),
  changePushKey: jest.fn(),
  getProfile: jest.fn(),
  login: jest.fn(),
  logout: jest.fn(() => Promise.resolve()),
  queryList: jest.fn(),
  removeById: jest.fn()
}))

jest.mock('@/utils/auth', () => ({
  getToken: jest.fn(),
  setToken: jest.fn(),
  setName: jest.fn(),
  removeToken: jest.fn(),
  removeName: jest.fn(),
  setServerId: jest.fn(),
  removeServerId: jest.fn()
}))

jest.mock('@/router', () => ({
  resetRouter: jest.fn()
}))

import {
  login as loginRequest,
  logout as logoutRequest
} from '@/api/user'
import userModule from '@/store/modules/user'
import {
  createPermissionGuard,
  createRouteLoader
} from '@/utils/permission-guard'

describe('permission guard', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  it('登录提交原始密码到 POST JSON API，不再进行前端 MD5', async() => {
    loginRequest.mockResolvedValue({
      data: { accessToken: 'signed-token', serverId: 'wvp-1' }
    })

    await userModule.actions.login(
      { commit: jest.fn() },
      { username: ' operator ', password: 'plain-secret' }
    )

    expect(loginRequest).toHaveBeenCalledWith({
      username: 'operator',
      password: 'plain-secret'
    })
  })

  it('首次加载合并并发请求，只安装一次动态路由', async() => {
    let routesLoaded = false
    const routes = [{ path: '/video' }, { path: '*' }]
    const store = {
      getters: {
        get routesLoaded() {
          return routesLoaded
        },
        permission_routes: routes
      },
      dispatch: jest.fn(type => {
        if (type === 'user/getInfo') return Promise.resolve()
        if (type === 'permission/generateRoutes') {
          routesLoaded = true
          return Promise.resolve(routes)
        }
        return Promise.resolve()
      })
    }
    const router = {
      addRoutes: jest.fn(),
      options: { routes: [] }
    }
    const loadRoutes = createRouteLoader(store, router)

    await Promise.all([loadRoutes(), loadRoutes()])

    expect(store.dispatch).toHaveBeenCalledTimes(2)
    expect(router.addRoutes).toHaveBeenCalledTimes(1)
    expect(router.options.routes).toBe(routes)
  })

  it('刷新后有 token 但无内存路由时恢复路由并重新匹配目标地址', async() => {
    const routeLoader = jest.fn(() => Promise.resolve({ installed: true }))
    const store = { dispatch: jest.fn(), getters: {} }
    const guard = createPermissionGuard({
      store,
      tokenProvider: () => 'signed-token',
      routeLoader
    })
    const next = jest.fn()
    const to = { path: '/device', fullPath: '/device?page=2', meta: {} }

    await guard(to, {}, next)

    expect(routeLoader).toHaveBeenCalledTimes(1)
    expect(next).toHaveBeenCalledWith({ ...to, replace: true })
  })

  it('动态路由接口返回 403 时进入无权限页但不清除登录态', async() => {
    const error = { response: { status: 403 } }
    const store = { dispatch: jest.fn(), getters: {} }
    const guard = createPermissionGuard({
      store,
      tokenProvider: () => 'signed-token',
      routeLoader: () => Promise.reject(error)
    })
    const next = jest.fn()

    await guard({ path: '/system', fullPath: '/system', meta: {} }, {}, next)

    expect(store.dispatch).not.toHaveBeenCalledWith('user/resetToken')
    expect(next).toHaveBeenCalledWith('/403')
  })

  it('公开分享页按路由元数据放行，即使本地还残留登录令牌', async() => {
    const routeLoader = jest.fn()
    const store = { dispatch: jest.fn(), getters: {} }
    const guard = createPermissionGuard({
      store,
      tokenProvider: () => 'stale-token',
      routeLoader
    })
    const next = jest.fn()

    await guard({
      path: '/play/share/abcdefghijklmnopqrstuvwxyzABCDEF',
      fullPath: '/play/share/abcdefghijklmnopqrstuvwxyzABCDEF',
      matched: [{ meta: { public: true } }]
    }, {}, next)

    expect(next).toHaveBeenCalledWith()
    expect(routeLoader).not.toHaveBeenCalled()
  })

  it('登出后同时清空用户、权限路由和标签页状态', async() => {
    const commit = jest.fn()
    const dispatch = jest.fn(() => Promise.resolve())

    await userModule.actions.logout({
      commit,
      dispatch,
      state: { token: 'signed-token' }
    })

    expect(logoutRequest).toHaveBeenCalledTimes(1)
    expect(dispatch).toHaveBeenCalledWith('permission/resetRoutes', null, { root: true })
    expect(dispatch).toHaveBeenCalledWith('tagsView/delAllViews', null, { root: true })
    expect(commit).toHaveBeenCalledWith('RESET_STATE')
  })
})
