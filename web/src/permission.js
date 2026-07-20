import router from './router'
import store from './store'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { getToken } from '@/utils/auth'
import getPageTitle from '@/utils/get-page-title'
import {
  createPermissionGuard,
  createRouteLoader
} from '@/utils/permission-guard'

NProgress.configure({ showSpinner: false })

const routeLoader = createRouteLoader(store, router)
const permissionGuard = createPermissionGuard({
  store,
  tokenProvider: getToken,
  routeLoader
})

router.beforeEach((to, from, next) => {
  NProgress.start()
  document.title = getPageTitle(to.meta.title)
  return permissionGuard(to, from, next)
})

router.afterEach(() => {
  NProgress.done()
})
