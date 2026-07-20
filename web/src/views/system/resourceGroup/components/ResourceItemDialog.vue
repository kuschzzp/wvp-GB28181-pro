<template>
  <el-dialog
    title="添加视频资源"
    :visible.sync="visible"
    width="900px"
    append-to-body
    :close-on-click-modal="false"
    @closed="reset"
  >
    <el-alert
      class="resource-type-alert"
      :title="typeHint()"
      :type="wholeSourceType ? 'warning' : 'info'"
      :closable="false"
      show-icon
    />

    <el-tabs v-model="activeType" type="card" @tab-click="changeType">
      <el-tab-pane
        v-for="type in resourceTypes"
        :key="type.value"
        :label="type.label"
        :name="type.value"
      />
    </el-tabs>

    <div class="resource-query-bar">
      <el-select
        v-if="activeType === 'JT_CHANNEL'"
        v-model="jtDeviceId"
        size="small"
        filterable
        class="terminal-select"
        placeholder="先选择 JT1078 终端"
        :loading="terminalLoading"
        @change="search"
      >
        <el-option
          v-for="terminal in terminals"
          :key="terminal.id"
          :label="terminalLabel(terminal)"
          :value="terminal.id"
        />
      </el-select>
      <el-input
        v-model.trim="query"
        size="small"
        clearable
        prefix-icon="el-icon-search"
        placeholder="搜索名称、编号、应用或流 ID"
        @keyup.enter.native="search"
      />
      <el-button size="small" type="primary" icon="el-icon-search" @click="search">
        查询
      </el-button>
      <el-button size="small" icon="el-icon-refresh" :loading="loading" @click="loadResources">
        刷新
      </el-button>
    </div>

    <el-table
      ref="resourceTable"
      v-loading="loading"
      :data="rows"
      row-key="key"
      border
      size="small"
      height="360px"
      @select="toggleRow"
      @select-all="toggleAll"
    >
      <el-table-column type="selection" width="48" :selectable="rowSelectable" />
      <el-table-column label="资源名称" min-width="230" show-overflow-tooltip>
        <template slot-scope="scope">
          <div class="resource-name">
            <span>{{ scope.row.label }}</span>
            <el-tag v-if="existingKeys.has(scope.row.key)" size="mini" type="info">
              已在当前组
            </el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="secondary" label="业务标识" min-width="230" show-overflow-tooltip />
      <el-table-column label="内部资源 ID" width="130" align="right">
        <template slot-scope="scope"><code>#{{ scope.row.resourceId }}</code></template>
      </el-table-column>
      <el-table-column label="状态" width="95" align="center">
        <template slot-scope="scope">
          <el-tag :type="scope.row.online ? 'success' : 'info'" size="mini">
            {{ scope.row.online ? '在线' : '离线/未知' }}
          </el-tag>
        </template>
      </el-table-column>
      <template slot="empty">
        <div class="empty-state">
          {{ activeType === 'JT_CHANNEL' && !jtDeviceId ? '请先选择 JT1078 终端' : '没有匹配的视频资源' }}
        </div>
      </template>
    </el-table>

    <div class="resource-pagination-row">
      <span>本次已选择 {{ selectedItems.size }} 项；重复资源会自动去重。</span>
      <el-pagination
        background
        :current-page="page"
        :page-size="count"
        :page-sizes="[15, 25, 50]"
        layout="total, sizes, prev, pager, next"
        :total="total"
        @size-change="changePageSize"
        @current-change="changePage"
      />
    </div>

    <span slot="footer">
      <el-button size="small" @click="visible = false">取消</el-button>
      <el-button
        size="small"
        type="primary"
        :disabled="selectedItems.size === 0"
        @click="confirm"
      >添加所选资源</el-button>
    </span>
  </el-dialog>
</template>

<script>
import { queryDevices as queryGbDevices } from '@/api/device'
import { getList as queryCommonChannels } from '@/api/commonChannel'
import {
  queryChannels as queryJtChannels,
  queryDevices as queryJtDevices
} from '@/api/jtDevice'
import { queryList as queryStreamProxies } from '@/api/streamProxy'
import { queryList as queryStreamPushes } from '@/api/streamPush'
import { unwrapResponseData } from '@/utils/systemManagement'
import { normalizeResourceItems, resourceItemKey } from '@/utils/videoResourceAuthorization'

