<template>
  <div class="resource-group-tree-panel">
    <el-input
      v-model.trim="keyword"
      size="small"
      clearable
      prefix-icon="el-icon-search"
      placeholder="搜索资源组名称或编码"
      @input="filterTree"
    />
    <el-tree
      ref="tree"
      v-loading="loading"
      class="resource-group-tree"
      :data="treeData"
      node-key="id"
      default-expand-all
      highlight-current
      :show-checkbox="checkable"
      check-strictly
      :expand-on-click-node="false"
      :filter-node-method="filterNode"
      :props="treeProps"
      @node-click="selectNode"
      @check="handleCheck"
    >
      <span slot-scope="{ data }" class="resource-group-node">
        <span class="resource-group-node__name">
          <i :class="data.children && data.children.length ? 'el-icon-folder-opened' : 'el-icon-folder'" />
          <span class="resource-group-node__label">{{ data.groupName }}</span>
          <el-tag v-if="isRoot(data)" size="mini" type="warning">ROOT</el-tag>
          <el-tag v-else-if="data.status === 1" size="mini" type="info">停用</el-tag>
          <el-tag v-else-if="inheritedIds.has(Number(data.id))" size="mini" type="success">
            随父组授权
          </el-tag>
        </span>
        <span v-if="showActions" class="resource-group-node__actions" @click.stop>
          <el-button
            v-hasPermi="['system:resource-group:add']"
            type="text"
            size="mini"
            :disabled="data.status === 1"
            @click="$emit('add', data)"
          >新增</el-button>
          <el-button
            v-if="!isRoot(data)"
            v-hasPermi="['system:resource-group:edit']"
            type="text"
            size="mini"
            @click="$emit('edit', data)"
          >编辑</el-button>
          <el-button
            v-if="!isRoot(data)"
            v-hasPermi="['system:resource-group:remove']"
            type="text"
            size="mini"
            class="danger-action"
            @click="$emit('remove', data)"
          >删除</el-button>
        </span>
      </span>
    </el-tree>
  </div>
</template>

<script>
import { collectInheritedResourceGroupIds } from '@/utils/videoResourceAuthorization'

export default {
  name: 'ResourceGroupTree',
  props: {
    groups: {
      type: Array,
      default: () => []
    },
    loading: {
      type: Boolean,
      default: false
    },
    currentId: {
      type: [Number, String],
      default: null
    },
    checkable: {
      type: Boolean,
      default: false
    },
    checkedKeys: {
      type: Array,
      default: () => []
    },
    readonly: {
      type: Boolean,
      default: false
    },
    showActions: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      keyword: '',
      treeProps: {
        label: 'groupName',
        children: 'children',
        disabled: 'disabled'
      }
    }
  },
  computed: {
    treeData() {
      const mapNode = node => ({
        ...node,
        disabled: this.nodeDisabled(node),
        children: (node.children || []).map(mapNode)
      })
      return this.groups.map(mapNode)
    },
    inheritedIds() {
      return collectInheritedResourceGroupIds(this.groups, this.checkedKeys)
    }
  },
  watch: {
    checkedKeys: {
      immediate: true,
      handler() {
        this.syncCheckedKeys()
      }
    },
    currentId: {
      immediate: true,
      handler(value) {
        this.$nextTick(() => {
          if (this.$refs.tree) this.$refs.tree.setCurrentKey(value)
        })
      }
    }
  },
  methods: {
    isRoot(node) {
      return Number(node.parentId) === 0 || node.groupCode === 'ROOT_VIDEO'
    },
    nodeDisabled(node) {
      return this.checkable && (this.readonly || Number(node.status) === 1)
    },
    filterTree(value) {
      if (this.$refs.tree) this.$refs.tree.filter(value)
    },
    filterNode(value, data) {
      if (!value) return true
      const keyword = String(value).toLowerCase()
      return String(data.groupName || '').toLowerCase().includes(keyword) ||
        String(data.groupCode || '').toLowerCase().includes(keyword)
    },
    selectNode(data) {
      this.$emit('select', data)
    },
    handleCheck(data, state) {
      const checkedKeys = [...new Set((state.checkedKeys || []).map(Number))]
      this.$emit('checked-change', checkedKeys)
    },
    syncCheckedKeys() {
      this.$nextTick(() => {
        if (this.checkable && this.$refs.tree) {
          this.$refs.tree.setCheckedKeys(this.checkedKeys)
        }
      })
    },
    getCheckedKeys() {
      if (this.checkable && this.$refs.tree) return this.$refs.tree.getCheckedKeys()
      return [...this.checkedKeys]
    }
  }
}
</script>

<style scoped>
.resource-group-tree-panel {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 12px;
}

.resource-group-tree {
  min-height: 250px;
  flex: 1;
  overflow: auto;
  background: transparent;
}

.resource-group-node {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  padding-right: 8px;
}

.resource-group-node__name {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 5px;
}

.resource-group-node__name > i {
  flex: 0 0 auto;
  color: #8da2bd;
}

.resource-group-node__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resource-group-node__actions {
  display: inline-flex;
  flex: 0 0 auto;
  margin-left: 8px;
  white-space: nowrap;
}

.danger-action {
  color: #f56c6c;
}
</style>
