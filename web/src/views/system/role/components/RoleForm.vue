<template>
  <el-dialog
    :title="isEdit ? '编辑角色' : '新增角色'"
    :visible.sync="visible"
    width="780px"
    append-to-body
    :close-on-click-modal="false"
    @closed="resetForm"
  >
    <el-form ref="form" :model="form" :rules="rules" label-width="104px" size="small">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="角色名称" prop="name">
            <el-input v-model.trim="form.name" maxlength="64" placeholder="例如：区域运维员" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="角色标识" prop="roleKey">
            <el-input
              v-model.trim="form.roleKey"
              maxlength="64"
              placeholder="例如：region_operator"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="显示顺序" prop="roleSort">
            <el-input-number v-model="form.roleSort" :min="0" :max="9999" controls-position="right" />
          </el-form-item>
        </el-col>
        <el-col v-if="!isEdit" :span="8">
          <el-form-item label="角色状态">
            <el-radio-group v-model="form.status">
              <el-radio :label="0">正常</el-radio>
              <el-radio :label="1">停用</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col v-if="!isEdit && canManageSystemRole" :span="8">
          <el-form-item label="系统角色">
            <el-switch v-model="form.systemRole" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-tabs v-model="activeTab" class="permission-tabs">
        <el-tab-pane label="菜单与操作权限" name="menu">
          <div class="tree-toolbar">
            <el-input
              v-model.trim="menuKeyword"
              size="small"
              clearable
              prefix-icon="el-icon-search"
              placeholder="筛选菜单或权限编码"
              @input="filterMenu"
            />
            <div class="tree-actions">
              <el-checkbox v-model="menuExpanded" @change="toggleMenuExpanded">展开全部</el-checkbox>
              <el-button type="text" @click="checkAllMenus">全选</el-button>
              <el-button type="text" @click="clearAllMenus">清空</el-button>
            </div>
          </div>
          <el-tree
            ref="menuTree"
            class="permission-tree"
            :data="menuOptions"
            node-key="id"
            show-checkbox
            check-strictly
            :default-expand-all="menuExpanded"
            :filter-node-method="filterMenuNode"
            :props="menuTreeProps"
          >
            <span slot-scope="{ data }" class="tree-node-content">
              <span>
                <i :class="menuTypeIcon(data.menuType)" />
                {{ data.menuName }}
              </span>
              <span v-if="data.perms" class="permission-code">{{ data.perms }}</span>
            </span>
          </el-tree>
        </el-tab-pane>

        <el-tab-pane label="部门数据范围" name="department">
          <el-alert
            title="部门范围只约束用户、角色、部门等系统管理数据，不约束摄像头和通道。"
            type="info"
            :closable="false"
            show-icon
          />
          <el-form-item class="scope-field" label="范围策略" prop="deptDataScope">
            <el-select v-model="form.deptDataScope" class="full-width" @change="changeDeptScope">
              <el-option
                v-for="option in departmentScopes"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              >
                <span>{{ option.label }}</span>
                <span class="option-description">{{ option.description }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <div v-show="form.deptDataScope === 'CUSTOM'" class="custom-department-box">
            <div class="tree-toolbar compact">
              <el-input
                v-model.trim="departmentKeyword"
                size="small"
                clearable
                prefix-icon="el-icon-search"
                placeholder="筛选授权部门"
                @input="filterDepartment"
              />
              <div class="tree-actions">
                <el-button type="text" @click="checkAllDepartments">全选可授权部门</el-button>
                <el-button type="text" @click="clearAllDepartments">清空</el-button>
              </div>
            </div>
            <el-tree
              ref="departmentTree"
              class="permission-tree department-scope-tree"
              :data="departmentOptions"
              node-key="id"
              show-checkbox
              check-strictly
              default-expand-all
              :filter-node-method="filterDepartmentNode"
              :props="departmentTreeProps"
            >
              <span slot-scope="{ data }" class="tree-node-content">
                <span><i class="el-icon-office-building" /> {{ data.deptName }}</span>
                <el-tag v-if="data.readOnlyPath" size="mini" type="info">只读路径</el-tag>
              </span>
            </el-tree>
          </div>
        </el-tab-pane>

        <el-tab-pane label="视频资源范围" name="video">
          <el-alert
            title="视频资源授权与部门完全独立；选择“指定资源组”后，由视频资源组授权关系决定可见设备。"
            type="warning"
            :closable="false"
            show-icon
          />
          <el-form-item class="scope-field" label="资源策略" prop="videoScopeType">
            <el-radio-group
              v-model="form.videoScopeType"
              :disabled="!canAssignVideoResources"
              @change="changeVideoScope"
            >
              <el-radio-button label="ALL">全部视频资源</el-radio-button>
              <el-radio-button label="CUSTOM">指定资源组</el-radio-button>
              <el-radio-button label="NONE">无视频资源</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-alert
            v-if="!canAssignVideoResources"
            class="video-scope-readonly-alert"
            title="当前账号没有“分配资源组”权限，现有视频范围仅供查看，保存角色时会原样保留。"
            type="info"
            :closable="false"
            show-icon
          />
          <div v-show="form.videoScopeType === 'CUSTOM'" class="custom-video-scope-box">
            <div class="video-scope-heading">
              <div>
                <strong>指定视频资源组</strong>
                <p>选择父组会动态包含全部下级组；停用组不可新增授权。</p>
              </div>
              <el-button
                v-if="canViewVideoResourceGroups"
                type="text"
                icon="el-icon-refresh"
                :loading="resourceGroupsLoading"
                :disabled="!canAssignVideoResources"
                @click="ensureResourceGroups(true)"
              >刷新资源组</el-button>
            </div>
            <resource-group-tree
              v-if="canViewVideoResourceGroups"
              ref="resourceGroupTree"
              class="role-resource-group-tree"
              :groups="resourceGroups"
              :loading="resourceGroupsLoading"
              :checked-keys="form.resourceGroupIds"
              :readonly="!canAssignVideoResources"
              checkable
              @checked-change="handleResourceGroupChecked"
            />
            <el-alert
              v-else
              title="当前账号没有“查看资源组”权限，无法展示资源组名称；保存角色时仍会原样保留既有视频范围。"
              type="info"
              :closable="false"
              show-icon
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-form>

    <span slot="footer" class="dialog-footer">
      <el-button size="small" @click="visible = false">取消</el-button>
      <el-button size="small" type="primary" :loading="saving" @click="submit">保存</el-button>
    </span>
  </el-dialog>
</template>

<script>
import { createSystemRole, updateSystemRole } from '@/api/role'
import { getResourceGroupTree } from '@/api/system/resourceGroup'
import { flattenManagementTree, unwrapResponseData } from '@/utils/systemManagement'
import ResourceGroupTree from '@/views/system/resourceGroup/components/ResourceGroupTree.vue'

function initialForm() {
  return {
    id: null,
    name: '',
    roleKey: '',
    roleSort: 10,
    deptDataScope: 'SELF',
    videoScopeType: 'NONE',
    status: 0,
    systemRole: false,
    menuIds: [],
    deptIds: [],
    resourceGroupIds: []
  }
}

export default {
  name: 'SystemRoleForm',
  components: { ResourceGroupTree },
  props: {
    menus: {
      type: Array,
      default: () => []
    },
    departments: {
      type: Array,
      default: () => []
    },
    canManageSystemRole: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      visible: false,
      saving: false,
      activeTab: 'menu',
      menuKeyword: '',
      departmentKeyword: '',
      menuExpanded: true,
      resourceGroups: [],
      resourceGroupsLoading: false,
      resourceGroupsLoaded: false,
      originalVideoScopeType: 'NONE',
      originalResourceGroupIds: [],
      form: initialForm(),
      menuTreeProps: { label: 'menuName', children: 'children', disabled: 'disabled' },
      departmentTreeProps: { label: 'deptName', children: 'children', disabled: 'disabled' },
      departmentScopes: [
        { value: 'ALL', label: '全部数据', description: '可管理所有部门的系统数据' },
        { value: 'CUSTOM', label: '指定部门', description: '仅管理勾选部门的数据' },
        { value: 'DEPT', label: '本部门', description: '仅管理用户主部门的数据' },
        { value: 'DEPT_AND_CHILD', label: '本部门及以下', description: '包含用户主部门的所有下级部门' },
        { value: 'SELF', label: '仅本人', description: '只允许查看和维护本人数据' },
        { value: 'NONE', label: '无数据', description: '不授予任何部门管理数据' }
      ],
      rules: {
        name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
        roleKey: [
          { required: true, message: '请输入角色标识', trigger: 'blur' },
          {
            pattern: /^[a-z][a-z0-9_]{1,63}$/,
            message: '角色标识须以小写字母开头，仅含小写字母、数字和下划线',
            trigger: 'blur'
          }
        ],
        deptDataScope: [{ required: true, message: '请选择部门范围', trigger: 'change' }],
        videoScopeType: [{ required: true, message: '请选择视频资源范围', trigger: 'change' }]
      }
    }
  },
  computed: {
    isEdit() {
      return this.form.id !== null
    },
    menuOptions() {
      const mapNode = node => ({
        ...node,
        disabled: node.status === 1,
        children: (node.children || []).map(mapNode)
      })
      return this.menus.map(mapNode)
    },
    departmentOptions() {
      const mapNode = node => ({
        ...node,
        disabled: node.status === 1 || Boolean(node.readOnlyPath),
        children: (node.children || []).map(mapNode)
      })
      return this.departments.map(mapNode)
    },
    canAssignVideoResources() {
      return this.$auth.hasPermi(['system:resource-group:assign'])
    },
    canViewVideoResourceGroups() {
      return this.$auth.hasPermi(['system:resource-group:list'])
    }
  },
  methods: {
    open(detail = null) {
      const role = detail && detail.role
      this.form = role
        ? {
            id: role.id,
            name: role.name || '',
            roleKey: role.roleKey || '',
            roleSort: Number(role.roleSort || 0),
            deptDataScope: role.deptDataScope || 'SELF',
            videoScopeType: role.videoScopeType || 'NONE',
            status: Number(role.status || 0),
            systemRole: Boolean(role.systemRole || role.system),
            menuIds: Array.isArray(detail.menuIds) ? [...detail.menuIds] : [],
            deptIds: Array.isArray(detail.deptIds) ? [...detail.deptIds] : [],
            resourceGroupIds: Array.isArray(detail.resourceGroupIds)
              ? [...detail.resourceGroupIds]
              : []
          }
        : initialForm()
      this.originalVideoScopeType = this.form.videoScopeType
      this.originalResourceGroupIds = [...this.form.resourceGroupIds]
      this.activeTab = 'menu'
      this.visible = true
      this.$nextTick(() => {
        if (this.$refs.form) this.$refs.form.clearValidate()
        if (this.$refs.menuTree) this.$refs.menuTree.setCheckedKeys(this.form.menuIds)
        if (this.$refs.departmentTree) this.$refs.departmentTree.setCheckedKeys(this.form.deptIds)
        if (this.form.videoScopeType === 'CUSTOM' && this.canViewVideoResourceGroups) {
          this.ensureResourceGroups()
        }
      })
    },
    buildPayload() {
      const menuIds = this.$refs.menuTree
        ? this.$refs.menuTree.getCheckedKeys()
        : this.form.menuIds
      const deptIds = this.form.deptDataScope === 'CUSTOM'
        ? (this.$refs.departmentTree
            ? this.$refs.departmentTree.getCheckedKeys()
            : this.form.deptIds)
        : []
      const canAssignVideoResources = this.canAssignVideoResources !== false
      const videoScopeType = canAssignVideoResources
        ? this.form.videoScopeType
        : (this.originalVideoScopeType || this.form.videoScopeType || 'NONE')
      const resourceGroupIds = videoScopeType === 'CUSTOM'
        ? (canAssignVideoResources
            ? (this.$refs.resourceGroupTree
                ? this.$refs.resourceGroupTree.getCheckedKeys()
                : this.form.resourceGroupIds)
            : (Array.isArray(this.originalResourceGroupIds)
                ? this.originalResourceGroupIds
                : this.form.resourceGroupIds))
        : []
      const payload = {
        id: this.form.id,
        name: this.form.name,
        roleKey: this.form.roleKey,
        roleSort: this.form.roleSort,
        deptDataScope: this.form.deptDataScope,
        videoScopeType,
        menuIds: [...new Set(menuIds)],
        deptIds: [...new Set(deptIds)],
        resourceGroupIds: [...new Set(resourceGroupIds.map(Number).filter(id => id > 0))]
      }
      if (!this.isEdit) {
        payload.status = this.form.status
        payload.systemRole = this.form.systemRole
      }
      return payload
    },
    submit() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        const payload = this.buildPayload()
        if (payload.deptDataScope === 'CUSTOM' && payload.deptIds.length === 0) {
          this.activeTab = 'department'
          this.$message.warning('指定部门范围至少选择一个可授权部门')
          return
        }
        if (payload.videoScopeType === 'CUSTOM' && payload.resourceGroupIds.length === 0) {
          this.activeTab = 'video'
          this.$message.warning('指定视频资源范围至少选择一个正常资源组')
          return
        }
        this.saving = true
        const request = this.isEdit
          ? updateSystemRole(this.form.id, payload)
          : createSystemRole(payload)
        request
          .then(() => {
            this.$message.success(this.isEdit ? '角色已更新' : '角色已创建')
            this.visible = false
            this.$emit('saved')
          })
          .finally(() => {
            this.saving = false
          })
      })
    },
    filterMenu(value) {
      if (this.$refs.menuTree) this.$refs.menuTree.filter(value)
    },
    filterMenuNode(value, data) {
      if (!value) return true
      const keyword = value.toLowerCase()
      return String(data.menuName || '').toLowerCase().includes(keyword) ||
        String(data.perms || '').toLowerCase().includes(keyword)
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
    toggleMenuExpanded(expanded) {
      const store = this.$refs.menuTree && this.$refs.menuTree.store
      if (!store) return
      Object.values(store.nodesMap).forEach(node => {
        node.expanded = expanded
      })
    },
    checkAllMenus() {
      if (!this.$refs.menuTree) return
      const ids = flattenManagementTree(this.menuOptions)
        .filter(node => !node.disabled)
        .map(node => node.id)
      this.$refs.menuTree.setCheckedKeys(ids)
    },
    clearAllMenus() {
      if (this.$refs.menuTree) this.$refs.menuTree.setCheckedKeys([])
    },
    checkAllDepartments() {
      if (!this.$refs.departmentTree) return
      const ids = flattenManagementTree(this.departmentOptions)
        .filter(node => !node.disabled)
        .map(node => node.id)
      this.$refs.departmentTree.setCheckedKeys(ids)
    },
    clearAllDepartments() {
      if (this.$refs.departmentTree) this.$refs.departmentTree.setCheckedKeys([])
    },
    changeDeptScope(value) {
      if (value !== 'CUSTOM' && this.$refs.departmentTree) {
        this.$refs.departmentTree.setCheckedKeys([])
      }
    },
    changeVideoScope(value) {
      if (value === 'CUSTOM') {
        this.ensureResourceGroups()
      } else if (this.canAssignVideoResources) {
        this.form.resourceGroupIds = []
      }
    },
    ensureResourceGroups(force = false) {
      if (!this.canViewVideoResourceGroups) {
        return Promise.resolve([])
      }
      if (this.resourceGroupsLoaded && !force) {
        this.$nextTick(() => {
          if (this.$refs.resourceGroupTree) this.$refs.resourceGroupTree.syncCheckedKeys()
        })
        return Promise.resolve(this.resourceGroups)
      }
      this.resourceGroupsLoading = true
      return getResourceGroupTree()
        .then(response => {
          this.resourceGroups = unwrapResponseData(response, []) || []
          this.resourceGroupsLoaded = true
          this.$nextTick(() => {
            if (this.$refs.resourceGroupTree) this.$refs.resourceGroupTree.syncCheckedKeys()
          })
          return this.resourceGroups
        })
        .finally(() => {
          this.resourceGroupsLoading = false
        })
    },
    handleResourceGroupChecked(checkedKeys) {
      if (!this.canAssignVideoResources) return
      this.form.resourceGroupIds = checkedKeys
    },
    menuTypeIcon(type) {
      if (type === 'F') return 'el-icon-thumb'
      if (type === 'C') return 'el-icon-document'
      return 'el-icon-folder-opened'
    },
    resetForm() {
      this.form = initialForm()
      this.originalVideoScopeType = 'NONE'
      this.originalResourceGroupIds = []
      this.saving = false
      this.menuKeyword = ''
      this.departmentKeyword = ''
    }
  }
}
</script>

