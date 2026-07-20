import {
  add,
  disable,
  enable,
  queryList,
  remark,
  remove,
  reset,
  updateScope
} from '@/api/userApiKey'

const actions = {
  remark(context, params) {
    return new Promise((resolve, reject) => {
      remark(params).then(response => {
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
  enable(context, id) {
    return new Promise((resolve, reject) => {
      enable(id).then(response => {
        const { data } = response
        resolve(data)
      }).catch(error => {
        reject(error)
      })
    })
  },
  disable(context, id) {
    return new Promise((resolve, reject) => {
      disable(id).then(response => {
        const { data } = response
        resolve(data)
      }).catch(error => {
        reject(error)
      })
    })
  },
  reset(context, id) {
    return new Promise((resolve, reject) => {
      reset(id).then(response => {
        const { data } = response
        resolve(data)
      }).catch(error => {
        reject(error)
      })
    })
  },
  remove(context, id) {
    return new Promise((resolve, reject) => {
      remove(id).then(response => {
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
  updateScope(context, params) {
    return new Promise((resolve, reject) => {
      updateScope(params).then(response => {
        const { data } = response
        resolve(data)
      }).catch(error => {
        reject(error)
      })
    })
  }
}

export default {
  namespaced: true,
  actions
}
