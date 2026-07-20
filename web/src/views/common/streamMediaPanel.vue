<template>
  <div class="media-info-content stream-share-panel">
    <el-alert
      v-if="!canCreateShare"
      title="当前账号没有创建视频分享的权限"
      type="info"
      :closable="false"
      show-icon
    />

    <el-form v-else label-width="100px" size="small">
      <el-form-item label="有效时长">
        <el-select v-model="durationMinutes" style="width: 100%">
          <el-option :value="15" label="15 分钟" />
          <el-option :value="60" label="1 小时" />
          <el-option :value="360" label="6 小时" />
          <el-option :value="1440" label="1 天" />
          <el-option :value="10080" label="7 天" />
        </el-select>
      </el-form-item>
      <el-form-item label="访问次数">
        <el-input-number
          v-model="maxViews"
          :min="0"
          :max="1000000"
          :step="1"
          controls-position="right"
          style="width: 100%"
        />
        <div class="share-field-tip">0 表示不限制兑换次数</div>
      </el-form-item>
      <el-form-item label="提取码">
        <el-input
          v-model="passcode"
          type="password"
          maxlength="128"
          show-password
          placeholder="可选"
          autocomplete="new-password"
        />
      </el-form-item>
      <el-form-item>
        <el-button
          type="primary"
          :loading="creating"
          :disabled="!validPlayableRef"
          @click="createShare"
        >创建受控分享</el-button>
      </el-form-item>

      <template v-if="createdShare">
        <el-divider />
        <el-form-item label="分享地址">
          <el-input :value="shareUrl" readonly>
            <el-button
              slot="append"
              icon="el-icon-document-copy"
              title="复制分享地址"
              @click="copyUrl(shareUrl)"
            />
          </el-input>
        </el-form-item>
        <el-form-item label="分享状态">
          <el-tag type="success">有效至 {{ shareExpiryText }}</el-tag>
          <el-tag v-if="createdShare.maxViews" type="info">
            最多 {{ createdShare.maxViews }} 次
          </el-tag>
        </el-form-item>
        <el-form-item v-if="canRevokeShare">
          <el-button
            type="danger"
            plain
            :loading="revoking"
            @click="revokeShare"
          >撤销分享</el-button>
        </el-form-item>
      </template>

      <el-alert
        :title="ticketExpiryText"
        type="info"
        :closable="false"
        show-icon
      />
    </el-form>
  </div>
</template>

<script>
import {
  createStreamShare,
  revokeStreamShare
} from '@/api/mediaAccess'

export default {
  name: 'StreamMediaPanel',
  props: {
    playableRef: { type: Object, default: null },
    expiresAt: { type: String, default: '' }
  },
  data() {
    return {
      durationMinutes: 60,
      maxViews: 0,
      passcode: '',
      creating: false,
      revoking: false,
      createdShare: null
    }
  },
  computed: {
    canCreateShare() {
      return this.$auth.hasPermi(['video:stream:share'])
    },
    canRevokeShare() {
      return this.$auth.hasPermi(['video:stream:revoke-share'])
    },
    validPlayableRef() {
      const type = this.playableRef && this.playableRef.resourceType
      const id = Number(this.playableRef && this.playableRef.resourceId)
      return Boolean(type && Number.isSafeInteger(id) && id > 0)
    },
    shareUrl() {
      if (!this.createdShare || !this.createdShare.shareId) return ''
      return `${window.location.origin}/#/play/share/${this.createdShare.shareId}`
    },
    shareExpiryText() {
      if (!this.createdShare || !this.createdShare.expiresAt) return '-'
      return new Date(this.createdShare.expiresAt).toLocaleString()
    },
    ticketExpiryText() {
      if (!this.expiresAt) return '播放器只使用短期凭证，不会暴露长期媒体地址'
      return `当前播放凭证有效至 ${new Date(this.expiresAt).toLocaleTimeString()}`
    }
  },
  watch: {
    playableRef: {
      deep: true,
      handler() {
        this.createdShare = null
      }
    }
  },
  methods: {
    async createShare() {
      if (!this.canCreateShare || !this.validPlayableRef || this.creating) return
      this.creating = true
      try {
        const response = await createStreamShare({
          resourceType: String(this.playableRef.resourceType).toUpperCase(),
          resourceId: Number(this.playableRef.resourceId),
          expiresAt: new Date(Date.now() + this.durationMinutes * 60000).toISOString(),
          maxViews: this.maxViews > 0 ? this.maxViews : null,
          passcode: this.passcode || null
        })
        this.createdShare = response && response.data ? response.data : response
        this.$message.success({ showClose: true, message: '受控分享创建成功' })
      } finally {
        this.creating = false
      }
    },
    async revokeShare() {
      if (!this.canRevokeShare || !this.createdShare || this.revoking) return
      this.revoking = true
      try {
        await revokeStreamShare(this.createdShare.shareId)
        this.createdShare = null
        this.$message.success({ showClose: true, message: '分享已撤销' })
      } finally {
        this.revoking = false
      }
    },
    copyUrl(text) {
      if (!text) return
      this.$copyText(text).then(() => {
        this.$message.success({ showClose: true, message: '分享地址已复制' })
      }, () => {})
    }
  }
}
</script>

<style scoped>
.stream-share-panel { padding: 4px; }
.share-field-tip { margin-top: 4px; color: #909399; font-size: 12px; line-height: 1.4; }
.stream-share-panel .el-tag + .el-tag { margin-left: 6px; }
</style>
