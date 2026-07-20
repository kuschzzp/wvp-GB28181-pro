<template>
  <div class="player-ptz-panel">
    <div v-if="$auth.hasPermi(['video:channel:play'])" class="player-section">
      <div class="player-wrapper" :style="{ height: playerHeight }">
        <playerTabs ref="playerTabs" :has-audio="hasAudio" :show-button="true" />
      </div>
    </div>
    <el-alert
      v-else
      title="当前账号没有实时播放权限"
      type="info"
      :closable="false"
      show-icon
    />
    <devicePtzPanel
      style="margin-top: 5vh"
      :device-id="deviceId"
      :channel-id="channelDeviceId"
      @drag-zoom-start="toggleDragZoom"
    />
  </div>
</template>

<script>
import playerTabs from '../../common/playerTabs.vue'
import devicePtzPanel from './devicePtzPanel.vue'

export default {
  name: 'PlayerPtzPanel',
  components: { playerTabs, devicePtzPanel },
  props: {
    deviceId: { type: String, default: null },
    channelDeviceId: { type: String, default: null }
  },
  data() {
    return {
      hasAudio: false,
      playerHeight: '40vh',
      dragZoomDirection: '',
      streamStarted: false
    }
  },
  mounted() {
    this.startPlay()
  },
  beforeDestroy() {
    this.stopPlay()
  },
  methods: {
    startPlay() {
      if (!this.$auth.hasPermi(['video:channel:play'])) return
      this.$store.dispatch('play/play', [this.deviceId, this.channelDeviceId])
        .then(data => {
          this.streamStarted = true
          this.hasAudio = data.hasAudio
          this.$nextTick(() => {
            if (this.$refs.playerTabs) {
              this.$refs.playerTabs.setStreamInfo((data.transcodeStream || data).playable)
            }
          })
        })
        .catch(e => {
          this.$message({ showClose: true, message: e || '播放失败', type: 'error' })
        })
    },
    stopPlay() {
      if (!this.streamStarted) return
      this.$store.dispatch('play/stop', { deviceId: this.deviceId, channelId: this.channelDeviceId })
        .then(() => {
          this.streamStarted = false
        })
        .catch(() => {
          this.streamStarted = false
        })
    },
    toggleDragZoom(direction) {
      if (!this.$auth.hasPermi(['video:ptz:control'])) return
      if (!this.$refs.playerTabs) return
      this.dragZoomDirection = direction
      this.$refs.playerTabs.startDragZoom((params) => {
        params.deviceId = this.deviceId
        params.channelId = this.channelDeviceId
        const action = this.dragZoomDirection === 'in' ? 'frontEnd/dragZoomIn' : 'frontEnd/dragZoomOut'
        const successMsg = this.dragZoomDirection === 'in' ? '拉框放大成功' : '拉框缩小成功'
        const failMsg = this.dragZoomDirection === 'in' ? '拉框放大失败' : '拉框缩小失败'
        this.$store.dispatch(action, params).then(() => {
          this.$message({ showClose: true, message: successMsg, type: 'success' })
        }).catch(() => {
          this.$message({ showClose: true, message: failMsg, type: 'error' })
        })
        this.dragZoomDirection = ''
      })
    },
  }
}
</script>

<style scoped>
.player-ptz-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.player-section {
  flex: 0.8;
}
.player-wrapper {
  position: relative;
  width: 100%;
}
</style>
