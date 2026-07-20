const getters = {
  sidebar: state => state.app.sidebar,
  device: state => state.app.device,
  token: state => state.user.token,
  showConfirmBoxForLoginLose: state => state.user.showConfirmBoxForLoginLose,
  serverId: state => state.user.serverId,
  name: state => state.user.name,
  roles: state => state.user.roles,
  permissions: state => state.user.permissions,
  authzVersion: state => state.user.authzVersion,
  systemUser: state => state.user.systemUser,
  permission_routes: state => state.permission.routes,
  addRoutes: state => state.permission.addRoutes,
  routesLoaded: state => state.permission.routesLoaded,
  visitedViews: state => state.tagsView.visitedViews,
  cachedViews: state => state.tagsView.cachedViews
}
export default getters
