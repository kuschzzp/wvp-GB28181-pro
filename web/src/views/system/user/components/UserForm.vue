<template>
  <el-dialog
    :title="isEdit ? '编辑用户' : '新增用户'"
    :visible.sync="visible"
    width="620px"
    append-to-body
    :close-on-click-modal="false"
    @closed="resetForm"
  >
    <el-alert
      v-if="form.systemUser"
      class="system-user-alert"
      title="系统内置用户受保护，部分操作可能被后端拒绝。"
      type="warning"
      :closable="false"
      show-icon
    />
    <el-form
      ref="form"
      :model="form"
      :rules="rules"
      label-width="96px"
      size="small"
    >
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model.trim="form.username"
              :disabled="isEdit"
              maxlength="64"
              placeholder="登录账号，创建后不可修改"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="用户昵称" prop="nickname">
            <el-input
              v-model.trim="form.nickname"
              maxlength="64"
              placeholder="用于界面展示"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item v-if="!isEdit" label="初始密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          show-password
          maxlength="128"
          autocomplete="new-password"
          placeholder="至少 8 位，创建后请提醒用户及时修改"
        />
      </el-form-item>

      <el-form-item label="所属部门" prop="deptId">
        <el-cascader
          v-model="form.deptId"
          class="full-width"
          :options="departmentOptions"
          :props="departmentProps"
          clearable
          filterable
          placeholder="请选择用户主部门"
        />
        <div class="form-tip">部门只控制用户和系统管理数据范围，不代表视频资源归属。</div>
      </el-form-item>

      <el-form-item v-if="!isEdit" label="分配角色" prop="roleIds">
        <el-select
          v-model="form.roleIds"
          class="full-width"
          multiple
          filterable
          placeholder="请选择至少一个角色"
        >
          <el-option
            v-for="role in enabledRoles"
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
      <el-button size="small" type="primary" :loading="saving" @click="submit">
        保存
      </el-button>
    </span>
  </el-dialog>
</template>

<script>
import { createSystemUser, updateSystemUser } from '@/api/user'

function initialForm() {
  return {
    id: null,
    username: '',
    password: '',
    nickname: '',
    deptId: null,
    roleIds: [],
    systemUser: false
  }
}

export default {
  name: 'SystemUserForm',
  props: {
    departments: {
      type: Array,
      default: () => []
    },
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
      departmentProps: {
        value: 'id',
        label: 'deptName',
        children: 'children',
        emitPath: false,
        checkStrictly: true
      },
      rules: {
        username: [
          { required: true, message: '请输入用户名', trigger: 'blur' },
          {
            pattern: /^[A-Za-z0-9_.@-]{1,64}$/,
            message: '用户名仅支持字母、数字及 _ . @ -',
            trigger: 'blur'
          }
        ],
        nickname: [{ required: true, message: '请输入用户昵称', trigger: 'blur' }],
        password: [
          { required: true, message: '请输入初始密码', trigger: 'blur' },
          { min: 8, max: 128, message: '密码长度必须为 8 到 128 位', trigger: 'blur' }
        ],
        deptId: [{ required: true, message: '请选择所属部门', trigger: 'change' }],
        roleIds: [
          { type: 'array', required: true, min: 1, message: '请选择至少一个角色', trigger: 'change' }
        ]
      }
    }
  },
  computed: {
    isEdit() {
      return this.form.id !== null
    },
    departmentOptions() {
      const mapNode = node => ({
        ...node,
        disabled: Boolean(node.readOnlyPath) || node.status === 1,
        children: (node.children || []).map(mapNode)
      })
      return this.departments.map(mapNode)
    },
    enabledRoles() {
      return this.roles.filter(role => role.status === 0 || this.form.roleIds.includes(role.id))
    }
  },
  methods: {
    open(row = null) {
      this.form = row
        ? {
            id: row.id,
            username: row.username || '',
            password: '',
            nickname: row.nickname || row.username || '',
            deptId: row.deptId || null,
            roleIds: [],
            systemUser: Boolean(row.systemUser)
          }
        : initialForm()
      this.visible = true
      this.$nextTick(() => {
        if (this.$refs.form) this.$refs.form.clearValidate()
      })
    },
    buildPayload() {
      if (this.isEdit) {
        return {
          nickname: this.form.nickname,
          deptId: this.form.deptId
        }
      }
      return {
        username: this.form.username,
        password: this.form.password,
        nickname: this.form.nickname,
        deptId: this.form.deptId,
        roleIds: [...this.form.roleIds]
      }
    },
    submit() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        this.saving = true
        const request = this.isEdit
          ? updateSystemUser(this.form.id, this.buildPayload())
          : createSystemUser(this.buildPayload())
        request
          .then(() => {
            this.$message.success(this.isEdit ? '用户信息已更新' : '用户已创建')
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
.full-width {
  width: 100%;
}

.system-user-alert {
  margin-bottom: 18px;
}

.form-tip {
  margin-top: 5px;
  color: #909399;
  font-size: 12px;
  line-height: 18px;
}

.role-key {
  float: right;
  color: #909399;
  font-size: 12px;
}
</style>