<style scoped>
.permission-tabs {
  margin-top: 4px;
}

.tree-toolbar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) auto;
  gap: 16px;
  align-items: center;
  margin-bottom: 10px;
}

.tree-toolbar.compact {
  margin-top: 12px;
}

.tree-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  white-space: nowrap;
}

.permission-tree {
  height: 330px;
  padding: 10px;
  overflow: auto;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fbfcfe;
}

.department-scope-tree {
  height: 250px;
}

.tree-node-content {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  padding-right: 10px;
}

.tree-node-content i {
  margin-right: 5px;
  color: #8da2bd;
}

.permission-code {
  margin-left: 16px;
  overflow: hidden;
  color: #909399;
  font-family: Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scope-field {
  margin-top: 18px;
}

.full-width {
  width: 100%;
}

.option-description {
  float: right;
  margin-left: 30px;
  color: #909399;
  font-size: 12px;
}

.custom-department-box {
  margin-left: 104px;
}

.custom-video-scope-box,
.video-scope-readonly-alert {
  margin: 12px 0 0 104px;
}

.video-scope-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
}

.video-scope-heading p {
  margin: 4px 0 0;
  color: #909399;
  font-size: 12px;
}

.role-resource-group-tree {
  height: 250px;
  padding: 10px;
  overflow: auto;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fbfcfe;
}

@media (max-width: 720px) {
  .tree-toolbar {
    grid-template-columns: 1fr;
  }

  .custom-department-box {
    margin-left: 0;
  }

  .custom-video-scope-box,
  .video-scope-readonly-alert {
    margin-left: 0;
  }
}
</style>
