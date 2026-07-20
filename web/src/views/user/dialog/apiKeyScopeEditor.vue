<template>
  <div class="api-key-scope-editor" v-loading="loading">
    <el-alert
      class="scope-note"
      title="API Key 的实际权限会持续与所属用户当前权限取交集；用户权限或资源范围收回后，Key 会立即同步收缩。"
      type="info"
      :closable="false"
      show-icon
    />

    <el-alert
      v-if="loadError"
      class="scope-error"
      title="权限范围选项加载失败，暂时不能保存。"
      type="error"
      :closable="false"
      show-icon
    >
      <el-button slot="default" type="text" size="mini" @click="loadOptions(true)">
        重新加载
      </el-button>
    </el-alert>

    <div class="scope-grid">
      <section class="scope-panel" aria-labelledby="api-key-permission-heading">
        <div class="scope-panel__heading">
          <div>
            <strong id="api-key-permission-heading">功能权限</strong>
            <p>只可勾选启用且带权限编码的节点。</p>
          </div>
          <span>{{ selectedPermissionCount }} 项</span>
        </div>
        <el-input
          v-model.trim="menuKeyword"
          size="small"
          clearable
          prefix-icon="el-icon-search"
          placeholder="筛选功能或权限编码"
          @input="filterMenus"
        />
        <el-tree
          ref="menuTree"
          class="scope-tree"
          :data="menuOptions"
          node-key="id"
          show-checkbox
          check-strictly
          default-expand-all
          :filter-node-method="filterMenuNode"
          :props="menuTreeProps"
          @check="handleMenuCheck"
        >
          <span slot-scope="{ data }" class="scope-tree-node">
            <span class="scope-tree-node__label">
              <i :class="menuTypeIcon(data.menuType)" />
              {{ data.menuName }}
            </span>
            <code v-if="data.perms">{{ data.perms }}</code>
          </span>
        </el-tree>
      </section>

      <section class="scope-panel" aria-labelledby="api-key-resource-heading">
        <div class="scope-panel__heading">
          <div>
            <strong id="api-key-resource-heading">视频资源组</strong>
            <p>父组会动态包含其下级组中的视频资源。</p>
          </div>
          <span>{{ selectedResourceGroupIds.length }} 项</span>
        </div>
        <resource-group-tree
          ref="resourceGroupTree"
          class="scope-resource-tree"
          :groups="resourceGroups"
          :loading="loading"
          :checked-keys="selectedResourceGroupIds"
          checkable
          @checked-change="handleResourceGroupChecked"
        />
      </section>
    </div>

    <section class="ip-panel" aria-labelledby="api-key-ip-heading">
      <div class="scope-panel__heading">
        <div>
          <strong id="api-key-ip-heading">客户端 IP 白名单</strong>
          <p>可选；每行或逗号分隔一个 IPv4、IPv6 或 CIDR。留空表示不限制来源 IP。</p>
        </div>
        <span>{{ parsedIpAllowlist.length }} 项</span>
      </div>
      <el-input
        v-model="ipAllowlistText"
        type="textarea"
        :rows="3"
        maxlength="2000"
        show-word-limit
        placeholder="例如：10.20.0.0/16&#10;2001:db8::/32"
      />
    </section>
  </div>
</template>

<script>
import { getScopeOptions } from '@/api/userApiKey'
import { buildManagementTree, flattenManagementTree, unwrapResponseData } from '@/utils/systemManagement'
import ResourceGroupTree from '@/views/system/resourceGroup/components/ResourceGroupTree.vue'

function normalizeIds(values) {
  if (!Array.isArray(values)) return []
  return [...new Set(values.map(Number).filter(id => id > 0))]
}

export function parseIpAllowlist(value) {
  if (Array.isArray(value)) value = value.join('\n')
  if (value === null || value === undefined || value === '') return []
  return [...new Set(String(value)
    .split(/[,\r\n]+/)
    .map(item => item.trim())
    .filter(Boolean))]
}

