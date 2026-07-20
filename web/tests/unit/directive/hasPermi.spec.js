import { createHasPermiDirective } from '@/directive/permission/hasPermi'
import { hasPermi } from '@/utils/permission'

describe('v-hasPermi', () => {
  it('任一权限匹配即允许，空权限声明默认拒绝', () => {
    const granted = ['video:channel:list', 'video:channel:play']

    expect(hasPermi(['video:channel:play'], granted)).toBe(true)
    expect(hasPermi(['video:channel:remove'], granted)).toBe(false)
    expect(hasPermi([], granted)).toBe(false)
    expect(hasPermi('video:channel:play', granted)).toBe(false)
  })

  it('无权限时从 DOM 移除元素', () => {
    const parent = document.createElement('div')
    const button = document.createElement('button')
    parent.appendChild(button)
    const directive = createHasPermiDirective(() => ['video:channel:list'])

    directive.inserted(button, { value: ['video:channel:remove'] })

    expect(parent.contains(button)).toBe(false)
  })

  it('有权限时保留元素', () => {
    const parent = document.createElement('div')
    const button = document.createElement('button')
    parent.appendChild(button)
    const directive = createHasPermiDirective(() => ['video:channel:play'])

    directive.inserted(button, { value: ['video:channel:play'] })

    expect(parent.contains(button)).toBe(true)
  })
})
