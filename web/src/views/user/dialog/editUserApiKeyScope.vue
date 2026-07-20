<template>
  <el-dialog
    :title="dialogTitle"
    :visible.sync="visible"
    width="820px"
    custom-class="api-key-scope-dialog"
    append-to-body
    :close-on-click-modal="false"
    :destroy-on-close="true"
    @closed="reset"
  >
    <api-key-scope-editor ref="scopeEditor" />
    <span slot="footer" class="dialog-footer">
      <el-button size="small" @click="visible = false">取消</el-button>
      <el-button size="small" type="primary" :loading="saving" @click="save">
        保存权限范围
      </el-button>
    </span>
  </el-dialog>
</template>

<script>
import ApiKeyScopeEditor from './apiKeyScopeEditor.vue'

export default {
  name: 'EditUserApiKeyScope',
  components: { ApiKeyScopeEditor },
  data() {
    return {
      visible: false,
      saving: false,
      row: null,
      ownerId: null,
      savedCallback: null
    }
  },
  computed: {
    dialogTitle() {
      const app = this.row && this.row.app
      return app ? `编辑 ${app} 的权限范围` : '编辑 API Key 权限范围'
    }
  },
  methods: {
    open(row, ownerId, callback) {
      this.row = row
      this.ownerId = ownerId
      this.savedCallback = callback
      this.visible = true
      this.$nextTick(() => {
        this.$refs.scopeEditor.initialize({
          permissionMenuIds: row.permissionMenuIds,
          resourceGroupIds: row.resourceGroupIds,
          ipAllowlist: row.ipAllowlist
        }, ownerId).catch(() => {})
      })
    },
    save() {
      const error = this.$refs.scopeEditor.validate()
      if (error) {
        this.$message.warning(error)
        return
      }
      this.saving = true
      return this.$store.dispatch('userApiKeys/updateScope', {
        id: this.row.id,
        userId: this.ownerId,
        ...this.$refs.scopeEditor.value()
      }).then(() => {
        this.$message.success('API Key 权限范围已更新')
        this.visible = false
        if (this.savedCallback) this.savedCallback()
      }).finally(() => {
        this.saving = false
      })
    },
    reset() {
      this.row = null
      this.ownerId = null
      this.savedCallback = null
      this.saving = false
    }
  }
}
</script>
