<template>
  <div class="player-tabs-wrapper" ref="playerWrapper">
    <el-tabs v-if="showTab && playerList.length > 1" v-model="activePlayer" type="card" :stretch="true" @tab-click="changePlayer">
      <el-tab-pane v-for="p in playerList" :key="p.key" :label="p.label" :name="p.key"></el-tab-pane>
    </el-tabs>
    <div class="player-video-area" :style="{ height: showTab ? 'calc(100% - 36px)' : '100%' }">
      <jessibucaPlayer
        v-if="activePlayer === 'jessibuca'"
        ref="jessibuca"
        style="width: 100%; height: 100%;"
        :has-audio="hasAudio"
        :show-button="showButton"
        fluent autoplay live
        @playTimeChange="$emit('playTimeChange', $event)"
        @playStatusChange="handlePlayerStatus"
      />
      <rtc-player
        v-if="activePlayer === 'webRTC'"
        ref="webRTC"
        style="width: 100%; height: 100%;"
        :has-audio="hasAudio"
        :show-button="showButton"
        fluent autoplay live
        @playTimeChange="$emit('playTimeChange', $event)"
        @playStatusChange="handlePlayerStatus"
      />
      <h265web
        v-if="activePlayer === 'h265web'"
        ref="h265web"
        style="width: 100%; height: 100%;"
        :has-audio="hasAudio"
        :show-button="showButton"
        fluent autoplay live
        @playTimeChange="$emit('playTimeChange', $event)"
        @playStatusChange="handlePlayerStatus"
      />
      <div v-if="sourceError" class="media-source-error">
        <i class="el-icon-warning-outline" />
        <span>{{ sourceError }}</span>
      </div>
    </div>
  </div>
</template>

<script>
import jessibucaPlayer from './jessibuca.vue'
import rtcPlayer from './rtcPlayer.vue'
import h265web from './h265web.vue'
import {
  createMediaSourceCancelSource,
  isMediaSourceCancelled,
  isMediaSourceForbidden,
  issueMediaSource,
  normalizePlayableRef
} from '@/utils/mediaSource'

const RENEW_AHEAD_MS = 15000
const RETRY_DELAY_MS = 1000

