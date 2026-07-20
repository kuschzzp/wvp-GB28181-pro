import { getRouters } from '@/api/menu'
import { constantRoutes } from '@/router'
import { hasPermi } from '@/utils/permission'

export const componentMap = Object.freeze({
  Layout: () => import('@/layout'),
  'dashboard/index': () => import('@/views/dashboard/index'),
  'live/index': () => import('@/views/live/index'),
  'device/index': () => import('@/views/device/index'),
  'channel/index': () => import('@/views/channel/index'),
  'channel/record': () => import('@/views/channel/record'),
  'device/channel/record': () => import('@/views/device/channel/record'),
  'jtDevice/channel/record': () => import('@/views/jtDevice/channel/record'),
  'channel/region/index': () => import('@/views/channel/region/index'),
  'channel/group/index': () => import('@/views/channel/group/index'),
  'cloudRecord/index': () => import('@/views/cloudRecord/index'),
  'cloudRecord/detail': () => import('@/views/cloudRecord/detail'),
  'cloud-record/index': () => import('@/views/cloudRecord/index'),
  'alarm/index': () => import('@/views/alarm/index'),
  'map/index': () => import('@/views/map/index'),
  'position/index': () => import('@/views/map/index'),
  'streamProxy/index': () => import('@/views/streamProxy/index'),
  'proxy/index': () => import('@/views/streamProxy/index'),
  'streamPush/index': () => import('@/views/streamPush/index'),
  'push/index': () => import('@/views/streamPush/index'),
  'jtDevice/index': () => import('@/views/jtDevice/index'),
  'jt1078/index': () => import('@/views/jtDevice/index'),
  'recordPlan/index': () => import('@/views/recordPlan/index'),
  'record-plan/index': () => import('@/views/recordPlan/index'),
  'mediaServer/index': () => import('@/views/mediaServer/index'),
  'media-server/index': () => import('@/views/mediaServer/index'),
  'platform/index': () => import('@/views/platform/index'),
  'user/index': () => import('@/views/user/index'),
  'system/user/index': () => import('@/views/system/user/index'),
  'system/role/index': () => import('@/views/system/role/index'),
  'system/dept/index': () => import('@/views/system/dept/index'),
  'system/menu/index': () => import('@/views/system/menu/index'),
  'system/resource-group/index': () => import('@/views/system/resourceGroup/index'),
  'operations/systemInfo': () => import('@/views/operations/systemInfo'),
  'operations/historyLog': () => import('@/views/operations/historyLog'),
  'operations/realLog': () => import('@/views/operations/realLog'),
  'operations/real-log/index': () => import('@/views/operations/realLog'),
  'operations/system/index': () => import('@/views/operations/systemInfo'),
  'operations/log/index': () => import('@/views/operations/historyLog')
})

export const notFoundRoute = Object.freeze({
  path: '*',
  redirect: '/404',
  hidden: true
})

const localPermissionRoutes = Object.freeze([
  {
    path: '/channel',
    component: componentMap.Layout,
    hidden: true,
    children: [{
      path: 'record/:channelId',
      name: 'CommonRecord',
      component: componentMap['channel/record'],
      hidden: true,
      meta: { title: '设备录像', permissions: ['video:record:list'] }
    }]
  },
  {
    path: '/device',
    component: componentMap.Layout,
    hidden: true,
    children: [{
      path: 'record/:deviceId/:channelDeviceId',
      name: 'DeviceRecord',
      component: componentMap['device/channel/record'],
      hidden: true,
      meta: { title: '国标录像', permissions: ['video:record:list'] }
    }]
  },
  {
    path: '/jtDevice',
    component: componentMap.Layout,
    hidden: true,
    children: [{
      path: 'record/:phoneNumber/:channelId',
      name: 'JTDeviceRecord',
      component: componentMap['jtDevice/channel/record'],
      hidden: true,
      meta: { title: '部标录像', permissions: ['video:jt:record'] }
    }]
  },
  {
    path: '/cloudRecord',
    component: componentMap.Layout,
    hidden: true,
    children: [{
      path: 'detail/:app/:stream',
      name: 'CloudRecordDetail',
      component: componentMap['cloudRecord/detail'],
      hidden: true,
      meta: { title: '云端录像详情', permissions: ['video:cloud-record:list'] }
    }]
  }
])

