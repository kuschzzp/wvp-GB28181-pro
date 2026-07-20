export function hasPermi(required, granted) {
  if (!Array.isArray(required) || required.length === 0) {
    return false
  }
  if (!Array.isArray(granted)) {
    return false
  }
  return required.some(permission => granted.includes(permission))
}

export function createAuth(store) {
  return Object.freeze({
    hasPermi(required) {
      return hasPermi(required, store.getters.permissions)
    }
  })
}
