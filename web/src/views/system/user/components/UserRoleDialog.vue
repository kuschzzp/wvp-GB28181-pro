<template>
  <el-dialog
    title="分配角色"
    :visible.sync="visible"
    width="520px"
    append-to-body
    :close-on-click-modal="false"
    @closed="resetForm"
  >
    <el-alert
      title="角色决定功能权限、部门数据范围和视频资源范围；部门与视频资源授权彼此独立。"
      type="info"
      :closable="false"
      show-icon
    />
    <el-form ref="form" :model="form" :rules="rules" label-width="90px" size="small">
      <el-form-item class="first-field" label="用户">
        <el-input :value="form.username" disabled />
      </el-form-item>
      <el-form-item label="角色" prop="roleIds">
        <el-select
          v-model="form.roleIds"
          class="full-width"
          multiple
          filterable
          placeholder="请选择至少一个角色"
        >
          <el-option
            v-for="role in availableRoles"
            :key="role.id"
            :label="role.name"
            :value="role.id"
          >
            <span>{{ role.name }}</span>
            <span class="role-key">{{ role.roleKey }}</span>
          </el-option>
        </el-select>
      </el-form-item>
    </el-form>

    <span slot="footer" class="dialog-footer">
      <el-button size="small" @click="visible = false">取消</el-button>
      <el-button size="small" type="primary" :loading="saving" @click="submit">保存角色</el-button>
    </span>
  </el-dialog>
</template>

<script>
import { assignSystemUserRoles } from '@/api/user'

function initialForm() {
  return {
    id: null,
    username: '',
    roleIds: []
  }
}

export default {
  name: 'SystemUserRoleDialog',
  props: {
    roles: {
      type: Array,
      default: () => []
    }
  },
  data() {
    return {
      visible: false,
      saving: false,
      form: initialForm(),
      rules: {
        roleIds: [
          { type: 'array', required: true, min: 1, message: '请选择至少一个角色', trigger: 'change' }
        ]
      }
    }
  },
  computed: {
    availableRoles() {
      return this.roles.filter(role => role.status === 0 || this.form.roleIds.includes(role.id))
    }
  },
  methods: {
    open(row) {
      this.form = {
        id: row.id,
        username: row.nickname
          ? `${row.username}（${row.nickname}）`
          : row.username,
        roleIds: Array.isArray(row.roleIds) ? [...row.roleIds] : []
      }
      this.visible = true
      this.$nextTick(() => {
        if (this.$refs.form) this.$refs.form.clearValidate()
      })
    },
    submit() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        this.saving = true
        assignSystemUserRoles(this.form.id, [...new Set(this.form.roleIds)])
          .then(() => {
            this.$message.success('用户角色已更新')
            this.visible = false
            this.$emit('saved')
          })
          .finally(() => {
            this.saving = false
          })
      })
    },
    resetForm() {
      this.form = initialForm()
      this.saving = false
    }
  }
}
</script>

<style scoped>
.first-field {
  margin-top: 18px;
}

.full-width {
  width: 100%;
}

.role-key {
  float: right;
  color: #909399;
  font-size: 12px;
}
</style>
