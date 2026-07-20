jest.mock('@/api/menu', () => ({
  getRouters: jest.fn()
}))

import permissionModule, {
  componentMap,
  filterAsyncRoutes,
  notFoundRoute
} from '@/store/modules/permission'
import { getRouters } from '@/api/menu'

describe('permission store', () => {
  it('显式注册完整的系统管理页面组件', () => {
    expect(componentMap['system/user/index']).toBeDefined()
    expect(componentMap['system/role/index']).toBeDefined()
    expect(componentMap['system/dept/index']).toBeDefined()
    expect(componentMap['system/menu/index']).toBeDefined()
  })

  it('只解析显式注册的路由组件', () => {
    const routes = filterAsyncRoutes([{
      name: 'VideoDevice',
      path: '/device',
      component: 'device/index',
      children: []
    }])

    expect(routes[0].component).toBe(componentMap['device/index'])
    expect(() => filterAsyncRoutes([{
      path: '/evil',
      component: '../../evil'
    }])).toThrow('未知路由组件')
  })

  it('递归解析子路由且不修改后端原始响应', () => {
    const backendRoutes = [{
      name: 'Video',
      path: '/video',
      component: 'Layout',
      children: [{
        name: 'VideoChannel',
        path: '/channel',
        component: 'channel/index',
        children: []
      }]
    }]

    const routes = filterAsyncRoutes(backendRoutes)

    expect(routes[0].component).toBe(componentMap.Layout)
    expect(routes[0].children[0].component).toBe(componentMap['channel/index'])
    expect(backendRoutes[0].component).toBe('Layout')
  })

  it('重置时清空动态路由，404 通配路由始终位于最后', () => {
    const state = {
      routes: [{ path: '/old' }],
      addRoutes: [{ path: '/old' }],
      routesLoaded: true
    }

    const resetRoutes = permissionModule.mutations.RESET_ROUTES
    resetRoutes(state)

    expect(state.addRoutes).toEqual([])
    expect(state.routesLoaded).toBe(false)

    const setRoutes = permissionModule.mutations.SET_ROUTES
    setRoutes(state, [
      { path: '/device' },
      notFoundRoute
    ])
    expect(state.routes[state.routes.length - 1].path).toBe('*')
  })

  it('根据后端菜单生成首页重定向，并把 404 通配路由最后安装', async() => {
    getRouters.mockResolvedValue([{
      name: 'Video',
      path: '/video',
      component: 'Layout',
      children: [{
        name: 'Device',
        path: '/device',
        component: 'device/index',
        children: []
      }]
    }])
    const commit = jest.fn()

    const routes = await permissionModule.actions.generateRoutes({
      commit,
      rootGetters: { permissions: ['video:device:list'] }
    })

    expect(routes[0]).toEqual({ path: '/', redirect: '/device', hidden: true })
    expect(routes[routes.length - 1]).toBe(notFoundRoute)
    expect(commit).toHaveBeenCalledWith('SET_ROUTES', routes)
  })
})
