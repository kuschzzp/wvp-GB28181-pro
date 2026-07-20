import fs from 'fs'
import path from 'path'

const projectRoot = path.resolve(__dirname, '../../..')

function source(relativePath) {
  return fs.readFileSync(path.join(projectRoot, relativePath), 'utf8')
}

describe('video business action permissions', () => {
  const permissionCoverage = {
    'src/views/device/list.vue': [
      'video:device:add',
      'video:device:edit',
      'video:device:remove',
      'video:device:sync',
      'video:device:config'
    ],
    'src/views/device/channel/index.vue': [
      'video:channel:play',
      'video:channel:stop',
      'video:channel:talk',
      'video:ptz:control',
      'video:ptz:preset',
      'video:ptz:cruise',
      'video:record:list'
    ],
    'src/views/channel/index.vue': [
      'video:channel:play',
      'video:channel:stop',
      'video:channel:talk',
      'video:ptz:control',
      'video:ptz:preset',
      'video:ptz:cruise',
      'video:record:list'
    ],
    'src/views/live/index.vue': [
      'video:channel:play',
      'video:ptz:control',
      'video:ptz:preset'
    ],
    'src/views/jtDevice/list.vue': [
      'video:jt:edit',
      'video:jt:control'
    ],
    'src/views/jtDevice/channel/index.vue': [
      'video:jt:play',
      'video:jt:talk',
      'video:jt:ptz',
      'video:jt:record'
    ],
    'src/views/jtDevice/channel/record.vue': [
      'video:jt:record'
    ],
    'src/views/jtDevice/dialog/queryMediaList.vue': [
      'video:jt:list',
      'video:jt:record'
    ],
    'src/views/jtDevice/dialog/jtDevicePlayer.vue': [
      'video:jt:play',
      'video:jt:talk',
      'video:jt:ptz'
    ],
    'src/views/streamProxy/index.vue': [
      'video:proxy:add',
      'video:proxy:edit',
      'video:proxy:play',
      'video:proxy:stop',
      'video:proxy:remove'
    ],
    'src/views/streamPush/index.vue': [
      'video:push:add',
      'video:push:edit',
      'video:push:play',
      'video:push:stop',
      'video:push:remove'
    ],
    'src/views/cloudRecord/index.vue': [
      'video:cloud-record:play',
      'video:cloud-record:download',
      'video:cloud-record:remove'
    ],
    'src/views/cloudRecord/detail.vue': [
      'video:cloud-record:play',
      'video:cloud-record:download'
    ],
    'src/views/channel/record.vue': [
      'video:record:play',
      'video:record:download'
    ],
    'src/views/device/channel/record.vue': [
      'video:record:play',
      'video:record:download'
    ],
    'src/views/alarm/index.vue': [
      'video:record:play',
      'video:alarm:remove',
      'video:alarm:clear'
    ],
    'src/views/common/streamMediaPanel.vue': [
      'video:stream:share'
    ],
    'src/views/device/dialog/devicePlayer.vue': [
      'video:channel:play',
      'video:ptz:control',
      'video:ptz:preset'
    ],
    'src/views/channel/player.vue': [
      'video:channel:play',
      'video:ptz:control',
      'video:ptz:preset'
    ],
    'src/views/device/common/devicePtzPanel.vue': [
      'video:ptz:control'
    ],
    'src/views/channel/common/channelPtzPanel.vue': [
      'video:ptz:control'
    ],
    'src/views/device/common/playerPtzPanel.vue': [
      'video:channel:play',
      'video:ptz:control'
    ],
    'src/views/channel/common/playerPtzPanel.vue': [
      'video:channel:play',
      'video:ptz:control'
    ],
    'src/views/device/channel/ptzConfig.vue': [
      'video:ptz:control',
      'video:ptz:preset',
      'video:ptz:cruise'
    ],
    'src/views/channel/ptzConfig.vue': [
      'video:ptz:control',
      'video:ptz:preset',
      'video:ptz:cruise'
    ]
  }

  Object.entries(permissionCoverage).forEach(([file, permissions]) => {
    it(`${file} 声明全部业务动作权限`, () => {
      const content = source(file)
      permissions.forEach(permission => expect(content).toContain(permission))
    })
  })

  it('动态播放和分享路径使用 $auth 做运行时权限判断', () => {
    expect(source('src/views/live/index.vue')).toMatch(
      /\$auth\.hasPermi\(\['video:channel:play'\]\)/
    )
    const sharePanel = source('src/views/common/streamMediaPanel.vue')
    expect(sharePanel).toMatch(
      /canCreateShare\(\)\s*{\s*return this\.\$auth\.hasPermi\(\['video:stream:share'\]\)/
    )
    expect(sharePanel).toMatch(
      /if \(!this\.canCreateShare \|\| !this\.validPlayableRef \|\| this\.creating\) return/
    )
  })

  it('播放器 PTZ、JT 录像和多媒体下载同时具备展示与运行时门禁', () => {
    expect(source('src/views/device/common/devicePtzPanel.vue')).toMatch(
      /if \(!this\.\$auth\.hasPermi\(\['video:ptz:control'\]\)\) return/
    )
    expect(source('src/views/channel/common/channelPtzPanel.vue')).toMatch(
      /if \(!this\.\$auth\.hasPermi\(\['video:ptz:control'\]\)\) return/
    )
    expect(source('src/views/jtDevice/channel/record.vue')).toMatch(
      /if \(!this\.\$auth\.hasPermi\(\['video:jt:record'\]\)\) return/
    )
    expect(source('src/views/jtDevice/dialog/queryMediaList.vue')).toMatch(
      /if \(!this\.\$auth\.hasPermi\(\['video:jt:record'\]\)\) return/
    )
  })
})
