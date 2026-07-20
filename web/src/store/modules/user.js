import {
  add,
  changePassword,
  changePasswordForAdmin,
  changePushKey,
  getProfile,
  login,
  logout,
  queryList,
  removeById
} from '@/api/user'
import {
  getToken,
  setToken,
  setName,
  removeToken,
  removeName,
  setServerId,
  removeServerId
} from '@/utils/auth'
import { resetRouter } from '@/router'

const getDefaultState = () => {
  return {
    token: getToken(),
    name: '',
    serverId: '',
    user: null,
    roles: [],
    permissions: [],
    authzVersion: 0,
    systemUser: false,
    showConfirmBoxForLoginLose: true
  }
}

const state = getDefaultState()

const mutations = {
  RESET_STATE: (state) => {
    Object.assign(state, getDefaultState())
  },
  SET_TOKEN: (state, token) => {
    state.token = token
  },
  SET_NAME: (state, name) => {
    state.name = name
  },
  SET_SERVER_ID: (state, serverId) => {
    state.serverId = serverId
  },
  SET_PROFILE: (state, profile) => {
    state.user = profile.user
    state.name = profile.user.nickname || profile.user.username
    state.roles = Array.isArray(profile.roles) ? profile.roles : []
    state.permissions = Array.isArray(profile.permissions) ? profile.permissions : []
    state.authzVersion = profile.authzVersion
    state.systemUser = Boolean(profile.systemUser)
  },
  SET_CONFIRM_BOX: (state, status) => {
    state.showConfirmBoxForLoginLose = status
  }
}

const actions = {
  // user login
  login({ commit }, userInfo) {
    const { username, password } = userInfo
    return new Promise((resolve, reject) => {
      login({
        username: username.trim(),
        password
      }).then(response => {
        const { data } = response
        commit('SET_TOKEN', data.accessToken)
        commit('SET_NAME', username.trim())
        commit('SET_SERVER_ID', data.serverId)
        commit('SET_CONFIRM_BOX', true)
        setToken(data.accessToken)
        setName(username.trim())
        setServerId(data.serverId)
        resolve()
      }).catch(error => {
        reject(error)
      })
    })
  },
  // user logout
  logout({ commit, dispatch }) {
    return new Promise((resolve, reject) => {
      logout().then(() => {
        removeToken()
        removeServerId()
        removeName()
        resetRouter()
        commit('RESET_STATE')
        Promise.all([
          dispatch('permission/resetRoutes', null, { root: true }),
          dispatch('tagsView/delAllViews', null, { root: true })
        ]).then(resolve).catch(reject)
      }).catch(error => {
        reject(error)
      })
    })
  },

  // remove token
  resetToken({ commit, dispatch }) {
    return new Promise((resolve, reject) => {
      removeToken() // must remove  token  first
      removeServerId()
      removeName()
      resetRouter()
      commit('RESET_STATE')
      Promise.all([
        dispatch('permission/resetRoutes', null, { root: true }),
        dispatch('tagsView/delAllViews', null, { root: true })
      ]).then(resolve).catch(reject)
    })
  },

  getInfo({ commit }) {
    return new Promise((resolve, reject) => {
      getProfile().then(response => {
        const profile = response && Object.prototype.hasOwnProperty.call(response, 'data')
          ? response.data
          : response
        if (!profile || !profile.user) {
          reject(new Error('用户权限信息格式错误'))
          return
        }
        commit('SET_PROFILE', profile)
        setName(profile.user.username)
        resolve(profile)
      }).catch(reject)
    })
  },

  changePushKey(context, params) {
    return new Promise((resolve, reject) => {
      changePushKey(params).then(response => {
        const { data } = response
        resolve(data)
      }).catch(error => {
        reject(error)
      })
    })
  },

  queryList(context, params) {
    return new Promise((resolve, reject) => {
      queryList(params).then(response => {
        const { data } = response
        resolve(data)
      }).catch(error => {
        reject(error)
      })
    })
  },

  removeById(context, id) {
    return new Promise((resolve, reject) => {
      removeById(id).then(response => {
        const { data } = response
        resolve(data)
      }).catch(error => {
        reject(error)
      })
    })
  },

  add(context, params) {
    return new Promise((resolve, reject) => {
      add(params).then(response => {
        const { data } = response
        resolve(data)
      }).catch(error => {
        reject(error)
      })
    })
  },

  changePassword(context, params) {
    return new Promise((resolve, reject) => {
      changePassword(params).then(response => {
        const { data } = response
        resolve(data)
      }).catch(error => {
        reject(error)
      })
    })
  },

  changePasswordForAdmin(context, params) {
    return new Promise((resolve, reject) => {
      changePasswordForAdmin(params).then(response => {
        const { data } = response
        resolve(data)
      }).catch(error => {
        reject(error)
      })
    })
  },
  closeConfirmBoxForLoginLose({ commit }) {
    commit('SET_CONFIRM_BOX', false)
  }
}

export default {
  namespaced: true,
  state,
  mutations,
  actions
}
