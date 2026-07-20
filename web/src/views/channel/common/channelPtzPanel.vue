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
  name: 'ChannelPtzPanel',
  components: { ptzControls },
  props: {
    channelId: { type: String, default: null }
  },
  methods: {
    onPtzMove(e) {
      if (!this.$auth.hasPermi(['video:ptz:control'])) return
      this.$store.dispatch('commonChanel/ptz', {
        channelId: this.channelId,
        command: e.direction,
        panSpeed: e.speed,
        tiltSpeed: e.speed,
        zoomSpeed: e.speed
      })
    },
    onPtzStop() {
      if (!this.$auth.hasPermi(['video:ptz:control'])) return
      this.$store.dispatch('commonChanel/ptz', {
        channelId: this.channelId,
        command: 'stop',
        panSpeed: 0,
        tiltSpeed: 0,
        zoomSpeed: 0
      })
    },
    onFocusMove(e) {
      if (!this.$auth.hasPermi(['video:ptz:control'])) return
      this.$store.dispatch('commonChanel/focus', {
        channelId: this.channelId,
        command: e.command,
        speed: e.speed
      })
    },
    onFocusStop() {
      if (!this.$auth.hasPermi(['video:ptz:control'])) return
      this.$store.dispatch('commonChanel/focus', {
        channelId: this.channelId,
        command: 'stop',
        speed: 0
      })
    },
    onIrisMove(e) {
      if (!this.$auth.hasPermi(['video:ptz:control'])) return
      this.$store.dispatch('commonChanel/iris', {
        channelId: this.channelId,
        command: e.command,
        speed: e.speed
      })
    },
    onIrisStop() {
      if (!this.$auth.hasPermi(['video:ptz:control'])) return
      this.$store.dispatch('commonChanel/iris', {
        channelId: this.channelId,
        command: 'stop',
        speed: 0
      })
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
