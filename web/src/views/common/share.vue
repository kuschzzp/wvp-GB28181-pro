<template>
  <main class="share-player-page">
    <header class="share-player-toolbar">
      <div class="share-player-brand">
        <i class="el-icon-video-camera" />
        <span>视频分享</span>
      </div>
      <el-radio-group
        v-model="activePlayer"
        size="mini"
        :disabled="loading || terminalError"
        @change="switchPlayer"
      >
        <el-radio-button label="jessibuca">流畅</el-radio-button>
        <el-radio-button label="webRTC">低延时</el-radio-button>
        <el-radio-button label="h265web">兼容 H.265</el-radio-button>
      </el-radio-group>
      <span class="share-player-expiry">{{ expiryText }}</span>
    </header>

    <section class="share-player-stage">
      <jessibuca-player
        v-if="activePlayer === 'jessibuca'"
        ref="jessibuca"
        :show-button="true"
        style="width: 100%; height: 100%"
        @playStatusChange="handlePlayerStatus"
      />
      <rtc-player
        v-if="activePlayer === 'webRTC'"
        ref="webRTC"
        :show-controls="true"
        style="width: 100%; height: 100%"
        @playStatusChange="handlePlayerStatus"
      />
      <h265web
        v-if="activePlayer === 'h265web'"
        ref="h265web"
        :show-button="true"
        style="width: 100%; height: 100%"
        @playStatusChange="handlePlayerStatus"
      />

      <div v-if="loading" class="share-player-overlay">
        <i class="el-icon-loading" />
        <span>正在获取临时播放凭证</span>
      </div>
      <div v-else-if="errorMessage" class="share-player-overlay share-player-error">
        <i class="el-icon-warning-outline" />
        <strong>{{ errorMessage }}</strong>
        <el-button
          v-if="!terminalError && !passcodeVisible"
          type="primary"
          size="small"
          @click="exchangeAndPlay"
        >重新连接</el-button>
      </div>
    </section>

    <el-dialog
      title="请输入分享提取码"
      width="360px"
      :visible.sync="passcodeVisible"
      :show-close="false"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      append-to-body
    >
      <el-input
        v-model="passcode"
        type="password"
        maxlength="128"
        show-password
        autocomplete="off"
        placeholder="提取码"
        @keyup.enter.native="submitPasscode"
      />
      <p v-if="passcodeError" class="share-passcode-error">{{ passcodeError }}</p>
      <span slot="footer">
        <el-button
          type="primary"
          :loading="loading"
          :disabled="!passcode"
          @click="submitPasscode"
        >开始播放</el-button>
      </span>
    </el-dialog>
  </main>
</template>

<script>
import jessibucaPlayer from './jessibuca.vue'
import rtcPlayer from './rtcPlayer.vue'
import h265web from './h265web.vue'
import { exchangeStreamShareTicket } from '@/api/mediaAccess'
import {
  createMediaSourceCancelSource,
  isMediaSourceCancelled
} from '@/utils/mediaSource'

const RENEW_AHEAD_MS = 15000
const RETRY_DELAY_MS = 2000
const SHARE_ID_PATTERN = /^[A-Za-z0-9_-]{22,64}$/

