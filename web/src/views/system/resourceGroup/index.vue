<template>
  <div class="app-container system-resource-group-page">
    <div class="page-heading">
      <div>
        <h2>视频资源组</h2>
        <p>独立组织视频授权资源，不会修改 GB28181 业务分组、行政区划或部门树。</p>
      </div>
      <el-button
        v-hasPermi="['system:resource-group:add']"
        type="primary"
        size="small"
        icon="el-icon-plus"
        :disabled="!selectedGroup || selectedGroup.status === 1"
        @click="openCreate(selectedGroup)"
      >新增下级资源组</el-button>
    </div>

    <el-alert
      class="scope-alert"
      title="角色选择父资源组后，会动态包含全部下级组；选择整台设备或终端后，也会自动包含以后同步的新通道。"
      type="info"
      :closable="false"
      show-icon
    />

    <div class="resource-group-layout">
      <aside class="group-panel">
        <div class="panel-title">
          <div>
            <strong>授权资源组</strong>
            <span>{{ flatGroups.length }} 个节点</span>
          </div>
          <el-button
            type="text"
            icon="el-icon-refresh"
            :loading="treeLoading"
            aria-label="刷新视频资源组"
            @click="loadTree(selectedGroupId)"
          />
        </div>
        <resource-group-tree
          :groups="groups"
          :loading="treeLoading"
          :current-id="selectedGroupId"
          show-actions
          @select="selectGroup"
          @add="openCreate"
          @edit="openEdit"
          @remove="openDelete"
        />
      </aside>

      <section class="resource-panel">
        <template v-if="selectedGroup">
          <div class="resource-panel__header">
            <div>
              <div class="selected-group-title">
                <h3>{{ selectedGroup.groupName }}</h3>
                <el-tag v-if="isRoot(selectedGroup)" size="mini" type="warning">ROOT</el-tag>
                <el-tag :type="selectedGroup.status === 0 ? 'success' : 'info'" size="mini">
                  {{ selectedGroup.status === 0 ? '正常' : '停用' }}
                </el-tag>
              </div>
              <p>
                <code>{{ selectedGroup.groupCode }}</code>
                <span v-if="selectedGroup.description"> · {{ selectedGroup.description }}</span>
              </p>
            </div>
            <div class="resource-actions">
              <el-button
                v-hasPermi="['system:resource-group:edit']"
                size="small"
                icon="el-icon-edit"
                :disabled="isRoot(selectedGroup)"
                @click="openEdit(selectedGroup)"
              >编辑资源组</el-button>
              <el-button
                v-hasPermi="['system:resource-group:assign']"
                size="small"
                type="primary"
                icon="el-icon-plus"
                :disabled="selectedGroup.status === 1"
                @click="openResourceSelector"
              >添加视频资源</el-button>
            </div>
          </div>

          <el-table
            v-loading="itemsLoading || itemsSaving"
            :data="items"
            row-key="key"
            border
            size="small"
            height="calc(100vh - 330px)"
            header-row-class-name="table-header"
            @selection-change="handleSelectionChange"
          >
            <el-table-column type="selection" width="48" />
            <el-table-column label="资源类型" width="135">
              <template slot-scope="scope">
                <el-tag size="mini" effect="plain">{{ resourceTypeLabel(scope.row.resourceType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="资源名称" min-width="220" show-overflow-tooltip>
              <template slot-scope="scope">
                {{ scope.row.label || `${resourceTypeLabel(scope.row.resourceType)} #${scope.row.resourceId}` }}
              </template>
            </el-table-column>
            <el-table-column label="业务标识" min-width="200" show-overflow-tooltip>
              <template slot-scope="scope">{{ scope.row.secondary || '-' }}</template>
            </el-table-column>
            <el-table-column label="内部资源 ID" width="140" align="right">
              <template slot-scope="scope"><code>#{{ scope.row.resourceId }}</code></template>
            </el-table-column>
            <el-table-column label="授权含义" min-width="230">
              <template slot-scope="scope">
                <span v-if="isWholeSource(scope.row.resourceType)" class="warning-text">
                  包含现在及以后新增的通道
                </span>
                <span v-else>仅当前资源</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template slot-scope="scope">
                <el-button
                  v-hasPermi="['system:resource-group:assign']"
                  type="text"
                  size="small"
                  class="danger-action"
                  :disabled="selectedGroup.status === 1"
                  @click="removeItems([scope.row])"
                >移除</el-button>
              </template>
            </el-table-column>
            <template slot="empty">
              <div class="empty-state">当前资源组尚未绑定视频资源</div>
            </template>
          </el-table>

          <div class="table-footer">
            <span>共 {{ items.length }} 项资源</span>
            <el-button
              v-hasPermi="['system:resource-group:assign']"
              size="small"
              type="danger"
              plain
              :disabled="selectedItems.length === 0 || selectedGroup.status === 1"
              @click="removeItems(selectedItems)"
            >移除所选 {{ selectedItems.length ? `(${selectedItems.length})` : '' }}</el-button>
          </div>
        </template>
        <div v-else class="panel-empty">请选择一个视频资源组</div>
      </section>
    </div>

    <el-dialog
      :title="groupForm.id ? '编辑视频资源组' : '新增视频资源组'"
      :visible.sync="groupDialogVisible"
      width="600px"
      append-to-body
      :close-on-click-modal="false"
      @closed="resetGroupForm"
    >
      <el-form ref="groupForm" :model="groupForm" :rules="groupRules" label-width="110px" size="small">
        <el-form-item label="上级资源组" prop="parentId">
          <el-select v-model="groupForm.parentId" class="full-width" filterable>
            <el-option
              v-for="option in parentOptions"
              :key="option.id"
              :label="`${'· '.repeat(option.treeDepth)}${option.groupName}`"
              :value="option.id"
              :disabled="option.status === 1"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="资源组名称" prop="groupName">
          <el-input v-model.trim="groupForm.groupName" maxlength="64" placeholder="例如：东区摄像头" />
        </el-form-item>
        <el-form-item label="资源组编码" prop="groupCode">
          <el-input v-model.trim="groupForm.groupCode" maxlength="64" placeholder="例如：EAST_AREA" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="显示顺序" prop="orderNum">
              <el-input-number v-model="groupForm.orderNum" :min="0" :max="9999" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="groupForm.status">
                <el-radio :label="0">正常</el-radio>
                <el-radio :label="1">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="说明">
          <el-input
            v-model.trim="groupForm.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="说明该组的授权用途"
          />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="groupDialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="groupSaving" @click="saveGroup">
          保存
        </el-button>
      </span>
    </el-dialog>

    <el-dialog
      title="删除视频资源组"
      :visible.sync="deleteDialogVisible"
      width="520px"
      append-to-body
      :close-on-click-modal="false"
      @closed="resetDeleteForm"
    >
      <el-alert
        :title="`删除“${deleteForm.groupName || ''}”前，请先确认其资源项和角色关系的迁移目标。空组可以不选。`"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-form label-width="100px" size="small" class="delete-form">
        <el-form-item label="迁移到">
          <el-select
            v-model="deleteForm.migrationTargetGroupId"
            class="full-width"
            clearable
            filterable
            placeholder="空组可不选；存在关联时必须选择"
          >
            <el-option
              v-for="option in deleteTargetOptions"
              :key="option.id"
              :label="`${'· '.repeat(option.treeDepth)}${option.groupName}`"
              :value="option.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button size="small" @click="deleteDialogVisible = false">取消</el-button>
        <el-button size="small" type="danger" :loading="groupSaving" @click="confirmDelete">
          删除并迁移
        </el-button>
      </span>
    </el-dialog>

    <resource-item-dialog ref="resourceItemDialog" @selected="addSelectedResources" />
  </div>
</template>

<script>
import {
  createResourceGroup,
  deleteResourceGroup,
  getResourceGroupItems,
  getResourceGroupTree,
  replaceResourceGroupItems,
  updateResourceGroup
} from '@/api/system/resourceGroup'
import {
  collectSubtreeIds,
  flattenManagementTree,
  unwrapResponseData
} from '@/utils/systemManagement'
import {
  mergeResourceItems,
  normalizeResourceItems,
  resourceItemKey,
  serializeResourceItems
} from '@/utils/videoResourceAuthorization'
import ResourceGroupTree from './components/ResourceGroupTree.vue'
import ResourceItemDialog from './components/ResourceItemDialog.vue'

function initialGroupForm() {
  return {
    id: null,
    parentId: null,
    groupCode: '',
    groupName: '',
    orderNum: 0,
    status: 0,
    description: ''
  }
}

export default {
  name: 'SystemResourceGroup',
  components: { ResourceGroupTree, ResourceItemDialog },
  data() {
    return {
      treeLoading: false,
      itemsLoading: false,
      itemsSaving: false,
      groupSaving: false,
      groupDialogVisible: false,
      deleteDialogVisible: false,
      groups: [],
      selectedGroupId: null,
      items: [],
      selectedItems: [],
      groupForm: initialGroupForm(),
      deleteForm: {
        id: null,
        groupName: '',
        migrationTargetGroupId: null
      },
      groupRules: {
        parentId: [{ required: true, message: '请选择上级资源组', trigger: 'change' }],
        groupName: [{ required: true, message: '请输入资源组名称', trigger: 'blur' }],
        groupCode: [
          { required: true, message: '请输入资源组编码', trigger: 'blur' },
          {
            pattern: /^[A-Za-z][A-Za-z0-9_-]{1,63}$/,
            message: '编码须以字母开头，仅含字母、数字、下划线和短横线',
            trigger: 'blur'
          }
        ]
      },
      resourceTypeLabels: {
        GB_DEVICE: 'GB 设备',
        CHANNEL: '统一通道',
        JT_TERMINAL: 'JT 终端',
        JT_CHANNEL: 'JT 通道',
        STREAM_PROXY: '拉流代理',
        STREAM_PUSH: '推流'
      }
    }
  },
  computed: {
    flatGroups() {
      return flattenManagementTree(this.groups)
    },
    selectedGroup() {
      return this.flatGroups.find(group => Number(group.id) === Number(this.selectedGroupId)) || null
    },
    parentOptions() {
      const excluded = this.groupForm.id
        ? collectSubtreeIds(this.groups, this.groupForm.id)
        : new Set()
      return this.flatGroups.filter(group => !excluded.has(group.id))
    },
    deleteTargetOptions() {
      return this.flatGroups.filter(group =>
        Number(group.id) !== Number(this.deleteForm.id) && Number(group.status) === 0)
    }
  },
  created() {
    this.loadTree()
  },
  methods: {
    loadTree(preferredId = null) {
      this.treeLoading = true
      return getResourceGroupTree()
        .then(response => {
          this.groups = unwrapResponseData(response, []) || []
          const flat = flattenManagementTree(this.groups)
          const preferred = flat.find(group => Number(group.id) === Number(preferredId))
          const target = preferred || flat[0] || null
          if (target) return this.selectGroup(target)
          this.selectedGroupId = null
          this.items = []
          return null
        })
        .finally(() => {
          this.treeLoading = false
        })
    },
    selectGroup(group) {
      this.selectedGroupId = group.id
      this.selectedItems = []
      return this.loadItems(group.id)
    },
    loadItems(groupId) {
      this.itemsLoading = true
      return getResourceGroupItems(groupId)
        .then(response => {
          this.items = normalizeResourceItems(unwrapResponseData(response, []) || [])
            .map(item => ({ ...item, key: resourceItemKey(item) }))
        })
        .finally(() => {
          this.itemsLoading = false
        })
    },
    isRoot(group) {
      return Number(group.parentId) === 0 || group.groupCode === 'ROOT_VIDEO'
    },
    isWholeSource(type) {
      return type === 'GB_DEVICE' || type === 'JT_TERMINAL'
    },
    resourceTypeLabel(type) {
      return this.resourceTypeLabels[type] || type
    },
    handleSelectionChange(selection) {
      this.selectedItems = selection
    },
    openCreate(parent) {
      if (!parent || Number(parent.status) === 1) {
        this.$message.warning('请选择一个正常状态的上级资源组')
        return
      }
      this.groupForm = { ...initialGroupForm(), parentId: parent.id }
      this.groupDialogVisible = true
      this.clearGroupValidation()
    },
    openEdit(group) {
      if (this.isRoot(group)) return
      this.groupForm = {
        id: group.id,
        parentId: group.parentId,
        groupCode: group.groupCode || '',
        groupName: group.groupName || '',
        orderNum: Number(group.orderNum || 0),
        status: Number(group.status || 0),
        description: group.description || ''
      }
      this.groupDialogVisible = true
      this.clearGroupValidation()
    },
    clearGroupValidation() {
      this.$nextTick(() => {
        if (this.$refs.groupForm) this.$refs.groupForm.clearValidate()
      })
    },
    saveGroup() {
      this.$refs.groupForm.validate(valid => {
        if (!valid) return
        const editing = Boolean(this.groupForm.id)
        const payload = {
          parentId: this.groupForm.parentId,
          groupCode: this.groupForm.groupCode,
          groupName: this.groupForm.groupName,
          orderNum: this.groupForm.orderNum,
          status: this.groupForm.status,
          description: this.groupForm.description || null
        }
        this.groupSaving = true
        const request = editing
          ? updateResourceGroup(this.groupForm.id, payload)
          : createResourceGroup(payload)
        request
          .then(response => {
            const result = unwrapResponseData(response, {}) || {}
            const targetId = editing ? this.groupForm.id : result.id
            this.$message.success(editing ? '视频资源组已更新' : '视频资源组已创建')
            this.groupDialogVisible = false
            return this.loadTree(targetId)
          })
          .finally(() => {
            this.groupSaving = false
          })
      })
    },
    openDelete(group) {
      if (this.isRoot(group)) return
      this.deleteForm = {
        id: group.id,
        groupName: group.groupName,
        migrationTargetGroupId: null
      }
      this.deleteDialogVisible = true
    },
    confirmDelete() {
      this.groupSaving = true
      return deleteResourceGroup(this.deleteForm.id, this.deleteForm.migrationTargetGroupId)
        .then(() => {
          this.$message.success('视频资源组已删除，关联资源已按选择迁移')
          this.deleteDialogVisible = false
          return this.loadTree(this.deleteForm.migrationTargetGroupId)
        })
        .finally(() => {
          this.groupSaving = false
        })
    },
    openResourceSelector() {
      if (!this.selectedGroup || Number(this.selectedGroup.status) === 1) return
      this.$refs.resourceItemDialog.open(this.items)
    },
    mergeItems(currentItems, incomingItems) {
      return mergeResourceItems(currentItems, incomingItems)
    },
    addSelectedResources(selected) {
      const nextItems = this.mergeItems(this.items, selected)
      return this.saveItems(nextItems, `已添加 ${nextItems.length - this.items.length} 项视频资源`)
    },
    removeItems(removing) {
      const keys = new Set(normalizeResourceItems(removing).map(resourceItemKey))
      if (keys.size === 0) return Promise.resolve()
      return this.$confirm(
        `确认从“${this.selectedGroup.groupName}”移除所选 ${keys.size} 项资源吗？`,
        '移除视频资源',
        { type: 'warning', confirmButtonText: '移除', cancelButtonText: '取消' }
      ).then(() => {
        const nextItems = this.items.filter(item => !keys.has(resourceItemKey(item)))
        return this.saveItems(nextItems, `已移除 ${keys.size} 项视频资源`)
      }).catch(() => {})
    },
    saveItems(nextItems, successMessage) {
      this.itemsSaving = true
      return replaceResourceGroupItems(
        this.selectedGroupId,
        serializeResourceItems(nextItems)
      ).then(() => {
        this.items = normalizeResourceItems(nextItems)
          .map(item => ({ ...item, key: resourceItemKey(item) }))
        this.selectedItems = []
        this.$message.success(successMessage)
      }).finally(() => {
        this.itemsSaving = false
      })
    },
    resetGroupForm() {
      this.groupForm = initialGroupForm()
      this.groupSaving = false
    },
    resetDeleteForm() {
      this.deleteForm = {
        id: null,
        groupName: '',
        migrationTargetGroupId: null
      }
    }
  }
}
</script>

<style scoped>
.system-resource-group-page {
  min-height: calc(100vh - 84px);
  background: #f5f7fa;
}

.page-heading,
.resource-panel__header,
.panel-title,
.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-heading {
  margin-bottom: 14px;
}

.page-heading h2,
.selected-group-title h3 {
  margin: 0 0 6px;
  color: #1f2d3d;
}

.page-heading h2 {
  font-size: 22px;
}

.page-heading p,
.resource-panel__header p {
  margin: 0;
  color: #7a869a;
  font-size: 13px;
}

.scope-alert {
  margin-bottom: 14px;
}

.resource-group-layout {
  display: grid;
  grid-template-columns: 310px minmax(0, 1fr);
  gap: 16px;
}

.group-panel,
.resource-panel {
  min-width: 0;
  border: 1px solid #e7eaf0;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(31, 45, 61, 0.04);
}

.group-panel {
  display: flex;
  height: calc(100vh - 245px);
  flex-direction: column;
  padding: 16px 12px;
}

.panel-title {
  margin-bottom: 12px;
}

.panel-title > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.panel-title span,
.table-footer {
  color: #909399;
  font-size: 12px;
}

.resource-panel {
  padding: 16px;
}

.resource-panel__header {
  margin-bottom: 14px;
}

.selected-group-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.selected-group-title h3 {
  font-size: 18px;
}

.resource-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
}

.table-footer {
  min-height: 44px;
  margin-top: 10px;
}

.panel-empty {
  display: flex;
  min-height: 420px;
  align-items: center;
  justify-content: center;
  color: #909399;
}

.warning-text {
  color: #b26a00;
}

.danger-action {
  color: #f56c6c;
}

.full-width {
  width: 100%;
}

.delete-form {
  margin-top: 18px;
}

.empty-state {
  padding: 28px 0;
  color: #909399;
}

code {
  color: #52677f;
  font-family: Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

@media (max-width: 900px) {
  .resource-group-layout {
    grid-template-columns: 1fr;
  }

  .group-panel {
    height: 340px;
  }
}

@media (max-width: 680px) {
  .page-heading,
  .resource-panel__header {
    align-items: stretch;
    flex-direction: column;
  }

  .resource-actions {
    flex-wrap: wrap;
  }
}
</style>
