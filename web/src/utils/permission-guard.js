function responseStatus(error) {
  return error && error.response ? error.response.status : undefined
}

function normalizePath(path) {
  const cleanPath = (path || '/').split('?')[0].split('#')[0]
  const withLeadingSlash = cleanPath.startsWith('/') ? cleanPath : `/${cleanPath}`
  const normalized = withLeadingSlash.replace(/\/+/g, '/')
  return normalized.length > 1 ? normalized.replace(/\/$/, '') : normalized
}

function resolvePath(parentPath, childPath) {
  if (!childPath) return normalizePath(parentPath)
  if (childPath.startsWith('/')) return normalizePath(childPath)
  const parent = parentPath && parentPath !== '/' ? parentPath.replace(/\/$/, '') : ''
  return normalizePath(`${parent}/${childPath}`)
}

function routePathToRegExp(routePath) {
  const escaped = routePath.replace(/[.+?^${}()|[\]\\]/g, '\\$&')
  const paramPattern = escaped.replace(/\/:[^/]+/g, '/[^/]+')
  return new RegExp(`^${paramPattern}/?$`)
}

function routePathMatches(routePath, targetPath) {
  if (!routePath || routePath === '*') return false
  const normalizedRoutePath = normalizePath(routePath)
  return routePathToRegExp(normalizedRoutePath).test(normalizePath(targetPath))
}

export function canAccessRoutePath(routes, targetPath, parentPath = '') {
  if (!Array.isArray(routes)) return false

  return routes.some(route => {
    if (!route || route.path === '*') return false
    const currentPath = resolvePath(parentPath, route.path)
    if (routePathMatches(currentPath, targetPath)) return true
    return canAccessRoutePath(route.children, targetPath, currentPath)
  })
}

export function isPublicRoute(route) {
  if (!route) return false
  if (route.path === '/login') return true
  return Array.isArray(route.matched)
    ? route.matched.some(record => record.meta && record.meta.public)
    : Boolean(route.meta && route.meta.public)
}

export function createRouteLoader(store, router) {
  let loadingPromise = null

  return function loadAuthorizedRoutes() {
    if (store.getters.routesLoaded) {
      return Promise.resolve({ installed: false })
    }
    if (loadingPromise) {
      return loadingPromise
    }

    loadingPromise = store.dispatch('user/getInfo')
      .then(() => store.dispatch('permission/generateRoutes'))
      .then(routes => {
        router.addRoutes(routes)
        router.options.routes = store.getters.permission_routes
        return { installed: true, routes }
      })
      .finally(() => {
        loadingPromise = null
      })

    return loadingPromise
  }
}

export function createPermissionGuard({
  store,
  tokenProvider,
  routeLoader
}) {
  return async function permissionGuard(to, from, next) {
    const token = tokenProvider()

    if (to.path === '/login' && token) {
      next({ path: '/' })
      return
    }

    // 公共分享页不能被本地残留或已失效的登录令牌阻断。
    if (isPublicRoute(to)) {
      next()
      return
    }

    if (!token) {
      next(`/login?redirect=${encodeURIComponent(to.fullPath || to.path)}`)
      return
    }

    try {
      const result = await routeLoader()
      if (result.installed) {
        const routes = result.routes || store.getters.addRoutes
        const shouldCheckTarget = to.path !== '/403' && to.path !== '/404'
        if (shouldCheckTarget && Array.isArray(routes) && routes.length > 0 && !canAccessRoutePath(routes, to.path)) {
          next({ path: '/', replace: true })
          return
        }
        next({ ...to, replace: true })
      } else {
        next()
      }
    } catch (error) {
      if (responseStatus(error) === 403) {
        if (to.path === '/403') {
          next()
        } else {
          next('/403')
        }
        return
      }

      await store.dispatch('user/resetToken')
      next(`/login?redirect=${encodeURIComponent(to.fullPath || to.path)}`)
    }
  }
}