const RESOURCE_TYPES = Object.freeze([
  { value: 'GB_DEVICE', label: 'GB 设备' },
  { value: 'CHANNEL', label: '统一通道' },
  { value: 'JT_TERMINAL', label: 'JT 终端' },
  { value: 'JT_CHANNEL', label: 'JT 通道' },
  { value: 'STREAM_PROXY', label: '拉流代理' },
  { value: 'STREAM_PUSH', label: '推流' }
])

export default {
  name: 'ResourceItemDialog',
  data() {
    return {
      visible: false,
      loading: false,
      terminalLoading: false,
      activeType: 'GB_DEVICE',
      resourceTypes: RESOURCE_TYPES,
      rows: [],
      terminals: [],
      jtDeviceId: null,
      query: '',
      page: 1,
      count: 15,
      total: 0,
      existingKeys: new Set(),
      selectedItems: new Map()
    }
  },
  computed: {
    wholeSourceType() {
      return this.activeType === 'GB_DEVICE' || this.activeType === 'JT_TERMINAL'
    }
  },
  methods: {
    open(existingItems = []) {
      this.reset()
      this.existingKeys = new Set(normalizeResourceItems(existingItems).map(resourceItemKey))
      this.visible = true
      return this.loadResources()
    },
    typeHint() {
      if (this.activeType === 'GB_DEVICE' || this.activeType === 'JT_TERMINAL') {
        return '选择整台设备或终端后，会自动包含现在以及以后同步的新通道。'
      }
      if (this.activeType === 'CHANNEL' || this.activeType === 'JT_CHANNEL') {
        return '单通道选择仅授权当前通道，不会扩大为整台设备或终端。'
      }
      return '流资源按当前内部稳定 ID 授权；应用名或流 ID 变化不会自动扩大权限。'
    },
    changeType() {
      this.page = 1
      this.query = ''
      if (this.activeType === 'JT_CHANNEL') {
        return this.loadTerminals().then(() => this.loadResources())
      }
      return this.loadResources()
    },
    search() {
      this.page = 1
      return this.loadResources()
    },
    loadTerminals() {
      if (this.terminals.length > 0) return Promise.resolve(this.terminals)
      this.terminalLoading = true
      return queryJtDevices({ page: 1, count: 100, query: '', online: null })
        .then(response => {
          const page = unwrapResponseData(response, {}) || {}
          this.terminals = Array.isArray(page.list) ? page.list : []
          if (!this.jtDeviceId && this.terminals.length > 0) {
            this.jtDeviceId = this.terminals[0].id
          }
          return this.terminals
        })
        .finally(() => {
          this.terminalLoading = false
        })
    },
    loadResources() {
      if (this.activeType === 'JT_CHANNEL' && !this.jtDeviceId) {
        this.rows = []
        this.total = 0
        return Promise.resolve()
      }
      this.loading = true
      return this.requestResources()
        .then(response => {
          const page = unwrapResponseData(response, {}) || {}
          const list = Array.isArray(page.list) ? page.list : []
          this.rows = list.map(row => this.mapRow(row)).filter(Boolean)
          this.total = Number(page.total || this.rows.length)
          this.restorePageSelection()
        })
        .finally(() => {
          this.loading = false
        })
    },
    requestResources() {
      const common = { page: this.page, count: this.count, query: this.query }
      if (this.activeType === 'GB_DEVICE') {
        return queryGbDevices({ ...common, status: null })
      }
      if (this.activeType === 'CHANNEL') {
        return queryCommonChannels({
          ...common,
          online: null,
          hasRecordPlan: null,
          channelType: null,
          civilCode: null,
          parentDeviceId: null
        })
      }
      if (this.activeType === 'JT_TERMINAL') {
        return queryJtDevices({ ...common, online: null })
      }
      if (this.activeType === 'JT_CHANNEL') {
        return queryJtChannels({ ...common, deviceId: this.jtDeviceId })
      }
      if (this.activeType === 'STREAM_PROXY') {
        return queryStreamProxies({ ...common, pulling: null, mediaServerId: null })
      }
      return queryStreamPushes({ ...common, pushing: null, mediaServerId: null })
    },
    mapRow(row) {
      const resourceId = Number(row && row.id)
      if (!row || resourceId <= 0) return null
      const mapped = {
        resourceType: this.activeType,
        resourceId,
        online: Boolean(row.onLine || row.online || row.pushing || row.pulling)
      }
      if (this.activeType === 'GB_DEVICE') {
        mapped.label = row.name || row.deviceId || `GB 设备 #${resourceId}`
        mapped.secondary = row.deviceId || '-'
      } else if (this.activeType === 'CHANNEL') {
        mapped.label = row.gbName || row.name || row.gbDeviceId || `通道 #${resourceId}`
        mapped.secondary = row.gbDeviceId || row.deviceId || '-'
        mapped.online = row.gbStatus === 'ON' || Boolean(row.online)
      } else if (this.activeType === 'JT_TERMINAL') {
        mapped.label = row.plateNo || row.phoneNumber || `JT 终端 #${resourceId}`
        mapped.secondary = row.phoneNumber || row.terminalId || '-'
      } else if (this.activeType === 'JT_CHANNEL') {
        mapped.label = row.name || row.channelId || `JT 通道 #${resourceId}`
        mapped.secondary = row.channelId || '-'
      } else {
        mapped.label = row.gbName || `${row.app || '-'}/${row.stream || '-'}`
        mapped.secondary = `${row.app || '-'}/${row.stream || '-'}`
      }
      mapped.key = resourceItemKey(mapped)
      return mapped
    },
    terminalLabel(terminal) {
      const name = terminal.plateNo || terminal.phoneNumber || `终端 #${terminal.id}`
      const identity = terminal.phoneNumber || terminal.terminalId || terminal.id
      return `${name}（${identity}）`
    },
    rowSelectable(row) {
      return !this.existingKeys.has(row.key)
    },
    toggleRow(selection, row) {
      const selected = selection.some(item => item.key === row.key)
      if (selected) this.selectedItems.set(row.key, row)
      else this.selectedItems.delete(row.key)
    },
    toggleAll(selection) {
      const selectedKeys = new Set(selection.map(row => row.key))
      this.rows.forEach(row => {
        if (!this.rowSelectable(row)) return
        if (selectedKeys.has(row.key)) this.selectedItems.set(row.key, row)
        else this.selectedItems.delete(row.key)
      })
    },
    restorePageSelection() {
      this.$nextTick(() => {
        if (!this.$refs.resourceTable) return
        this.rows.forEach(row => {
          if (this.selectedItems.has(row.key)) {
            this.$refs.resourceTable.toggleRowSelection(row, true)
          }
        })
      })
    },
    changePage(page) {
      this.page = page
      this.loadResources()
    },
    changePageSize(count) {
      this.count = count
      this.page = 1
      this.loadResources()
    },
    confirm() {
      this.$emit('selected', [...this.selectedItems.values()])
      this.visible = false
    },
    reset() {
      this.loading = false
      this.terminalLoading = false
      this.activeType = 'GB_DEVICE'
      this.rows = []
      this.terminals = []
      this.jtDeviceId = null
      this.query = ''
      this.page = 1
      this.count = 15
      this.total = 0
      this.existingKeys = new Set()
      this.selectedItems = new Map()
    }
  }
}
</script>

<style scoped>
.resource-type-alert {
  margin-bottom: 14px;
}

.resource-query-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.resource-query-bar .el-input {
  min-width: 220px;
  flex: 1;
}

.terminal-select {
  width: 250px;
  flex: 0 0 250px;
}

.resource-name {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.resource-pagination-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 14px;
  color: #7a869a;
  font-size: 12px;
}

code {
  color: #52677f;
  font-family: Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.empty-state {
  padding: 28px 0;
  color: #909399;
}

@media (max-width: 760px) {
  .resource-query-bar,
  .resource-pagination-row {
    align-items: stretch;
    flex-direction: column;
  }

  .terminal-select {
    width: 100%;
    flex-basis: auto;
  }
}
</style>
