<template>
  <div id="app" style="width: 100%">
    <el-dialog
      title="ApiKey列表"
      width="80%"
      top="2rem"
      :close-on-click-modal="false"
      :visible.sync="showDialog"
      :destroy-on-close="true"
      @close="close()"
    >
      <el-form :inline="true" size="mini">
        <el-form-item>
          <el-button
            v-hasPermi="['system:apikey:add']"
            icon="el-icon-plus"
            size="mini"
            style="margin-right: 1rem;"
            type="primary"
            @click="addUserApiKey"
          >
            添加ApiKey
          </el-button>
        </el-form-item>
      </el-form>
      <!--ApiKey列表-->
      <el-table
        size="small"
        :data="userList"
        style="width: 100%;font-size: 12px;"
        :height="winHeight"
        header-row-class-name="table-header"
      >
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="app" label="应用名" min-width="160" />
        <el-table-column label="ApiKey" :show-overflow-tooltip="true" min-width="300">
          <template #default="scope">
            <span>{{ formatApiKey(scope.row) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.status)">
              {{ statusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="过期时间" width="160">
          <template #default="scope">
            {{ formatTime(scope.row.expiresAt) }}
          </template>
        </el-table-column>
        <el-table-column label="授权范围" min-width="190" show-overflow-tooltip>
          <template #default="scope">
            {{ scopeSummary(scope.row) }}
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注信息" min-width="160" />
        <el-table-column label="操作" min-width="320" fixed="right">
          <template #default="scope">
            <el-button
              v-if="scope.row.status === 'ACTIVE' || scope.row.status === 'LEGACY'"
              v-hasPermi="['system:apikey:disable']"
              size="medium"
              icon="el-icon-circle-close"
              type="text"
              @click="disableUserApiKey(scope.row)"
            >
              停用
            </el-button>
            <el-button
              v-else-if="scope.row.status === 'DISABLED'"
              v-hasPermi="['system:apikey:disable']"
              size="medium"
              icon="el-icon-circle-check"
              type="text"
              @click="enableUserApiKey(scope.row)"
            >
              启用
            </el-button>
            <el-button
              v-hasPermi="['system:apikey:rotate']"
              size="medium"
              icon="el-icon-key"
              type="text"
              @click="editUserApiKeyScope(scope.row)"
            >
              权限范围
            </el-button>
            <el-button
              v-hasPermi="['system:apikey:rotate']"
              size="medium"
              icon="el-icon-refresh"
              type="text"
              @click="resetUserApiKey(scope.row)"
            >
              重置
            </el-button>
            <el-button
              v-hasPermi="['system:apikey:rotate']"
              size="medium"
              icon="el-icon-edit"
              type="text"
              @click="remarkUserApiKey(scope.row)"
            >
              备注
            </el-button>
            <el-button
              v-hasPermi="['system:apikey:remove']"
              size="medium"
              icon="el-icon-delete"
              type="text"
              style="color: #f56c6c"
              @click="deleteUserApiKey(scope.row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="text-align: right"
        :current-page="currentPage"
        :page-size="count"
        :page-sizes="[15, 25, 35, 50]"
        layout="total, sizes, prev, pager, next"
        :total="total"
        @size-change="handleSizeChange"
        @current-change="currentChange"
      />
    </el-dialog>
    <addUserApiKey ref="addUserApiKey" />
    <remarkUserApiKey ref="remarkUserApiKey" />
    <editUserApiKeyScope ref="editUserApiKeyScope" />
    <apiKeySecret ref="apiKeySecret" />
  </div>
</template>

<script>
import addUserApiKey from './dialog/addUserApiKey.vue'
import remarkUserApiKey from './dialog/remarkUserApiKey.vue'
import editUserApiKeyScope from './dialog/editUserApiKeyScope.vue'
import apiKeySecret from './dialog/apiKeySecret.vue'
import moment from 'moment'

export default {
  name: 'UserApiKeyManager',
  components: {
    addUserApiKey,
    remarkUserApiKey,
    editUserApiKeyScope,
    apiKeySecret
  },
  data() {
    return {
      userList: [], // 设备列表
      currentUser: {}, // 当前操作设备对象
      winHeight: window.innerHeight - 300,
      currentPage: 1,
      count: 15,
      total: 0,
      getUserApiKeyListLoading: false,
      showDialog: false
    }
  },
  mounted() {},
  methods: {
    openDialog: function(userId) {
      this.userId = userId
      this.showDialog = true
      this.initData()
    },
    initData() {
      this.getUserApiKeyList()
    },
    currentChange(val) {
      this.currentPage = val
      this.getUserApiKeyList()
    },
    handleSizeChange(val) {
      this.count = val
      this.getUserApiKeyList()
    },
    getUserApiKeyList() {
      this.getUserApiKeyListLoading = true
      this.$store.dispatch('userApiKeys/queryList', {
        page: this.currentPage,
        count: this.count,
        userId: this.userId
      })
        .then(data => {
          this.total = data.total
          this.userList = data.list
        })
        .finally(() => {
          this.getUserApiKeyListLoading = false
        })
    },
    addUserApiKey() {
      this.$refs.addUserApiKey.openDialog(this.userId, (createdKey) => {
        this.$refs.addUserApiKey.close()
        this.$message({
          showClose: true,
          message: 'ApiKey添加成功',
          type: 'success'
        })
        this.showApiKeySecret(createdKey)
        setTimeout(this.getUserApiKeyList, 200)
      })
    },
    remarkUserApiKey(row) {
      this.$refs.remarkUserApiKey.openDialog(row.id, () => {
        this.$refs.remarkUserApiKey.close()
        this.$message({
          showClose: true,
          message: '备注修改成功',
          type: 'success'
        })
        setTimeout(this.getUserApiKeyList, 200)
      })
    },
    editUserApiKeyScope(row) {
      this.$refs.editUserApiKeyScope.open(row, this.userId, () => {
        this.getUserApiKeyList()
      })
    },
    enableUserApiKey(row) {
      this.$confirm('确定启用此 API Key？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        center: true,
        type: 'warning'
      }).then(() => {
        this.$store.dispatch('userApiKeys/enable', row.id)
          .then(() => {
            this.$message({
              showClose: true,
              message: '启用成功',
              type: 'success'
            })
            this.getUserApiKeyList()
          })
          .catch((error) => {
            this.$message({
              showClose: true,
              message: error,
              type: 'error'
            })
          })
      }).catch(() => {
      })
    },
    disableUserApiKey(row) {
      this.$confirm('确定停用此 API Key？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        center: true,
        type: 'warning'
      }).then(() => {
        this.$store.dispatch('userApiKeys/disable', row.id)
          .then(() => {
            this.$message({
              showClose: true,
              message: '停用成功',
              type: 'success'
            })
            this.getUserApiKeyList()
          })
          .catch((error) => {
            this.$message({
              showClose: true,
              message: '停用失败',
              type: 'error'
            })
            console.error(error)
          })
      }).catch(() => {
      })
    },
    resetUserApiKey(row) {
      this.$confirm('轮换后旧 API Key 立即失效，新密钥只显示一次。确定继续？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        center: true,
        type: 'warning'
      }).then(() => {
        this.$store.dispatch('userApiKeys/reset', row.id)
          .then((createdKey) => {
            this.$message({
              showClose: true,
              message: '重置成功',
              type: 'success'
            })
            this.showApiKeySecret(createdKey)
            this.getUserApiKeyList()
          })
          .catch((error) => {
            this.$message({
              showClose: true,
              message: '重置失败',
              type: 'error'
            })
            console.error(error)
          })
      }).catch(() => {
      })
    },
    deleteUserApiKey(row) {
      this.$confirm('确定删除此 API Key？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        center: true,
        type: 'warning'
      }).then(() => {
        this.$store.dispatch('userApiKeys/remove', row.id)
          .then(() => {
            this.$message({
              showClose: true,
              message: '删除成功',
              type: 'success'
            })
            this.getUserApiKeyList()
          })
          .catch((error) => {
            this.$message({
              showClose: true,
              message: '删除失败',
              type: 'error'
            })
            console.error(error)
          })
      }).catch(() => {
      })
    },
    close() {
      this.showDialog = false
    },
    showApiKeySecret(createdKey) {
      this.$refs.apiKeySecret.open(createdKey)
    },
    formatApiKey(row) {
      if (!row || (!row.prefix && !row.last4)) {
        return '-'
      }
      return `${row.prefix || ''}••••${row.last4 || ''}`
    },
    statusText(status) {
      const statusMap = {
        ACTIVE: '启用',
        DISABLED: '停用',
        REVOKED: '已撤销',
        LEGACY: '待轮换'
      }
      return statusMap[status] || '未知'
    },
    statusTagType(status) {
      const typeMap = {
        ACTIVE: 'success',
        DISABLED: 'info',
        REVOKED: 'danger',
        LEGACY: 'warning'
      }
      return typeMap[status] || 'info'
    },
    formatTime(timestamp) {
      return timestamp ? moment(timestamp).format('YYYY-MM-DD HH:mm:ss') : '永不过期'
    },
    scopeSummary(row) {
      const permissionCount = Array.isArray(row && row.permissionMenuIds)
        ? row.permissionMenuIds.length
        : 0
      const resourceCount = Array.isArray(row && row.resourceGroupIds)
        ? row.resourceGroupIds.length
        : 0
      const ipCount = Array.isArray(row && row.ipAllowlist) ? row.ipAllowlist.length : 0
      return `功能 ${permissionCount} · 资源组 ${resourceCount} · IP ${ipCount || '不限'}`
    }
  }
}
</script>
<style>

</style>
