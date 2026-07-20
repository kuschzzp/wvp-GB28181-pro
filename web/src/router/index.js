import Vue from 'vue'
import Router from 'vue-router'

Vue.use(Router)

/**
 * 这里只保留无需业务菜单授权的静态路由。
 * 登录后的业务路由由 /api/menu/routers 返回，并通过固定组件白名单安装。
 */
export const constantRoutes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index'),
    hidden: true,
    meta: { public: true }
  },
  {
    path: '/403',
    name: 'Page403',
    component: () => import('@/views/403'),
    hidden: true
  },
  {
    path: '/404',
    name: 'Page404',
    component: () => import('@/views/404'),
    hidden: true
  },
  {
    path: '/play/share/:shareId',
    name: 'sharePlayer',
    component: () => import('@/views/common/share.vue'),
    hidden: true,
    meta: { public: true }
  }
]

const createRouter = () => new Router({
  scrollBehavior: () => ({ y: 0 }),
  routes: constantRoutes
})

const router = createRouter()

// Vue Router 3 没有 removeRoute，登出时通过替换 matcher 清除动态路由。
export function resetRouter() {
  const newRouter = createRouter()
  router.matcher = newRouter.matcher
  router.options.routes = constantRoutes
}

export default router