function cloneMeta(meta) {
  return meta ? { ...meta } : {}
}

function resolvePath(parentPath, childPath) {
  if (!childPath) return parentPath || '/'
  if (childPath.startsWith('/')) return childPath
  const parent = parentPath && parentPath !== '/' ? parentPath.replace(/\/$/, '') : ''
  return `${parent}/${childPath}`.replace(/\/+/g, '/')
}

function firstLeafPath(routes, parentPath = '') {
  for (const route of routes) {
    if (route.hidden) continue
    const currentPath = resolvePath(parentPath, route.path)
    if (Array.isArray(route.children) && route.children.length > 0) {
      const childPath = firstLeafPath(route.children, currentPath)
      if (childPath) return childPath
    } else if (route.path !== '*') {
      return currentPath
    }
  }
  return null
}

export function filterAsyncRoutes(routes) {
  if (!Array.isArray(routes)) return []

  return routes.map(route => {
    const component = componentMap[route.component]
    if (!component) {
      throw new Error(`未知路由组件: ${route.component}`)
    }

    const children = filterAsyncRoutes(route.children || [])
    const resolved = {
      path: route.path,
      name: route.name,
      component,
      meta: cloneMeta(route.meta),
      children
    }
    if (route.hidden) resolved.hidden = true
    if (route.alwaysShow) resolved.alwaysShow = true
    if (route.redirect) resolved.redirect = route.redirect
    if (!resolved.redirect && route.component === 'Layout' && children.length > 0) {
      resolved.redirect = firstLeafPath(children, route.path)
    }
    return resolved
  })
}

function filterLocalRoutes(routes, permissions) {
  return routes
    .map(route => {
      const children = (route.children || []).filter(child => {
        const required = child.meta && child.meta.permissions
        return hasPermi(required, permissions)
      })
      return { ...route, children }
    })
    .filter(route => route.children.length > 0)
}

function unwrapRoutes(response) {
  const data = response && Object.prototype.hasOwnProperty.call(response, 'data')
    ? response.data
    : response
  if (!Array.isArray(data)) {
    throw new Error('动态路由响应格式错误')
  }
  return data
}

const getDefaultState = () => ({
  routes: constantRoutes,
  addRoutes: [],
  routesLoaded: false
})

const state = getDefaultState()

const mutations = {
  SET_ROUTES(state, routes) {
    state.addRoutes = routes
    state.routes = constantRoutes.concat(routes)
    state.routesLoaded = true
  },
  RESET_ROUTES(state) {
    Object.assign(state, getDefaultState())
  }
}

const actions = {
  generateRoutes({ commit, rootGetters }) {
    return getRouters().then(response => {
      const remoteRoutes = filterAsyncRoutes(unwrapRoutes(response))
      const localRoutes = filterLocalRoutes(localPermissionRoutes, rootGetters.permissions)
      const firstPath = firstLeafPath(remoteRoutes)
      const homeRoute = firstPath
        ? [{ path: '/', redirect: firstPath, hidden: true }]
        : [{ path: '/', redirect: '/403', hidden: true }]
      const runtimeRoutes = homeRoute.concat(remoteRoutes, localRoutes, [notFoundRoute])
      commit('SET_ROUTES', runtimeRoutes)
      return runtimeRoutes
    })
  },
  resetRoutes({ commit }) {
    commit('RESET_ROUTES')
    return Promise.resolve()
  }
}

export default {
  namespaced: true,
  state,
  mutations,
  actions
}