export default {
  name: 'SharePlayer',
  components: { jessibucaPlayer, rtcPlayer, h265web },
  data() {
    return {
      shareId: '',
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
      loading: false,
      destroyedState: false,
      terminalError: false,
      errorMessage: '',
      passcodeVisible: false,
      passcode: '',
      passcodeError: ''
    }
  },
  computed: {
    expiryText() {
      if (!this.sourceLease || !this.sourceLease.expiresAt) return '短期凭证播放'
      return `本次凭证有效至 ${new Date(this.sourceLease.expiresAt).toLocaleTimeString()}`
    }
  },
  watch: {
    '$route.params.shareId'() {
      this.initializeShare()
    }
  },
  mounted() {
    this.initializeShare()
  },
  beforeDestroy() {
    this.teardown()
  },
  methods: {
    initializeShare() {
      this.teardown(false)
      this.destroyedState = false
      this.terminalError = false
      this.errorMessage = ''
      this.passcodeVisible = false
      this.passcodeError = ''
      this.passcode = ''
      this.shareId = String(this.$route.params.shareId || '').trim()
      if (!SHARE_ID_PATTERN.test(this.shareId)) {
        this.terminalError = true
        this.errorMessage = '分享链接无效'
        return
      }
      return this.exchangeAndPlay()
    },
    switchPlayer() {
      this.stopActivePlayer()
      this.errorMessage = ''
      return this.exchangeAndPlay()
    },
    async exchangeAndPlay() {
      if (!this.shareId || this.destroyedState || this.terminalError) return
      this.cancelPendingRequest('分享播放源已切换')
      this.clearTimer('renewTimer')
      this.clearTimer('retryTimer')
      const generation = ++this.requestGeneration
      const cancelSource = createMediaSourceCancelSource()
      this.sourceRequest = cancelSource
      this.loading = true
      this.passcodeError = ''
      try {
        const response = await exchangeStreamShareTicket(
          this.shareId,
          {
            schema: this.playerSchema[this.activePlayer],
            passcode: this.passcode || null
          },
          cancelSource.token
        )
        if (!this.isCurrentRequest(generation) || this.destroyedState) return
        const payload = response && response.data ? response.data : response
        const schema = this.playerSchema[this.activePlayer]
        const url = payload && payload.sources &&
          (payload.sources[schema] || payload.sources[schema.toLowerCase()])
        if (!url || !payload.expiresAt) {
          throw new Error('服务端未返回有效的临时播放凭证')
        }
        this.sourceLease = { url, schema, expiresAt: payload.expiresAt }
        this.errorMessage = ''
        this.passcodeVisible = false
        await this.$nextTick()
        if (!this.isCurrentRequest(generation) || this.destroyedState) return
        const player = this.$refs[this.activePlayer]
        if (player && player.play) player.play(url)
        this.scheduleRenewal(payload.expiresAt)
      } catch (error) {
        if (!this.isCurrentRequest(generation) || isMediaSourceCancelled(error)) return
        this.handleExchangeError(error)
      } finally {
        if (this.isCurrentRequest(generation)) {
          this.loading = false
          if (this.sourceRequest === cancelSource) this.sourceRequest = null
        }
      }
    },
    handleExchangeError(error) {
      const status = error && error.response && error.response.status
      const reason = error && error.response && error.response.data && error.response.data.msg
      if (status === 403 && (reason === 'PASSCODE_REQUIRED' || reason === 'PASSCODE_INVALID')) {
        this.passcodeVisible = true
        this.passcodeError = reason === 'PASSCODE_INVALID' ? '提取码错误，请重试' : ''
        this.errorMessage = ''
        return
      }
      if (status === 404 || status === 410) {
        this.terminalError = true
        this.errorMessage = this.shareFailureMessage(reason, status)
        return
      }
      this.errorMessage = error && error.message ? error.message : '分享播放凭证获取失败'
      this.scheduleRetry()
    },
    shareFailureMessage(reason, status) {
      const messages = {
        EXPIRED: '分享已过期',
        REVOKED: '分享已被撤销',
        EXHAUSTED: '分享访问次数已用完',
        AUTHORIZATION_CHANGED: '分享创建者的访问权限已发生变化'
      }
      return messages[reason] || (status === 404 ? '分享不存在' : '分享已失效')
    },
    submitPasscode() {
      if (!this.passcode) return
      return this.exchangeAndPlay()
    },
    scheduleRenewal(expiresAt) {
      this.clearTimer('renewTimer')
      const expiry = Date.parse(expiresAt)
      if (Number.isNaN(expiry)) return
      this.renewTimer = setTimeout(() => {
        this.renewTimer = null
        this.exchangeAndPlay()
      }, Math.max(1000, expiry - Date.now() - RENEW_AHEAD_MS))
    },
    scheduleRetry() {
      if (this.retryTimer || this.terminalError || this.destroyedState || this.passcodeVisible) return
      this.retryTimer = setTimeout(() => {
        this.retryTimer = null
        this.exchangeAndPlay()
      }, RETRY_DELAY_MS)
    },
    handlePlayerStatus(status) {
      if (status === false && !this.loading && !this.terminalError) {
        this.scheduleRetry()
      }
    },
    isCurrentRequest(generation) {
      return generation === this.requestGeneration
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
    stopActivePlayer() {
      const player = this.$refs[this.activePlayer]
      if (player && player.pause) player.pause()
    },
    teardown(destroyed = true) {
      this.destroyedState = destroyed
      this.cancelPendingRequest('分享播放器已销毁')
      this.clearTimer('renewTimer')
      this.clearTimer('retryTimer')
      this.stopActivePlayer()
      this.sourceLease = null
      this.loading = false
    }
  }
}
</script>

<style scoped>
.share-player-page {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  color: #e5e7eb;
  background: #070b12;
  overflow: hidden;
}
.share-player-toolbar {
  min-height: 52px;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 16px;
  padding: 0 20px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(15, 23, 42, 0.94);
}
.share-player-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  letter-spacing: 0.04em;
}
.share-player-brand i { color: #409eff; }
.share-player-expiry {
  justify-self: end;
  color: #94a3b8;
  font-size: 12px;
}
.share-player-stage {
  position: relative;
  flex: 1;
  min-height: 0;
  background: #000;
}
.share-player-overlay {
  position: absolute;
  inset: 0;
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  color: #cbd5e1;
  background: rgba(2, 6, 23, 0.84);
}
.share-player-overlay i { font-size: 30px; }
.share-player-error strong { font-size: 16px; font-weight: 500; }
.share-passcode-error {
  margin: 10px 0 0;
  color: #f56c6c;
  font-size: 13px;
}
@media (max-width: 720px) {
  .share-player-toolbar {
    grid-template-columns: 1fr auto;
    padding: 0 12px;
  }
  .share-player-expiry { display: none; }
  .share-player-brand span { display: none; }
}
</style>
