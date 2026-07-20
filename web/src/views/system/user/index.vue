<template>
  <div class="app-container system-user-page">
    <div class="page-heading">
      <div>
        <h2>用户管理</h2>
        <p>维护登录账号、所属部门和角色。部门仅用于系统管理数据权限。</p>
      </div>
      <el-button
        v-hasPermi="['system:user:add']"
        type="primary"
        size="small"
        icon="el-icon-plus"
        @click="openCreate"
      >新增用户</el-button>
    </div>

    <div class="management-layout">
      <aside class="department-panel">
        <div class="panel-title">
          <span>部门范围</span>
          <el-button
            type="text"
            icon="el-icon-refresh"
            :loading="referenceLoading"
            @click="loadReferenceData"
          />
        </div>
        <el-input
          v-model.trim="departmentKeyword"
          size="small"
          clearable
          prefix-icon="el-icon-search"
          placeholder="搜索部门"
          @input="filterDepartment"
        />
        <button
          type="button"
          class="all-department"
          :class="{ active: query.deptId === null }"
          @click="selectAllDepartments"
        >
          <i class="el-icon-office-building" />
          全部可见部门
        </button>
        <el-tree
          ref="departmentTree"
          class="department-tree"
          :data="departments"
          node-key="id"
          default-expand-all
          highlight-current
          :expand-on-click-node="false"
          :filter-node-method="filterDepartmentNode"
          :props="departmentTreeProps"
          @node-click="selectDepartment"
        >
          <span slot-scope="{ data }" class="department-node">
            <span class="department-node-name">
              <i class="el-icon-folder-opened" />
              {{ data.deptName }}
            </span>
            <el-tag v-if="data.readOnlyPath" size="mini" type="info">路径</el-tag>
          </span>
        </el-tree>
      </aside>

      <section class="user-panel">
        <el-form :inline="true" size="small" class="query-bar" @submit.native.prevent>
          <el-form-item label="账号">
            <el-input
              v-model.trim="query.keyword"
              clearable
              prefix-icon="el-icon-search"
              placeholder="用户名或昵称"
              @keyup.enter.native="search"
            />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" clearable placeholder="全部状态">
              <el-option label="正常" :value="0" />
              <el-option label="停用" :value="1" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="el-icon-search" @click="search">查询</el-button>
            <el-button icon="el-icon-refresh-left" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table
          v-loading="loading"
          :data="users"
          row-key="id"
          size="small"
          border
          height="calc(100vh - 280px)"
          header-row-class-name="table-header"
        >
          <el-table-column prop="username" label="用户名" min-width="145">
            <template slot-scope="scope">
              <div class="user-identity">
                <span>{{ scope.row.username }}</span>
                <el-tag v-if="scope.row.systemUser" size="mini" type="warning">系统</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="nickname" label="昵称" min-width="130" show-overflow-tooltip />
          <el-table-column prop="deptName" label="所属部门" min-width="150" show-overflow-tooltip>
            <template slot-scope="scope">{{ scope.row.deptName || '-' }}</template>
          </el-table-column>
          <el-table-column label="角色" min-width="190" show-overflow-tooltip>
            <template slot-scope="scope">{{ roleNames(scope.row.roleIds) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template slot-scope="scope">
              <el-tag :type="scope.row.status === 0 ? 'success' : 'info'" size="mini">
                {{ scope.row.status === 0 ? '正常' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="登录锁定" width="90" align="center">
            <template slot-scope="scope">
              <span :class="scope.row.locked ? 'danger-text' : 'muted-text'">
                {{ scope.row.locked ? '已锁定' : '未锁定' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="lastLoginAt" label="最后登录" min-width="170">
            <template slot-scope="scope">{{ scope.row.lastLoginAt || '从未登录' }}</template>
          </el-table-column>
          <el-table-column label="操作" fixed="right" width="410">
            <template slot-scope="scope">
              <el-button
                v-hasPermi="['system:user:edit']"
                type="text"
                size="small"
                :disabled="isOwnUser(scope.row)"
                @click="openEdit(scope.row)"
              >编辑</el-button>
              <el-button
                v-hasPermi="['system:role:assign']"
                type="text"
                size="small"
                :disabled="isOwnUser(scope.row)"
                @click="openRoleAssignment(scope.row)"
              >角色</el-button>
              <el-dropdown
                v-hasPermi="['system:user:changeStatus']"
                class="status-dropdown"
                trigger="click"
                @command="handleStatusCommand(scope.row, $event)"
              >
                <el-button type="text" size="small" :disabled="isOwnUser(scope.row)">
                  状态<i class="el-icon-arrow-down el-icon--right" />
                </el-button>
                <el-dropdown-menu slot="dropdown">
                  <el-dropdown-item command="toggle-status">
                    {{ scope.row.status === 0 ? '停用账号' : '启用账号' }}
                  </el-dropdown-item>
                  <el-dropdown-item command="toggle-lock">
                    {{ scope.row.locked ? '解除登录锁定' : '锁定登录' }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </el-dropdown>
              <el-button
                v-hasPermi="['system:user:resetPwd']"
                type="text"
                size="small"
                @click="resetPassword(scope.row)"
              >重置密码</el-button>
              <el-button
                v-if="canManageApiKeys(scope.row)"
                type="text"
                size="small"
                @click="openApiKeys(scope.row)"
              >API Key</el-button>
              <el-button
                v-hasPermi="['system:user:remove']"
                type="text"
                size="small"
                class="danger-action"
                :disabled="scope.row.systemUser || isOwnUser(scope.row)"
                @click="removeUser(scope.row)"
              >删除</el-button>
            </template>
          </el-table-column>
          <template slot="empty">
            <div class="empty-state">当前部门范围内没有匹配用户</div>
          </template>
        </el-table>

        <el-pagination
          class="pagination"
          background
          :current-page="query.page"
          :page-size="query.count"
          :page-sizes="[15, 20, 30, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="changePageSize"
          @current-change="changePage"
        />
      </section>
    </div>

    <user-form
      ref="userForm"
      :departments="departments"
      :roles="roles"
      @saved="queryUsers"
    />
    <user-role-dialog ref="userRoleDialog" :roles="roles" @saved="queryUsers" />
    <api-key-manager ref="apiKeyManager" />
  </div>
</template>

<script>
import {
  changeSystemUserStatus,
  deleteSystemUser,
  getSystemUserReferences,
  querySystemUsers,
  resetSystemUserPassword
} from '@/api/user'
import { unwrapResponseData } from '@/utils/systemManagement'
import { hasPermi } from '@/utils/permission'
import ApiKeyManager from '@/views/user/apiKeyManager.vue'
import UserForm from './components/UserForm.vue'
import UserRoleDialog from './components/UserRoleDialog.vue'

export default {
  name: 'SystemUser',
  components: { ApiKeyManager, UserForm, UserRoleDialog },
  data() {
    return {
      loading: false,
      referenceLoading: false,
      users: [],
      roles: [],
      departments: [],
      total: 0,
      departmentKeyword: '',
      departmentTreeProps: {
        label: 'deptName',
        children: 'children'
      },
      query: {
        page: 1,
        count: 20,
        keyword: '',
        deptId: null,
        status: null
      }
    }
  },
  computed: {
    currentUserId() {
      const current = this.$store.state.user && this.$store.state.user.user
      return current ? current.id : null
    }
  },
  created() {
    this.loadReferenceData()
    this.queryUsers()
  },
  methods: {
    loadReferenceData() {
      this.referenceLoading = true
      return getSystemUserReferences()
        .then(response => {
          const references = unwrapResponseData(response, {}) || {}
          this.departments = Array.isArray(references.departments) ? references.departments : []
          this.roles = Array.isArray(references.roles) ? references.roles : []
        })
        .finally(() => {
          this.referenceLoading = false
        })
    },
    queryUsers() {
      this.loading = true
      const params = {
        page: this.query.page,
        count: this.query.count
      }
      if (this.query.keyword) params.keyword = this.query.keyword
      if (this.query.deptId !== null) params.deptId = this.query.deptId
      if (this.query.status !== null && this.query.status !== '') params.status = this.query.status
      return querySystemUsers(params)
        .then(response => {
          const page = unwrapResponseData(response, {}) || {}
          this.users = Array.isArray(page.list) ? page.list : []
          this.total = Number(page.total || 0)
        })
        .finally(() => {
          this.loading = false
        })
    },
    search() {
      this.query.page = 1
      this.queryUsers()
    },
    resetQuery() {
      this.query.keyword = ''
      this.query.status = null
      this.query.page = 1
      this.queryUsers()
    },
    selectAllDepartments() {
      this.query.deptId = null
      this.query.page = 1
      if (this.$refs.departmentTree) this.$refs.departmentTree.setCurrentKey(null)
      this.queryUsers()
    },
    selectDepartment(data) {
      if (data.readOnlyPath) return
      this.query.deptId = data.id
      this.query.page = 1
      this.queryUsers()
    },
    filterDepartment(value) {
      if (this.$refs.departmentTree) this.$refs.departmentTree.filter(value)
    },
    filterDepartmentNode(value, data) {
      if (!value) return true
      const keyword = value.toLowerCase()
      return String(data.deptName || '').toLowerCase().includes(keyword) ||
        String(data.deptCode || '').toLowerCase().includes(keyword)
    },
    changePage(page) {
      this.query.page = page
      this.queryUsers()
    },
    changePageSize(count) {
      this.query.count = count
      this.query.page = 1
      this.queryUsers()
    },
    roleNames(roleIds) {
      if (!Array.isArray(roleIds) || roleIds.length === 0) return '-'
      const names = roleIds.map(id => {
        const role = this.roles.find(item => item.id === id)
        return role ? role.name : `#${id}`
      })
      return names.join('、')
    },
    isOwnUser(row) {
      return this.currentUserId !== null && row.id === this.currentUserId
    },
    canManageApiKeys(row) {
      const permissions = this.$store.getters.permissions
      return hasPermi(['system:apikey:list'], permissions) &&
        (this.isOwnUser(row) || hasPermi(['system:apikey:manageOthers'], permissions))
    },
    openCreate() {
      this.$refs.userForm.open()
    },
    openEdit(row) {
      this.$refs.userForm.open(row)
    },
    openRoleAssignment(row) {
      this.$refs.userRoleDialog.open(row)
    },
    handleStatusCommand(row, command) {
      const payload = {
        status: command === 'toggle-status'
          ? (row.status === 0 ? 1 : 0)
          : row.status,
        locked: command === 'toggle-lock' ? !row.locked : Boolean(row.locked)
      }
      const action = command === 'toggle-status'
        ? (payload.status === 0 ? '启用' : '停用')
        : (payload.locked ? '锁定' : '解除锁定')
      this.$confirm(
        `确认${action}用户“${row.username}”吗？变更后其现有登录令牌会立即失效。`,
        '账号状态变更',
        { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' }
      ).then(() => changeSystemUserStatus(row.id, payload))
        .then(() => {
          this.$message.success(`用户已${action}`)
          this.queryUsers()
        })
        .catch(() => {})
    },
    resetPassword(row) {
      this.$prompt(`为用户“${row.username}”设置新密码`, '重置密码', {
        confirmButtonText: '确认重置',
        cancelButtonText: '取消',
        inputType: 'password',
        inputPlaceholder: '请输入 8 到 128 位新密码',
        inputValidator: value => {
          if (!value || value.length < 8 || value.length > 128) {
            return '密码长度必须为 8 到 128 位'
          }
          return true
        }
      }).then(({ value }) => resetSystemUserPassword(row.id, value))
        .then(() => this.$message.success('密码已重置'))
        .catch(() => {})
    },
    openApiKeys(row) {
      this.$refs.apiKeyManager.openDialog(row.id)
    },
    removeUser(row) {
      this.$confirm(
        `删除用户“${row.username}”后将无法登录，是否继续？`,
        '删除用户',
        { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
      ).then(() => deleteSystemUser(row.id))
        .then(() => {
          this.$message.success('用户已删除')
          if (this.users.length === 1 && this.query.page > 1) this.query.page -= 1
          this.queryUsers()
        })
        .catch(() => {})
    }
  }
}
</script>

<style scoped>
.system-user-page {
  min-height: calc(100vh - 84px);
  background: #f5f7fa;
}

.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-heading h2 {
  margin: 0 0 6px;
  color: #1f2d3d;
  font-size: 22px;
}

.page-heading p {
  margin: 0;
  color: #7a869a;
  font-size: 13px;
}

.management-layout {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  gap: 16px;
}

.department-panel,
.user-panel {
  border: 1px solid #e7eaf0;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(31, 45, 61, 0.04);
}

.department-panel {
  padding: 16px 12px;
  min-height: calc(100vh - 190px);
}

.user-panel {
  min-width: 0;
  padding: 16px;
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding-left: 4px;
  color: #303133;
  font-size: 15px;
  font-weight: 600;
}

.all-department {
  width: 100%;
  margin: 10px 0 4px;
  padding: 9px 10px;
  border: 0;
  border-radius: 4px;
  outline: none;
  background: transparent;
  color: #606266;
  text-align: left;
  cursor: pointer;
}

.all-department:hover,
.all-department.active {
  background: #ecf5ff;
  color: #409eff;
}

.all-department i {
  margin-right: 6px;
}

.department-tree {
  max-height: calc(100vh - 310px);
  overflow: auto;
}

.department-node {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  padding-right: 6px;
}

.department-node-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.department-node-name i {
  margin-right: 4px;
  color: #8da2bd;
}

.query-bar {
  margin-bottom: 8px;
  padding: 12px 12px 0;
  border-radius: 5px;
  background: #f8fafc;
}

.user-identity {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}

.danger-action,
.danger-text {
  color: #f56c6c;
}

.status-dropdown {
  margin-left: 10px;
}

.muted-text {
  color: #909399;
}

.pagination {
  margin-top: 16px;
  text-align: right;
}

.empty-state {
  padding: 28px 0;
  color: #909399;
}

@media (max-width: 1000px) {
  .management-layout {
    grid-template-columns: 1fr;
  }

  .department-panel {
    min-height: auto;
  }

  .department-tree {
    max-height: 260px;
  }
}
</style>
