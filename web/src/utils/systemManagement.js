export function unwrapResponseData(response, fallback = null) {
  if (response === null || response === undefined) return fallback
  return Object.prototype.hasOwnProperty.call(response, 'data')
    ? response.data
    : response
}

function compareTreeNode(left, right) {
  const orderDifference = Number(left.orderNum || 0) - Number(right.orderNum || 0)
  if (orderDifference !== 0) return orderDifference
  return Number(left.id || 0) - Number(right.id || 0)
}

function sortTree(nodes) {
  nodes.sort(compareTreeNode)
  nodes.forEach(node => sortTree(node.children))
  return nodes
}

export function buildManagementTree(items) {
  if (!Array.isArray(items)) return []
  const nodes = new Map()
  items.forEach(item => {
    if (item && item.id !== null && item.id !== undefined) {
      nodes.set(item.id, { ...item, children: [] })
    }
  })

  const roots = []
  nodes.forEach(node => {
    const parent = nodes.get(node.parentId)
    if (parent && parent.id !== node.id) {
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  })
  return sortTree(roots)
}

export function flattenManagementTree(nodes, depth = 0, result = []) {
  if (!Array.isArray(nodes)) return result
  nodes.forEach(node => {
    result.push({ ...node, treeDepth: depth })
    flattenManagementTree(node.children, depth + 1, result)
  })
  return result
}

function addSubtreeIds(node, result) {
  result.add(node.id)
  if (Array.isArray(node.children)) {
    node.children.forEach(child => addSubtreeIds(child, result))
  }
}

export function collectSubtreeIds(nodes, targetId) {
  const result = new Set()
  const visit = list => {
    for (const node of list || []) {
      if (node.id === targetId) {
        addSubtreeIds(node, result)
        return true
      }
      if (visit(node.children)) return true
    }
    return false
  }
  visit(nodes)
  return result
}
