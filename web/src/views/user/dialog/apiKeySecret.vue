<template>
  <el-dialog
    title="请立即保存 API Key"
    width="560px"
    :append-to-body="true"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :destroy-on-close="true"
    :visible.sync="showDialog"
    :before-close="handleDialogClose"
  >
    <el-alert
      class="secret-alert"
      title="该密钥只会显示这一次，关闭后无法再次查看。"
      type="warning"
      :closable="false"
      show-icon
    />
    <el-input
      v-model="secret"
      class="secret-input"
      type="textarea"
      :rows="3"
      readonly
      aria-label="API Key 明文"
    />
    <div slot="footer" class="dialog-footer">
      <el-button :loading="copying" :disabled="!secret" type="primary" @click="copySecret">
        复制 API Key
      </el-button>
      <el-button @click="close">我已保存，关闭</el-button>
    </div>
  </el-dialog>
</template>

<script>
export default {
  name: 'ApiKeySecret',
  data() {
    return {
      showDialog: false,
      secret: '',
      copying: false
    }
  },
  beforeDestroy() {
    this.clearSecret()
  },
  methods: {
    open(createdKey) {
      if (!createdKey || !createdKey.secret) {
        this.$message({
          showClose: true,
          message: '接口未返回 API Key 明文，请重试',
          type: 'error'
        })
        return false
      }
      this.secret = createdKey.secret
      this.showDialog = true
      return true
    },
    async copySecret() {
      if (!this.secret || this.copying) {
        return false
      }
      this.copying = true
      try {
        await this.$copyText(this.secret)
        this.$message({
          showClose: true,
          message: 'API Key 已复制，请妥善保存',
          type: 'success'
        })
        return true
      } catch (error) {
        this.$message({
          showClose: true,
          message: '复制失败，请手动选择并复制',
          type: 'error'
        })
        return false
      } finally {
        this.copying = false
      }
    },
    handleDialogClose(done) {
      this.clearSecret()
      done()
    },
    close() {
      this.clearSecret()
      this.showDialog = false
    },
    clearSecret() {
      this.secret = ''
      this.copying = false
    }
  }
}
</script>

<style scoped>
.secret-alert {
  margin-bottom: 16px;
}

.secret-input {
  font-family: Menlo, Monaco, Consolas, "Courier New", monospace;
}
</style>
