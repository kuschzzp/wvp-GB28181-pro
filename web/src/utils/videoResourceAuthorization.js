export const BINDABLE_VIDEO_RESOURCE_TYPES = Object.freeze([
  'GB_DEVICE',
  'CHANNEL',
  'JT_TERMINAL',
  'JT_CHANNEL',
  'STREAM_PROXY',
  'STREAM_PUSH'
])

const bindableTypes = new Set(BINDABLE_VIDEO_RESOURCE_TYPES)

export function resourceItemKey(item) {
  if (!item) return ''
  const resourceType = item.resourceType || item.type
  const resourceId = Number(item.resourceId || item.id || 0)
  if (!bindableTypes.has(resourceType) || resourceId <= 0) return ''
  return `${resourceType}:${resourceId}`
}

export function normalizeResourceItems(items) {
  const normalized = new Map()
  ;(Array.isArray(items) ? items : []).forEach(item => {
    const key = resourceItemKey(item)
    if (!key) return
    const resourceType = item.resourceType || item.type
    const resourceId = Number(item.resourceId || item.id)
    const previous = normalized.get(key) || {}
    normalized.set(key, {
      ...previous,
      ...item,
      resourceType,
      resourceId,
      label: item.label || previous.label || '',
      secondary: item.secondary || previous.secondary || ''
    })
  })
  return [...normalized.values()]
}

export function mergeResourceItems(currentItems, incomingItems) {
  return normalizeResourceItems([
    ...(Array.isArray(currentItems) ? currentItems : []),
    ...(Array.isArray(incomingItems) ? incomingItems : [])
  ])
}

export function serializeResourceItems(items) {
  return normalizeResourceItems(items).map(item => ({
    type: item.resourceType,
    id: item.resourceId
  }))
}

export function collectInheritedResourceGroupIds(groups, checkedKeys) {
  const explicit = new Set((Array.isArray(checkedKeys) ? checkedKeys : [])
    .map(Number)
    .filter(id => id > 0))
  const inherited = new Set()

  const visit = (nodes, parentSelected) => {
    (Array.isArray(nodes) ? nodes : []).forEach(node => {
      const id = Number(node.id)
      const selected = explicit.has(id)
      if (parentSelected && !selected) inherited.add(id)
      visit(node.children, parentSelected || selected)
    })
  }
  visit(groups, false)
  return inherited
}
