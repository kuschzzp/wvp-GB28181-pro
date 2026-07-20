<template>
  <div class="app-container system-role-page">
    <div class="page-heading">
      <div>
        <h2>角色管理</h2>
        <p>把菜单操作权限、部门数据范围和视频资源范围组合成可复用角色。</p>
      </div>
      <el-button
        v-hasPermi="['system:role:add']"
        type="primary"
        size="small"
        icon="el-icon-plus"
        @click="openCreate"
      >新增角色</el-button>
    </div>

    <section class="role-card">
      <el-form :inline="true" size="small" class="query-bar" @submit.native.prevent>
        <el-form-item label="角色">
          <el-input
            v-model.trim="query.keyword"
            clearable
            prefix-icon="el-icon-search"
            placeholder="角色名称或标识"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部状态">
            <el-option label="正常" :value="0" />
            <el-option label="停用" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button icon="el-icon-refresh" :loading="loading" @click="loadData">刷新</el-button>
        </el-form-item>
      </el-form>

      <el-table
        v-loading="loading"
        :data="filteredRoles"
        row-key="id"
        border
        size="small"
        height="calc(100vh - 245px)"
        header-row-class-name="table-header"
      >
        <el-table-column prop="name" label="角色名称" min-width="170">
          <template slot-scope="scope">
            <div class="role-name">
              <span>{{ scope.row.name }}</span>
              <el-tag v-if="scope.row.systemRole === 1" size="mini" type="warning">系统</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="roleKey" label="角色标识" min-width="170">
          <template slot-scope="scope"><code>{{ scope.row.roleKey }}</code></template>
        </el-table-column>
        <el-table-column prop="roleSort" label="排序" width="80" align="center" />
        <el-table-column label="部门数据范围" min-width="170">
          <template slot-scope="scope">
            <el-tag size="mini" effect="plain">{{ departmentScopeLabel(scope.row.deptDataScope) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="视频资源范围" min-width="160">
          <template slot-scope="scope">
            <el-tag size="mini" effect="plain" type="success">
              {{ videoScopeLabel(scope.row.videoScopeType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template slot-scope="scope">
            <el-tag :type="scope.row.status === 0 ? 'success' : 'info'" size="mini">
              {{ scope.row.status === 0 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" min-width="170">
          <template slot-scope="scope">{{ scope.row.updateTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template slot-scope="scope">
            <el-button
              v-hasPermi="['system:role:edit']"
              type="text"
              size="small"
              @click="openEdit(scope.row)"
            >编辑</el-button>
            <el-button
              v-hasPermi="['system:role:changeStatus']"
              type="text"
              size="small"
              :disabled="scope.row.systemRole === 1"
              @click="toggleRoleStatus(scope.row)"
            >{{ scope.row.status === 0 ? '停用' : '启用' }}</el-button>
            <el-button
              v-hasPermi="['system:role:remove']"
              type="text"
              size="small"
              class="danger-action"
              :disabled="scope.row.systemRole === 1"
              @click="removeRole(scope.row)"
            >删除</el-button>
          </template>
        </el-table-column>
        <template slot="empty">
          <div class="empty-state">没有匹配的角色</div>
        </template>
      </el-table>

      <div class="table-summary">共 {{ filteredRoles.length }} 个角色</div>
    </section>

    <role-form
      ref="roleForm"
      :menus="menuTree"
      :departments="departments"
      :can-manage-system-role="canManageSystemRole"
      @saved="loadData"
    />
  </div>
</template>

<script>
import {
  changeSystemRoleStatus,
  deleteSystemRole,
  getSystemRole,
  getSystemRoleReferences,
  querySystemRoles
} from '@/api/role'
import { buildManagementTree, unwrapResponseData } from '@/utils/systemManagement'
import RoleForm from './components/RoleForm.vue'

export default {
  name: 'SystemRole',
  components: { RoleForm },
  data() {
    return {
      loading: false,
      roles: [],
      menuTree: [],
      departments: [],
      query: {
        keyword: '',
        status: null
      },
      departmentScopeLabels: {
        ALL: '全部数据',
        CUSTOM: '指定部门',
        DEPT: '本部门',
        DEPT_AND_CHILD: '本部门及以下',
        SELF: '仅本人',
        NONE: '无数据'
      },
      videoScopeLabels: {
        ALL: '全部资源',
        CUSTOM: '指定资源组',
        NONE: '无资源'
      }
    }
  },
  computed: {
    filteredRoles() {
      const keyword = this.query.keyword.toLowerCase()
      return this.roles.filter(role => {
        const matchesKeyword = !keyword ||
          String(role.name || '').toLowerCase().includes(keyword) ||
          String(role.roleKey || '').toLowerCase().includes(keyword)
        const matchesStatus = this.query.status === null || this.query.status === '' ||
          role.status === this.query.status
        return matchesKeyword && matchesStatus
      })
    },
    canManageSystemRole() {
      return Boolean(this.$store.getters.systemUser)
    }
  },
  created() {
    this.loadData()
  },
  methods: {
    loadData() {
      this.loading = true
      return Promise.all([querySystemRoles(), getSystemRoleReferences()])
        .then(([roleResponse, referenceResponse]) => {
          const references = unwrapResponseData(referenceResponse, {}) || {}
          this.roles = unwrapResponseData(roleResponse, []) || []
          this.menuTree = buildManagementTree(
            Array.isArray(references.menus) ? references.menus : []
          )
          this.departments = Array.isArray(references.departments)
            ? references.departments
            : []
        })
        .finally(() => {
          this.loading = false
        })
    },
    openCreate() {
      this.$refs.roleForm.open()
    },
    openEdit(row) {
      this.loading = true
      getSystemRole(row.id)
        .then(response => {
          const detail = unwrapResponseData(response, null)
          if (detail) this.$refs.roleForm.open(detail)
        })
        .finally(() => {
          this.loading = false
        })
    },
    removeRole(row) {
      this.$confirm(
        `删除角色“${row.name}”后，关联用户将失去该角色权限，是否继续？`,
        '删除角色',
        { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
      ).then(() => deleteSystemRole(row.id))
        .then(() => {
          this.$message.success('角色已删除')
          this.loadData()
        })
        .catch(() => {})
    },
    toggleRoleStatus(row) {
      const status = row.status === 0 ? 1 : 0
      const action = status === 0 ? '启用' : '停用'
      this.$confirm(
        `确认${action}角色“${row.name}”吗？关联用户的现有登录令牌会立即失效。`,
        '角色状态变更',
        { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' }
      ).then(() => changeSystemRoleStatus(row.id, status))
        .then(() => {
          this.$message.success(`角色已${action}`)
          this.loadData()
        })
        .catch(() => {})
    },
    departmentScopeLabel(scope) {
      return this.departmentScopeLabels[scope] || scope || '-'
    },
    videoScopeLabel(scope) {
      return this.videoScopeLabels[scope] || scope || '-'
    }
  }
}
</script>

<style scoped>
.system-role-page {
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

.role-card {
  padding: 16px;
  border: 1px solid #e7eaf0;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(31, 45, 61, 0.04);
}

.query-bar {
  margin-bottom: 8px;
  padding: 12px 12px 0;
  border-radius: 5px;
  background: #f8fafc;
}

.role-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}

code {
  color: #52677f;
  font-family: Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.danger-action {
  color: #f56c6c;
}

.empty-state {
  padding: 28px 0;
  color: #909399;
}

.table-summary {
  margin-top: 14px;
  color: #909399;
  font-size: 12px;
  text-align: right;
}
</style>
