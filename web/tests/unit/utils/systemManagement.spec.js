import {
  buildManagementTree,
  collectSubtreeIds,
  flattenManagementTree,
  unwrapResponseData
} from '@/utils/systemManagement'

describe('system management helpers', () => {
  it('兼容 WVPResult 和直接响应两种数据格式', () => {
    expect(unwrapResponseData({ code: 0, data: [1, 2] })).toEqual([1, 2])
    expect(unwrapResponseData([3, 4])).toEqual([3, 4])
    expect(unwrapResponseData(null, [])).toEqual([])
  })

  it('将菜单平铺数据稳定转换为树并按顺序号排序', () => {
    const tree = buildManagementTree([
      { id: 12, parentId: 10, orderNum: 2, menuName: 'B' },
      { id: 10, parentId: 0, orderNum: 2, menuName: 'Root B' },
      { id: 11, parentId: 10, orderNum: 1, menuName: 'A' },
      { id: 5, parentId: 0, orderNum: 1, menuName: 'Root A' }
    ])

    expect(tree.map(item => item.id)).toEqual([5, 10])
    expect(tree[1].children.map(item => item.id)).toEqual([11, 12])
  })

  it('可展开树并收集指定节点的整棵子树', () => {
    const tree = [{
      id: 1,
      children: [{ id: 2, children: [{ id: 3, children: [] }] }]
    }]

    expect(flattenManagementTree(tree).map(item => [item.id, item.treeDepth]))
      .toEqual([[1, 0], [2, 1], [3, 2]])
    expect(Array.from(collectSubtreeIds(tree, 2))).toEqual([2, 3])
  })
})
