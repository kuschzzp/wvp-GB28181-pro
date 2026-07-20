<template>
  <div class="app-container system-menu-page">
    <div class="page-heading">
      <div>
        <h2>菜单管理</h2>
        <p>维护动态路由、按钮权限和显示顺序；系统内置菜单只允许调整显示状态与排序。</p>
      </div>
      <el-button
        v-hasPermi="['system:menu:add']"
        type="primary"
        size="small"
        icon="el-icon-plus"
        @click="openCreate()"
      >新增菜单</el-button>
    </div>

    <section class="menu-card">
      <div class="toolbar">
        <el-input
          v-model.trim="keyword"
          size="small"
          clearable
          prefix-icon="el-icon-search"
          placeholder="搜索菜单名称、路由或权限编码"
        />
        <div>
          <el-button size="small" @click="expanded = !expanded">
            {{ expanded ? '折叠全部' : '展开全部' }}
          </el-button>
          <el-button size="small" icon="el-icon-refresh" :loading="loading" @click="loadMenus">
            刷新
          </el-button>
        </div>
      </div>

      <el-table
        :key="tableKey"
        v-loading="loading"
        :data="filteredMenuTree"
        row-key="id"
        border
        size="small"
        :default-expand-all="expanded"
        height="calc(100vh - 240px)"
        :tree-props="{ children: 'children' }"
        header-row-class-name="table-header"
      >
        <el-table-column prop="menuName" label="菜单名称" min-width="220">
          <template slot-scope="scope">
            <span class="menu-name">
              <i :class="menuTypeIcon(scope.row.menuType)" />
              {{ scope.row.menuName }}
              <el-tag v-if="scope.row.systemMenu" size="mini" type="warning">内置</el-tag>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="85" align="center">
          <template slot-scope="scope">
            <el-tag size="mini" :type="menuTypeTag(scope.row.menuType)">
              {{ menuTypeLabel(scope.row.menuType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路由地址" min-width="180" show-overflow-tooltip>
          <template slot-scope="scope">{{ scope.row.path || '-' }}</template>
        </el-table-column>
        <el-table-column prop="component" label="组件标识" min-width="190" show-overflow-tooltip>
          <template slot-scope="scope"><code>{{ scope.row.component || '-' }}</code></template>
        </el-table-column>
        <el-table-column prop="perms" label="权限编码" min-width="220" show-overflow-tooltip>
          <template slot-scope="scope"><code>{{ scope.row.perms || '-' }}</code></template>
        </el-table-column>
        <el-table-column prop="orderNum" label="排序" width="75" align="center" />
        <el-table-column label="显示" width="80" align="center">
          <template slot-scope="scope">
            <el-tag :type="scope.row.visible === 0 ? 'success' : 'info'" size="mini">
              {{ scope.row.visible === 0 ? '显示' : '隐藏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template slot-scope="scope">
            <el-tag :type="scope.row.status === 0 ? 'success' : 'info'" size="mini">
              {{ scope.row.status === 0 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="205" fixed="right">
          <template slot-scope="scope">
            <el-button
              v-hasPermi="['system:menu:add']"
              type="text"
              size="small"
              :disabled="scope.row.menuType === 'F' || scope.row.status === 1"
              @click="openCreate(scope.row)"
            >新增下级</el-button>
            <el-button
              v-hasPermi="['system:menu:edit']"
              type="text"
              size="small"
              :disabled="scope.row.systemMenu && !canManageBuiltIn"
              @click="openEdit(scope.row)"
            >编辑</el-button>
            <el-button
              v-hasPermi="['system:menu:remove']"
              type="text"
              size="small"
              class="danger-action"
              :disabled="scope.row.systemMenu || (scope.row.children && scope.row.children.length > 0)"
              @click="removeMenu(scope.row)"
            >删除</el-button>
          </template>
        </el-table-column>
        <template slot="empty">
          <div class="empty-state">没有匹配的菜单或权限</div>
        </template>
      </el-table>
    </section>

    <el-dialog
      :title="form.id ? '编辑菜单' : '新增菜单'"
      :visible.sync="dialogVisible"
      width="720px"
      append-to-body
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-alert
        v-if="immutableBuiltIn"
        class="built-in-alert"
        title="系统内置菜单的路由和权限结构不可修改，本次仅可调整排序与显示状态。"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-form ref="menuForm" :model="form" :rules="rules" label-width="100px" size="small">
        <el-form-item label="菜单类型" prop="menuType">
          <el-radio-group
            v-model="form.menuType"
            :disabled="immutableBuiltIn"
            @change="changeMenuType"
          >
            <el-radio-button label="M">目录</el-radio-button>
            <el-radio-button label="C">菜单</el-radio-button>
            <el-radio-button label="F">按钮权限</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="上级节点" prop="parentId">
              <el-select
                v-model="form.parentId"
                class="full-width"
                filterable
                :disabled="immutableBuiltIn"
              >
                <el-option label="顶级菜单" :value="0" />
                <el-option
                  v-for="option in parentOptions"
                  :key="option.id"
                  :label="`${'· '.repeat(option.treeDepth)}${option.menuName}`"
                  :value="option.id"
                  :disabled="option.status === 1"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="菜单名称" prop="menuName">
              <el-input
                v-model.trim="form.menuName"
                maxlength="64"
                :disabled="immutableBuiltIn"
                placeholder="请输入菜单或操作名称"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <template v-if="form.menuType !== 'F'">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="路由名称" prop="routeName">
                <el-input
                  v-model.trim="form.routeName"
                  maxlength="64"
                  :disabled="immutableBuiltIn"
                  placeholder="唯一的 Vue 路由名称"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="路由地址" prop="path">
                <el-input
                  v-model.trim="form.path"
                  maxlength="255"
                  :disabled="immutableBuiltIn"
                  placeholder="例如：user 或 /system"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="组件标识" prop="component">
            <el-select
              v-model="form.component"
              class="full-width"
              filterable
              :disabled="immutableBuiltIn"
              placeholder="选择前端已注册组件"
            >
              <el-option
                v-for="component in componentOptions"
                :key="component"
                :label="component"
                :value="component"
              />
            </el-select>
          </el-form-item>
        </template>

        <el-form-item :label="form.menuType === 'F' ? '权限编码' : '页面权限'" prop="perms">
          <el-input
            v-model.trim="form.perms"
            maxlength="128"
            :disabled="immutableBuiltIn"
            :placeholder="form.menuType === 'F' ? '必填，例如：system:user:add' : '可选，用于进入页面的权限编码'"
          />
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="菜单图标">
              <el-input
                v-model.trim="form.icon"
                maxlength="100"
                :disabled="immutableBuiltIn"
                placeholder="SVG 图标名称，可选"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="显示顺序" prop="orderNum">
              <el-input-number v-model="form.orderNum" :min="0" :max="9999" controls-position="right" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="侧栏显示">
              <el-radio-group v-model="form.visible">
                <el-radio :label="0">显示</el-radio>
                <el-radio :label="1">隐藏</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="菜单状态">
              <el-radio-group v-model="form.status" :disabled="immutableBuiltIn">
                <el-radio :label="0">正常</el-radio>
                <el-radio :label="1">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col v-if="!form.id && canManageBuiltIn" :span="8">
            <el-form-item label="系统内置">
              <el-switch v-model="form.systemMenu" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row v-if="form.menuType !== 'F'" :gutter="20">
          <el-col :span="8">
            <el-form-item label="页面缓存">
              <el-switch v-model="form.cacheable" :disabled="immutableBuiltIn" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="外部链接">
              <el-switch v-model="form.external" :disabled="immutableBuiltIn" />
            </el-form-item>
          </el-col>
        </el-row>
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
  createSystemMenu,
  deleteSystemMenu,
  getSystemMenu,
  querySystemMenus,
  updateSystemMenu
} from '@/api/menu'
import {
  buildManagementTree,
  collectSubtreeIds,
  flattenManagementTree,
  unwrapResponseData
} from '@/utils/systemManagement'

const COMPONENT_OPTIONS = [
  'Layout',
  'dashboard/index',
  'live/index',
  'alarm/index',
  'channel/index',
  'channel/group/index',
  'channel/region/index',
  'cloud-record/index',
  'device/index',
  'jt1078/index',
  'media-server/index',
  'operations/log/index',
  'operations/real-log/index',
  'operations/system/index',
  'platform/index',
  'position/index',
  'proxy/index',
  'push/index',
  'record-plan/index',
  'system/dept/index',
  'system/menu/index',
  'system/resource-group/index',
  'system/role/index',
  'system/user/index'
]

function initialForm() {
  return {
    id: null,
    parentId: 0,
    menuName: '',
    menuType: 'C',
    routeName: '',
    path: '',
    component: '',
    perms: '',
    icon: '',
    orderNum: 0,
    visible: 0,
    status: 0,
    cacheable: false,
    external: false,
    systemMenu: false
  }
}

export default {
  name: 'SystemMenu',
  data() {
    const requiredForRoute = (rule, value, callback) => {
      if (this.form.menuType !== 'F' && !value) callback(new Error('目录和菜单必须填写该路由字段'))
      else callback()
    }
    const requiredForPermission = (rule, value, callback) => {
      if (this.form.menuType === 'F' && !value) callback(new Error('按钮权限必须填写权限编码'))
      else callback()
    }
    return {
      loading: false,
      saving: false,
      dialogVisible: false,
      expanded: true,
      tableKey: 0,
      keyword: '',
      menus: [],
      form: initialForm(),
      componentOptions: COMPONENT_OPTIONS,
      rules: {
        parentId: [{ required: true, message: '请选择上级节点', trigger: 'change' }],
        menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
        menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
        routeName: [{ validator: requiredForRoute, trigger: 'blur' }],
        path: [{ validator: requiredForRoute, trigger: 'blur' }],
        component: [{ validator: requiredForRoute, trigger: 'change' }],
        perms: [{ validator: requiredForPermission, trigger: 'blur' }]
      }
    }
  },
  computed: {
    menuTree() {
      return buildManagementTree(this.menus)
    },
    filteredMenuTree() {
      const keyword = this.keyword.toLowerCase()
      if (!keyword) return this.menuTree
      const filterTree = nodes => nodes.reduce((result, node) => {
        const children = filterTree(node.children || [])
        const matched = [node.menuName, node.path, node.component, node.perms]
          .some(value => String(value || '').toLowerCase().includes(keyword))
        if (matched || children.length > 0) result.push({ ...node, children })
        return result
      }, [])
      return filterTree(this.menuTree)
    },
    immutableBuiltIn() {
      return Boolean(this.form.id && this.form.systemMenu)
    },
    canManageBuiltIn() {
      return Boolean(this.$store.getters.systemUser)
    },
    parentOptions() {
      const excluded = this.form.id
        ? collectSubtreeIds(this.menuTree, this.form.id)
        : new Set()
      return flattenManagementTree(this.menuTree)
        .filter(node => node.menuType !== 'F' && !excluded.has(node.id))
    }
  },
  watch: {
    expanded() {
      this.tableKey += 1
    }
  },
  created() {
    this.loadMenus()
  },
  methods: {
    loadMenus() {
      this.loading = true
      return querySystemMenus()
        .then(response => {
          this.menus = unwrapResponseData(response, []) || []
        })
        .finally(() => {
          this.loading = false
        })
    },
    openCreate(parent = null) {
      this.form = {
        ...initialForm(),
        parentId: parent ? parent.id : 0,
        menuType: parent ? 'C' : 'M',
        component: parent ? '' : 'Layout'
      }
      this.dialogVisible = true
      this.clearValidation()
    },
    openEdit(row) {
      this.loading = true
      getSystemMenu(row.id)
        .then(response => {
          const menu = unwrapResponseData(response, row) || row
          this.form = {
            id: menu.id,
            parentId: Number(menu.parentId || 0),
            menuName: menu.menuName || '',
            menuType: menu.menuType || 'C',
            routeName: menu.routeName || '',
            path: menu.path || '',
            component: menu.component || '',
            perms: menu.perms || '',
            icon: menu.icon || '',
            orderNum: Number(menu.orderNum || 0),
            visible: Number(menu.visible || 0),
            status: Number(menu.status || 0),
            cacheable: Boolean(menu.cacheable),
            external: Boolean(menu.external),
            systemMenu: Boolean(menu.systemMenu)
          }
          this.dialogVisible = true
          this.clearValidation()
        })
        .finally(() => {
          this.loading = false
        })
    },
    clearValidation() {
      this.$nextTick(() => {
        if (this.$refs.menuForm) this.$refs.menuForm.clearValidate()
      })
    },
    changeMenuType(type) {
      if (type === 'F') {
        this.form.routeName = ''
        this.form.path = ''
        this.form.component = ''
        this.form.cacheable = false
        this.form.external = false
      } else if (type === 'M' && !this.form.component) {
        this.form.component = 'Layout'
      }
      this.clearValidation()
    },
    buildPayload() {
      return {
        parentId: this.form.parentId,
        menuName: this.form.menuName,
        menuType: this.form.menuType,
        routeName: this.form.routeName || null,
        path: this.form.path || null,
        component: this.form.component || null,
        perms: this.form.perms || null,
        icon: this.form.icon || null,
        orderNum: this.form.orderNum,
        visible: this.form.visible,
        status: this.form.status,
        cacheable: this.form.cacheable,
        external: this.form.external,
        systemMenu: this.form.systemMenu
      }
    },
    submit() {
      this.$refs.menuForm.validate(valid => {
        if (!valid) return
        if (this.form.menuType === 'F' && this.form.parentId === 0) {
          this.$message.warning('按钮权限必须选择上级菜单')
          return
        }
        if (this.form.external && !/^https?:\/\//.test(this.form.path)) {
          this.$message.warning('外部链接必须以 http:// 或 https:// 开头')
          return
        }
        const editing = Boolean(this.form.id)
        this.saving = true
        const request = editing
          ? updateSystemMenu(this.form.id, this.buildPayload())
          : createSystemMenu(this.buildPayload())
        request
          .then(() => {
            this.$message.success(editing ? '菜单已更新' : '菜单已创建')
            this.dialogVisible = false
            this.loadMenus()
          })
          .finally(() => {
            this.saving = false
          })
      })
    },
    removeMenu(row) {
      this.$confirm(
        `确认删除“${row.menuName}”吗？关联角色将同时失去该权限。`,
        '删除菜单',
        { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
      ).then(() => deleteSystemMenu(row.id))
        .then(() => {
          this.$message.success('菜单已删除')
          this.loadMenus()
        })
        .catch(() => {})
    },
    menuTypeLabel(type) {
      return { M: '目录', C: '菜单', F: '按钮' }[type] || type
    },
    menuTypeTag(type) {
      return { M: '', C: 'success', F: 'info' }[type] || 'info'
    },
    menuTypeIcon(type) {
      if (type === 'F') return 'el-icon-thumb'
      if (type === 'C') return 'el-icon-document'
      return 'el-icon-folder-opened'
    },
    resetForm() {
      this.form = initialForm()
      this.saving = false
    }
  }
}
</script>

<style scoped>
.system-menu-page {
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

.menu-card {
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

.toolbar > .el-input {
  width: 380px;
}

.menu-name {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}

.menu-name > i {
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

.built-in-alert {
  margin-bottom: 18px;
}

.full-width {
  width: 100%;
}

.empty-state {
  padding: 28px 0;
  color: #909399;
}
</style>