export default {
  name: 'PlayerTabs',
  components: { jessibucaPlayer, rtcPlayer, h265web },
  props: {
    hasAudio: { type: Boolean, default: false },
    showButton: { type: Boolean, default: true },
    showTab: { type: Boolean, default: true }
  },
  data() {
    return {
      playableRef: null,
      activePlayer: 'jessibuca',
      playerSchema: {
        jessibuca: 'WS_FLV',
        webRTC: 'WEBRTC',
        h265web: 'HLS'
      },
      sourceLease: null,
      sourceRequest: null,
      requestGeneration: 0,
      renewTimer: null,
      retryTimer: null,
      retryBlocked: false,
      stopped: true,
      destroyedState: false,
      sourceError: '',
      allPlayerList: [
        { key: 'jessibuca', label: 'Jessibuca' },
        { key: 'webRTC', label: 'WebRTC' },
        { key: 'h265web', label: 'H265web' }
      ]
    }
  },
  computed: {
    playerList() {
      return this.allPlayerList
    },
    playerCount() {
      return this.playerList.length
    }
  },
  created() {
    if (this.playerCount === 1) {
      this.activePlayer = this.playerList[0].key
    }
  },
  beforeDestroy() {
    this.teardown(true)
  },
  methods: {
    getPlayerList() {
      return this.playerList
    },
    getActivePlayer() {
      return this.activePlayer
    },
    switchPlayer(key) {
      if (this.activePlayer === key) return
      this.activePlayer = key
      if (this.playableRef) {
        return this.play()
      }
    },
    changePlayer(tab) {
      this.activePlayer = tab.name
      this.$emit('player-changed', this.activePlayer)
      return this.play()
    },
    setStreamInfo(playableRef) {
      this.playableRef = normalizePlayableRef(playableRef)
      this.stopped = false
      this.destroyedState = false
      this.retryBlocked = false
      this.sourceError = ''
      return this.play()
    },
    async play() {
      if (!this.playableRef || this.stopped || this.destroyedState || this.retryBlocked) return
      this.cancelPendingRequest('媒体源已切换')
      this.clearTimer('retryTimer')
      this.clearTimer('renewTimer')
      const generation = ++this.requestGeneration
      const cancelSource = createMediaSourceCancelSource()
      this.sourceRequest = cancelSource
      const schema = this.playerSchema[this.activePlayer]
      try {
        const lease = await issueMediaSource(this.playableRef, schema, cancelSource.token)
        if (!this.isCurrentRequest(generation) || this.stopped || this.destroyedState) return
        this.sourceLease = lease
        this.sourceError = ''
        await this.$nextTick()
        if (!this.isCurrentRequest(generation) || this.stopped || this.destroyedState) return
        const player = this.$refs[this.activePlayer]
        if (player && player.play) {
          player.play(lease.url)
        }
        this.$emit('playerChanged', {
          expiresAt: lease.expiresAt,
          schema: lease.schema
        })
        this.scheduleRenewal(lease.expiresAt)
      } catch (error) {
        if (!this.isCurrentRequest(generation) || isMediaSourceCancelled(error)) return
        if (isMediaSourceForbidden(error)) {
          this.retryBlocked = true
          this.sourceError = '无权播放该视频，已停止自动重试'
        } else {
          this.sourceError = error && error.message ? error.message : '临时播放地址申请失败'
          this.scheduleRetry()
        }
        this.$emit('play-error', error)
      } finally {
        if (this.isCurrentRequest(generation) && this.sourceRequest === cancelSource) {
          this.sourceRequest = null
        }
      }
    },
    isCurrentRequest(generation) {
      return generation === this.requestGeneration
    },
    scheduleRenewal(expiresAt) {
      this.clearTimer('renewTimer')
      const expiry = Date.parse(expiresAt)
      if (Number.isNaN(expiry)) return
      const delay = Math.max(1000, expiry - Date.now() - RENEW_AHEAD_MS)
      this.renewTimer = setTimeout(() => {
        this.renewTimer = null
        this.play()
      }, delay)
    },
    scheduleRetry() {
      if (this.retryBlocked || this.stopped || this.destroyedState || this.retryTimer) return
      this.retryTimer = setTimeout(() => {
        this.retryTimer = null
        this.play()
      }, RETRY_DELAY_MS)
    },
    handlePlayerStatus(status) {
      this.$emit('playStatusChange', status)
      if (status === false && !this.stopped && !this.retryBlocked) {
        this.scheduleRetry()
      }
    },
    cancelPendingRequest(message) {
      if (this.sourceRequest) {
        this.sourceRequest.cancel(message)
        this.sourceRequest = null
      }
      this.requestGeneration++
    },
    clearTimer(name) {
      if (this[name]) {
        clearTimeout(this[name])
        this[name] = null
      }
    },
    teardown(destroyed) {
      this.stopped = true
      this.destroyedState = Boolean(destroyed)
      this.cancelPendingRequest('播放器已停止')
      this.clearTimer('renewTimer')
      this.clearTimer('retryTimer')
      this.sourceLease = null
      this.$emit('playerChanged', { expiresAt: null, schema: null })
    },
    stop() {
      this.teardown(false)
      if (this.$refs[this.activePlayer]) {
        this.$refs[this.activePlayer].pause()
      }
    },
    pause() {
      if (this.$refs[this.activePlayer]) {
        this.$refs[this.activePlayer].pause()
      }
    },
    isLoaded() {
      const player = this.$refs[this.activePlayer]
      return Boolean(player && player.loaded)
    },
    unPause() {
      const player = this.$refs[this.activePlayer]
      if (player && player.unPause) {
        player.unPause()
      } else if (this.playableRef && !this.stopped) {
        return this.play()
      }
    },
    mute() {
      const player = this.$refs[this.activePlayer]
      if (player && player.mute) {
        player.mute()
      }
    },
    cancelMute() {
      const player = this.$refs[this.activePlayer]
      if (player && player.cancelMute) {
        player.cancelMute()
      }
    },
    destroy() {
      this.teardown(false)
      const player = this.$refs[this.activePlayer]
      if (player && player.destroy) {
        player.destroy()
      }
    },
    setPlaybackRate(rate) {
      const player = this.$refs[this.activePlayer]
      if (player && player.setPlaybackRate) {
        player.setPlaybackRate(rate)
      }
    },
    resize(width, height) {
      const player = this.$refs[this.activePlayer]
      if (player && player.resize) {
        player.resize(width, height)
      }
    },
    screenshot() {
      const player = this.$refs[this.activePlayer]
      if (player && player.screenshot) {
        return player.screenshot()
      }
    },
    getVideoRect() {
      const player = this.$refs[this.activePlayer]
      return player && player.getVideoRect ? player.getVideoRect() : null
    },
    startDragZoom(callback) {
      const player = this.$refs[this.activePlayer]
      if (player && player.startDragZoom) {
        player.startDragZoom(callback)
      }
    }
  }
}
</script>

<style scoped>
.player-tabs-wrapper {
  width: 100%;
  height: 100%;
}
.player-tabs-wrapper .el-tabs {
  margin-bottom: 0;
}
.player-tabs-wrapper .el-tabs >>> .el-tabs__header {
  margin-bottom: 0;
}
.player-video-area {
  position: relative;
  width: 100%;
  height: 100%;
  background: #000;
}
.media-source-error {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #f5f7fa;
  background: rgba(0, 0, 0, 0.72);
  font-size: 14px;
}
</style>
