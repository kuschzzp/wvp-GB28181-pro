<template>
  <div class="secured-image" :style="wrapperStyle">
    <el-image
      v-if="objectUrl"
      :src="objectUrl"
      :preview-src-list="preview ? [objectUrl] : []"
      :fit="fit"
      :lazy="lazy"
      class="secured-image__content"
    >
      <div slot="error" class="secured-image__state">
        <i class="el-icon-picture-outline" />
      </div>
    </el-image>
    <div
      v-else
      class="secured-image__state"
      :class="{ 'secured-image__state--clickable': clickToLoad && !loading }"
      @click="clickToLoad && !loading ? load() : null"
    >
      <i :class="loading ? 'el-icon-loading' : 'el-icon-picture-outline'" />
    </div>
  </div>
</template>

<script>
import {
  issueAlarmSnapshotTicket,
  issueGbSnapshotTicket,
  issueJtSnapshotTicket
} from '@/api/mediaAccess'
import {
  createDownloadObjectUrl,
  revokeDownloadObjectUrl
} from '@/utils/downloadTicket'

export default {
  name: 'SecuredImage',
  props: {
    sourceType: {
      type: String,
      required: true,
      validator: value => ['alarm', 'gb', 'jt'].includes(value)
    },
    resourceId: {
      type: [Number, String],
      default: null
    },
    deviceId: {
      type: String,
      default: null
    },
    channelId: {
      type: [Number, String],
      default: null
    },
    width: {
      type: String,
      default: '64px'
    },
    height: {
      type: String,
      default: '48px'
    },
    fit: {
      type: String,
      default: 'cover'
    },
    lazy: {
      type: Boolean,
      default: false
    },
    preview: {
      type: Boolean,
      default: true
    },
    clickToLoad: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      objectUrl: null,
      loading: false,
      requestSequence: 0,
      abortController: null
    }
  },
  computed: {
    wrapperStyle() {
      return { width: this.width, height: this.height }
    },
    requestKey() {
      return [this.sourceType, this.resourceId, this.deviceId, this.channelId].join(':')
    }
  },
  watch: {
    requestKey() {
      if (this.clickToLoad) {
        this.requestSequence++
        this.cancelActiveRequest()
        this.replaceObjectUrl(null)
        this.loading = false
        return
      }
      this.load()
    }
  },
  mounted() {
    if (!this.clickToLoad) this.load()
  },
  beforeDestroy() {
    this.cancelActiveRequest()
    this.replaceObjectUrl(null)
  },
  methods: {
    async load() {
      const sequence = ++this.requestSequence
      this.cancelActiveRequest()
      this.replaceObjectUrl(null)
      if (!this.hasRequiredInput()) return

      this.loading = true
      this.abortController = typeof AbortController === 'undefined'
        ? null
        : new AbortController()
      try {
        const ticket = await this.issueTicket()
        const url = await createDownloadObjectUrl(
          ticket,
          this.abortController && this.abortController.signal)
        if (sequence !== this.requestSequence) {
          revokeDownloadObjectUrl(url)
          return
        }
        this.replaceObjectUrl(url)
      } catch (error) {
        if (!this.isAbort(error) && sequence === this.requestSequence) {
          this.$emit('error', error)
        }
      } finally {
        if (sequence === this.requestSequence) this.loading = false
      }
    },
    issueTicket() {
      if (this.sourceType === 'alarm') {
        return issueAlarmSnapshotTicket(this.resourceId)
      }
      if (this.sourceType === 'jt') {
        return issueJtSnapshotTicket(this.deviceId, this.channelId)
      }
      return issueGbSnapshotTicket(this.deviceId, this.channelId)
    },
    hasRequiredInput() {
      if (this.sourceType === 'alarm') {
        return this.resourceId !== null && this.resourceId !== undefined
      }
      return Boolean(this.deviceId) && this.channelId !== null && this.channelId !== undefined
    },
    cancelActiveRequest() {
      if (this.abortController) this.abortController.abort()
      this.abortController = null
    },
    replaceObjectUrl(nextUrl) {
      if (this.objectUrl && this.objectUrl !== nextUrl) {
        revokeDownloadObjectUrl(this.objectUrl)
      }
      this.objectUrl = nextUrl
    },
    isAbort(error) {
      return error && (error.name === 'AbortError' || error.code === 'ERR_CANCELED')
    }
  }
}
</script>

<style scoped>
.secured-image {
  overflow: hidden;
  color: #c0c4cc;
}

.secured-image__content,
.secured-image__state {
  width: 100%;
  height: 100%;
}

.secured-image__state--clickable {
  cursor: pointer;
}

.secured-image__state {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}
</style>
