function responseStatus(error) {
  return error && error.response ? error.response.status : undefined
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
        return { installed: true }
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
