jest.mock('@/utils/request', () => jest.fn())

import request from '@/utils/request'
import { add } from '@/api/user'

describe('user api', () => {
  it('新增用户使用 JSON 主接口并提交角色集合', () => {
    add({
      username: 'operator',
      password: 'safe-password',
      roleId: 7
    })

    expect(request).toHaveBeenCalledWith({
      method: 'post',
      url: '/api/user',
      data: {
        username: 'operator',
        password: 'safe-password',
        nickname: 'operator',
        deptId: null,
        roleIds: [7]
      }
    })
  })
})
