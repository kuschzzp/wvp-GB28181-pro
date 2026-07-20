<template>
  <div v-if="$auth.hasPermi(['video:ptz:control'])" class="ptz-section">
    <ptzControls
      btn-layout="row"
      @ptz-move="onPtzMove"
      @ptz-stop="onPtzStop"
      @focus-move="onFocusMove"
      @focus-stop="onFocusStop"
      @iris-move="onIrisMove"
      @iris-stop="onIrisStop"
      @toggle-drag-zoom="emitDragZoom('in')"
      @toggle-drag-zoom-out="emitDragZoom('out')"
    />
  </div>
</template>

<script>
import ptzControls from '../../common/ptzControls.vue'

export default {
  name: 'DevicePtzPanel',
  components: { ptzControls },
  props: {
    deviceId: { type: String, default: null },
    channelId: { type: String, default: null }
  },
  methods: {
    ptzSpeed(speed) {
      return parseInt(speed * 255 / 100)
    },
    onPtzMove(e) {
      if (!this.$auth.hasPermi(['video:ptz:control'])) return
      const speedVal = this.ptzSpeed(e.speed)
      this.$store.dispatch('frontEnd/ptz', {
        deviceId: this.deviceId,
        channelId: this.channelId,
        command: e.direction,
        horizonSpeed: speedVal,
        verticalSpeed: speedVal,
        zoomSpeed: parseInt(e.speed * 15 / 100)
      })
    },
    onPtzStop() {
      if (!this.$auth.hasPermi(['video:ptz:control'])) return
      this.$store.dispatch('frontEnd/ptz', {
        deviceId: this.deviceId,
        channelId: this.channelId,
        command: 'stop',
        horizonSpeed: 0,
        verticalSpeed: 0,
        zoomSpeed: 0
      })
    },
    onFocusMove(e) {
      if (!this.$auth.hasPermi(['video:ptz:control'])) return
      const speedVal = this.ptzSpeed(e.speed)
      this.$store.dispatch('frontEnd/focus', [this.deviceId, this.channelId, e.command, speedVal])
    },
    onFocusStop() {
      if (!this.$auth.hasPermi(['video:ptz:control'])) return
      this.$store.dispatch('frontEnd/focus', [this.deviceId, this.channelId, 'stop', 0])
    },
    onIrisMove(e) {
      if (!this.$auth.hasPermi(['video:ptz:control'])) return
      const speedVal = this.ptzSpeed(e.speed)
      this.$store.dispatch('frontEnd/iris', [this.deviceId, this.channelId, e.command, speedVal])
    },
    onIrisStop() {
      if (!this.$auth.hasPermi(['video:ptz:control'])) return
      this.$store.dispatch('frontEnd/iris', [this.deviceId, this.channelId, 'stop', 0])
    },
    emitDragZoom(type) {
      if (!this.$auth.hasPermi(['video:ptz:control'])) return
      this.$emit('drag-zoom-start', type)
    }
  }
}
</script>

<style scoped>
.ptz-section {
  flex-shrink: 0;
  margin-bottom: 8px;
}
</style>
