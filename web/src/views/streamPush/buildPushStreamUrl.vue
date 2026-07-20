<template>
  <div id="buildPushStreamUrl">
    <el-dialog
      v-el-drag-dialog
      title="构建推流地址"
      width="480px"
      top="12vh"
      :close-on-click-modal="false"
      :visible.sync="showDialog"
      :destroy-on-close="true"
      @close="close"
    >
      <el-alert
        title="旧版推流地址生成已停用"
        description="旧入口需要在浏览器中读取长期 pushKey 并生成可重复使用的 MD5 签名，存在密钥泄露和地址长期复用风险。短期推流凭证将在媒体安全阶段统一接入。"
        type="warning"
        :closable="false"
        show-icon
      />
      <p class="migration-note">
        已有外部推流任务不受此页面调整影响；请暂时通过受控的服务端配置维护推流凭证。
      </p>
      <div slot="footer" class="dialog-footer">
        <el-button @click="close">关闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import elDragDialog from '@/directive/el-drag-dialog'

export default {
  name: 'BuildPushStreamUrl',
  directives: { elDragDialog },
  data() {
    return {
      showDialog: false
    }
  },
  methods: {
    openDialog() {
      this.showDialog = true
    },
    close() {
      this.showDialog = false
    }
  }
}
</script>

<style scoped>
.migration-note {
  margin: 16px 0 0;
  color: #606266;
  line-height: 1.7;
}
</style>