export default {
  name: 'ApiKeyScopeEditor',
  components: { ResourceGroupTree },
  data() {
    return {
      loading: false,
      loaded: false,
      loadedOwnerId: null,
      ownerId: null,
      loadError: false,
      menus: [],
      resourceGroups: [],
      menuKeyword: '',
      selectedPermissionMenuIds: [],
      selectedResourceGroupIds: [],
      ipAllowlistText: '',
      menuTreeProps: {
        label: 'menuName',
        children: 'children',
        disabled: 'disabled'
      }
    }
  },
  computed: {
    menuOptions() {
      const mapNode = node => ({
        ...node,
        disabled: node.selectable !== true,
        children: (node.children || []).map(mapNode)
      })
      return buildManagementTree(this.menus).map(mapNode)
    },
    menuById() {
      const result = new Map()
      flattenManagementTree(this.menuOptions).forEach(menu => result.set(Number(menu.id), menu))
      return result
    },
    selectedPermissionCount() {
      return this.selectedPermissionMenuIds.length
    },
    parsedIpAllowlist() {
      return parseIpAllowlist(this.ipAllowlistText)
    }
  },
  methods: {
    initialize(scope = {}, ownerId = null) {
      this.ownerId = ownerId
      this.menuKeyword = ''
      this.selectedPermissionMenuIds = normalizeIds(scope.permissionMenuIds)
      this.selectedResourceGroupIds = normalizeIds(scope.resourceGroupIds)
      this.ipAllowlistText = parseIpAllowlist(scope.ipAllowlist).join('\n')
      return this.loadOptions().then(() => {
        this.syncCheckedKeys()
        return this.value()
      })
    },
    loadOptions(force = false) {
      if (this.loaded && this.loadedOwnerId === this.ownerId && !force) {
        this.loadError = false
        return Promise.resolve()
      }
      this.loading = true
      this.loadError = false
      return getScopeOptions(this.ownerId)
        .then(response => {
          const options = unwrapResponseData(response, {}) || {}
          this.menus = (options.permissionMenus || []).map(menu => ({
            ...menu,
            perms: menu.permissionCode
          }))
          this.resourceGroups = options.resourceGroups || []
          this.loaded = true
          this.loadedOwnerId = this.ownerId
          this.syncCheckedKeys()
        })
        .catch(error => {
          this.loaded = false
          this.loadedOwnerId = null
          this.loadError = true
          throw error
        })
        .finally(() => {
          this.loading = false
        })
    },
    syncCheckedKeys() {
      this.$nextTick(() => {
        if (this.$refs.menuTree) {
          this.$refs.menuTree.setCheckedKeys(this.selectedPermissionMenuIds)
        }
        if (this.$refs.resourceGroupTree) {
          this.$refs.resourceGroupTree.syncCheckedKeys()
        }
      })
    },
    handleMenuCheck(data, state) {
      this.selectedPermissionMenuIds = normalizeIds(state.checkedKeys)
        .filter(id => {
          const menu = this.menuById.get(id)
          return menu && !menu.disabled && String(menu.perms || '').trim()
        })
    },
    handleResourceGroupChecked(checkedKeys) {
      this.selectedResourceGroupIds = normalizeIds(checkedKeys)
    },
    filterMenus(value) {
      if (this.$refs.menuTree) this.$refs.menuTree.filter(value)
    },
    filterMenuNode(value, data) {
      if (!value) return true
      const keyword = String(value).toLowerCase()
      return String(data.menuName || '').toLowerCase().includes(keyword) ||
        String(data.perms || '').toLowerCase().includes(keyword)
    },
    menuTypeIcon(type) {
      if (type === 'F') return 'el-icon-thumb'
      if (type === 'C') return 'el-icon-document'
      return 'el-icon-folder-opened'
    },
    value() {
      const permissionMenuIds = this.$refs.menuTree
        ? normalizeIds(this.$refs.menuTree.getCheckedKeys())
        : normalizeIds(this.selectedPermissionMenuIds)
      const resourceGroupIds = this.$refs.resourceGroupTree
        ? normalizeIds(this.$refs.resourceGroupTree.getCheckedKeys())
        : normalizeIds(this.selectedResourceGroupIds)
      return {
        permissionMenuIds: permissionMenuIds.filter(id => {
          const menu = this.menuById.get(id)
          return menu && !menu.disabled && String(menu.perms || '').trim()
        }),
        resourceGroupIds,
        ipAllowlist: this.parsedIpAllowlist
      }
    },
    validate() {
      if (!this.loaded || this.loadError) return '请先成功加载 API Key 权限范围选项'
      const scope = this.value()
      if (scope.ipAllowlist.length > 64) return '客户端 IP 白名单最多允许 64 项'
      if (scope.resourceGroupIds.length > 0) {
        const hasVideoPermission = scope.permissionMenuIds.some(id => {
          const menu = this.menuById.get(id)
          return menu && String(menu.perms || '').startsWith('video:')
        })
        if (!hasVideoPermission) return '选择视频资源组时，至少勾选一项视频功能权限'
      }
      return null
    }
  }
}
</script>

<style scoped>
.scope-note,
.scope-error {
  margin-bottom: 14px;
}

.scope-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.scope-panel,
.ip-panel {
  min-width: 0;
  padding: 14px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fbfcfe;
}

.ip-panel {
  margin-top: 14px;
}

.scope-panel__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.scope-panel__heading strong {
  color: #303133;
  font-size: 14px;
}

.scope-panel__heading p {
  margin: 4px 0 0;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

.scope-panel__heading > span {
  flex: 0 0 auto;
  color: #606266;
  font-size: 12px;
}

.scope-tree,
.scope-resource-tree {
  height: 260px;
  margin-top: 10px;
  overflow: auto;
  background: transparent;
}

.scope-tree-node {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-right: 8px;
}

.scope-tree-node__label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scope-tree-node code {
  max-width: 58%;
  overflow: hidden;
  color: #7a869a;
  font-family: Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 760px) {
  .scope-grid {
    grid-template-columns: 1fr;
  }
}
</style>

<style>
.api-key-scope-dialog {
  width: min(820px, calc(100vw - 32px)) !important;
}

@media (max-width: 760px) {
  .api-key-scope-dialog .el-dialog__body {
    padding: 16px;
  }
}
</style>
