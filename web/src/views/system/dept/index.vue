<template>
  <div class="app-container system-department-page">
    <div class="page-heading">
      <div>
        <h2>部门管理</h2>
        <p>部门用于约束人员与系统管理数据范围，不用于组织摄像头或视频通道。</p>
      </div>
      <el-button
        v-hasPermi="['system:dept:add']"
        type="primary"
        size="small"
        icon="el-icon-plus"
        @click="openCreate()"
      >新增部门</el-button>
    </div>

    <section class="department-card">
      <div class="toolbar">
        <el-input
          v-model.trim="keyword"
          size="small"
          clearable
          prefix-icon="el-icon-search"
          placeholder="搜索部门名称或编码"
        />
        <el-button size="small" icon="el-icon-refresh" :loading="loading" @click="loadTree">
          刷新
        </el-button>
      </div>

      <el-alert
        class="scope-alert"
        title="标记为“只读路径”的节点仅用于展示当前数据范围的上级路径，不能新增、编辑或删除。"
        type="info"
        :closable="false"
        show-icon
      />

      <el-table
        v-loading="loading"
        :data="filteredDepartments"
        row-key="id"
        border
        size="small"
        default-expand-all
        height="calc(100vh - 280px)"
        :tree-props="{ children: 'children' }"
        header-row-class-name="table-header"
      >
        <el-table-column prop="deptName" label="部门名称" min-width="240">
          <template slot-scope="scope">
            <span class="department-name">
              <i class="el-icon-office-building" />
              {{ scope.row.deptName }}
              <el-tag v-if="isRoot(scope.row)" size="mini" type="warning">ROOT</el-tag>
              <el-tag v-else-if="scope.row.readOnlyPath" size="mini" type="info">只读路径</el-tag>
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="deptCode" label="部门编码" min-width="160">
          <template slot-scope="scope"><code>{{ scope.row.deptCode }}</code></template>
        </el-table-column>
        <el-table-column prop="orderNum" label="排序" width="80" align="center" />
        <el-table-column prop="leaderUserId" label="负责人用户 ID" min-width="140">
          <template slot-scope="scope">{{ scope.row.leaderUserId || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template slot-scope="scope">
            <el-tag :type="scope.row.status === 0 ? 'success' : 'info'" size="mini">
              {{ scope.row.status === 0 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="205">
          <template slot-scope="scope">
            <el-button
              v-hasPermi="['system:dept:add']"
              type="text"
              size="small"
              :disabled="!canOperate(scope.row) || scope.row.status === 1"
              @click="openCreate(scope.row)"
            >新增下级</el-button>
            <el-button
              v-hasPermi="['system:dept:edit']"
              type="text"
              size="small"
              :disabled="!canOperate(scope.row) || isRoot(scope.row)"
              @click="openEdit(scope.row)"
            >编辑</el-button>
            <el-button
              v-hasPermi="['system:dept:remove']"
              type="text"
              size="small"
              class="danger-action"
              :disabled="!canOperate(scope.row) || isRoot(scope.row)"
              @click="removeDepartment(scope.row)"
            >删除</el-button>
          </template>
        </el-table-column>
        <template slot="empty">
          <div class="empty-state">当前数据范围内没有匹配部门</div>
        </template>
      </el-table>
    </section>

    <el-dialog
      :title="form.id ? '编辑部门' : '新增部门'"
      :visible.sync="dialogVisible"
      width="560px"
      append-to-body
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="departmentForm" :model="form" :rules="rules" label-width="104px" size="small">
        <el-form-item label="上级部门" prop="parentId">
          <el-select
            v-model="form.parentId"
            class="full-width"
            filterable
            placeholder="请选择上级部门"
          >
            <el-option
              v-for="option in parentOptions"
              :key="option.id"
              :label="`${'· '.repeat(option.treeDepth)}${option.deptName}`"
              :value="option.id"
              :disabled="option.disabled"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model.trim="form.deptName" maxlength="64" placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="部门编码" prop="deptCode">
          <el-input v-model.trim="form.deptCode" maxlength="64" placeholder="建议使用稳定的英文或数字编码" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="显示顺序" prop="orderNum">
              <el-input-number v-model="form.orderNum" :min="0" :max="9999" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部门状态">
              <el-radio-group v-model="form.status">
                <el-radio :label="0">正常</el-radio>
                <el-radio :label="1">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="负责人用户 ID">
          <el-input-number
            v-model="form.leaderUserId"
            class="full-width number-input"
            :min="1"
            :max="2147483647"
            controls-position="right"
            placeholder="可选"
          />
          <div class="form-tip">负责人仅作为部门信息，不自动获得部门权限。</div>
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="dialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="submit">保存</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import {
  createDepartment,
  deleteDepartment,
  getDepartmentTree,
  updateDepartment
} from '@/api/system/dept'
import {
  collectSubtreeIds,
  flattenManagementTree,
  unwrapResponseData
} from '@/utils/systemManagement'

function initialForm() {
  return {
    id: null,
    parentId: null,
    deptName: '',
    deptCode: '',
    orderNum: 0,
    leaderUserId: null,
    status: 0
  }
}

export default {
  name: 'SystemDepartment',
  data() {
    return {
      loading: false,
      saving: false,
      dialogVisible: false,
      keyword: '',
      departments: [],
      form: initialForm(),
      rules: {
        parentId: [{ required: true, message: '请选择上级部门', trigger: 'change' }],
        deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
        deptCode: [{ required: true, message: '请输入部门编码', trigger: 'blur' }]
      }
    }
  },
  computed: {
    filteredDepartments() {
      const keyword = this.keyword.toLowerCase()
      if (!keyword) return this.departments
      const filterTree = nodes => nodes.reduce((result, node) => {
        const children = filterTree(node.children || [])
        const matched = String(node.deptName || '').toLowerCase().includes(keyword) ||
          String(node.deptCode || '').toLowerCase().includes(keyword)
        if (matched || children.length > 0) result.push({ ...node, children })
        return result
      }, [])
      return filterTree(this.departments)
    },
    parentOptions() {
      const excluded = this.form.id
        ? collectSubtreeIds(this.departments, this.form.id)
        : new Set()
      return flattenManagementTree(this.departments)
        .filter(node => !excluded.has(node.id))
        .map(node => ({
          ...node,
          disabled: Boolean(node.readOnlyPath) || node.status === 1
        }))
    }
  },
  created() {
    this.loadTree()
  },
  methods: {
    loadTree() {
      this.loading = true
      return getDepartmentTree()
        .then(response => {
          this.departments = unwrapResponseData(response, []) || []
        })
        .finally(() => {
          this.loading = false
        })
    },
    isRoot(row) {
      return Number(row.parentId) === 0
    },
    canOperate(row) {
      return !row.readOnlyPath
    },
    firstWritableDepartment() {
      return flattenManagementTree(this.departments)
        .find(node => !node.readOnlyPath && node.status === 0)
    },
    openCreate(parent = null) {
      const target = parent || this.firstWritableDepartment()
      if (!target) {
        this.$message.warning('当前数据范围内没有可作为上级的部门')
        return
      }
      this.form = { ...initialForm(), parentId: target.id }
      this.dialogVisible = true
      this.clearValidation()
    },
    openEdit(row) {
      this.form = {
        id: row.id,
        parentId: row.parentId,
        deptName: row.deptName,
        deptCode: row.deptCode,
        orderNum: Number(row.orderNum || 0),
        leaderUserId: row.leaderUserId || null,
        status: Number(row.status || 0)
      }
      this.dialogVisible = true
      this.clearValidation()
    },
    clearValidation() {
      this.$nextTick(() => {
        if (this.$refs.departmentForm) this.$refs.departmentForm.clearValidate()
      })
    },
    submit() {
      this.$refs.departmentForm.validate(valid => {
        if (!valid) return
        const payload = {
          parentId: this.form.parentId,
          deptName: this.form.deptName,
          deptCode: this.form.deptCode,
          orderNum: this.form.orderNum,
          leaderUserId: this.form.leaderUserId,
          status: this.form.status
        }
        this.saving = true
        const request = this.form.id
          ? updateDepartment(this.form.id, payload)
          : createDepartment(payload)
        request
          .then(() => {
            this.$message.success(this.form.id ? '部门已更新' : '部门已创建')
            this.dialogVisible = false
            this.loadTree()
          })
          .finally(() => {
            this.saving = false
          })
      })
    },
    removeDepartment(row) {
      this.$confirm(
        `仅空部门可以删除。确认删除“${row.deptName}”吗？`,
        '删除部门',
        { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
      ).then(() => deleteDepartment(row.id))
        .then(() => {
          this.$message.success('部门已删除')
          this.loadTree()
        })
        .catch(() => {})
    },
    resetForm() {
      this.form = initialForm()
      this.saving = false
    }
  }
}
</script>

<style scoped>
.system-department-page {
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

.department-card {
  padding: 16px;
  border: 1px solid #e7eaf0;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(31, 45, 61, 0.04);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.toolbar .el-input {
  width: 320px;
}

.scope-alert {
  margin-bottom: 12px;
}

.department-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}

.department-name > i {
  color: #8da2bd;
}

code {
  color: #52677f;
  font-family: Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.danger-action {
  color: #f56c6c;
}

.full-width {
  width: 100%;
}

.number-input ::v-deep .el-input {
  width: 100%;
}

.form-tip {
  margin-top: 5px;
  color: #909399;
  font-size: 12px;
  line-height: 18px;
}

.empty-state {
  padding: 28px 0;
  color: #909399;
}
</style>
