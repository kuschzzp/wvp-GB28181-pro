<template>
  <div id="channelList" style="height: calc(100vh - 124px);">
    <div v-if="!jtChannel">
      <el-form :inline="true" size="mini">
        <el-form-item style="margin-right: 2rem">
          <el-page-header content="通道列表" @back="showDevice" />
        </el-form-item>
        <el-form-item label="搜索">
          <el-input
            v-model="searchSrt"
            style="margin-right: 1rem; width: auto;"
            placeholder="关键字"
            prefix-icon="el-icon-search"
            clearable
            @input="search"
          />
        </el-form-item>
        <el-form-item>
          <el-button v-hasPermi="['video:jt:edit']" icon="el-icon-plus" size="mini" style="margin-right: 1rem;" type="primary" @click="add">添加通道</el-button>
        </el-form-item>
        <el-form-item style="float: right;">
          <el-button icon="el-icon-refresh-right" circle @click="refresh()" />
        </el-form-item>
      </el-form>
      <el-container v-loading="isLoging" style="height: 82vh;">
        <el-main style="padding: 5px;">
          <el-table
            ref="channelListTable"
            :data="deviceChannelList"
            :height="winHeight"
            style="width: 100%"
            header-row-class-name="table-header"
          >
            <el-table-column prop="channelId" label="通道编号" min-width="180" />
            <el-table-column prop="name" label="名称" min-width="180" />
            <el-table-column label="快照" min-width="100">
              <template v-slot:default="scope">
                <secured-image
                  source-type="jt"
                  :device-id="device && device.phoneNumber ? device.phoneNumber : ''"
                  :channel-id="scope.row.channelId"
                  fit="contain"
                  width="60px"
                  height="45px"
                  :click-to-load="true"
                />
              </template>
            </el-table-column>
            <el-table-column label="开启音频" min-width="100">
              <template slot-scope="scope">
                <el-switch
                  v-model="scope.row.hasAudio"
                  active-color="#409EFF"
                  :disabled="!$auth.hasPermi(['video:jt:edit'])"
                  @change="updateChannel(scope.row)"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="340" fixed="right">
              <template slot-scope="scope">
                <el-button
                  v-hasPermi="['video:jt:play']"
                  size="medium"
                  :disabled="device == null || !device.status"
                  icon="el-icon-video-play"
                  type="text"
                  @click="sendDevicePush(scope.row)"
                >播放
                </el-button>
                <el-button
                  v-if="!!scope.row.stream"
                  v-hasPermi="['video:jt:play']"
                  size="medium"
                  :disabled="device == null || !device.status"
                  icon="el-icon-switch-button"
                  type="text"
                  style="color: #f56c6c"
                  @click="stopDevicePush(scope.row)"
                >停止
                </el-button>
                <el-button
                  v-hasPermi="['video:jt:record']"
                  size="medium"
                  :disabled="device == null || !device.status"
                  icon="el-icon-camera"
                  type="text"
                  @click="shooting(scope.row)"
                >抓图
                </el-button>
                <el-divider direction="vertical" />
                <el-button
                  v-hasPermi="['video:jt:edit']"
                  size="medium"
                  type="text"
                  icon="el-icon-edit"
                  @click="handleEdit(scope.row)"
                >
                  编辑
                </el-button>
                <el-divider direction="vertical" />
                <el-dropdown
                  v-if="$auth.hasPermi(['video:jt:record', 'video:cloud-record:list'])"
                  @command="(command)=>{moreClick(command, scope.row)}"
                >
                  <el-button size="medium" type="text">
                    更多功能<i class="el-icon-arrow-down el-icon--right" />
                  </el-button>
                  <el-dropdown-menu slot="dropdown">
                    <el-dropdown-item
                      v-if="$auth.hasPermi(['video:jt:record'])"
                      command="records"
                      :disabled="device == null || !device.status"
                    >
                      设备录像</el-dropdown-item>
                    <el-dropdown-item
                      v-if="$auth.hasPermi(['video:cloud-record:list'])"
                      command="cloudRecords"
                      :disabled="device == null || !device.status"
                    >
                      云端录像</el-dropdown-item>
                  </el-dropdown-menu>
                </el-dropdown>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            style="float: right"
            :current-page="currentPage"
            :page-size="count"
            :page-sizes="[15, 25, 35, 50]"
            layout="total, sizes, prev, pager, next"
            :total="total"
            @size-change="handleSizeChange"
            @current-change="currentChange"
          />
        </el-main>
      </el-container>
    </div>
    <devicePlayer
      v-if="$auth.hasPermi(['video:jt:play', 'video:jt:talk', 'video:jt:ptz'])"
      ref="devicePlayer"
    />
    <channelEdit v-if="jtChannel" ref="channelEdit" :jt-channel="jtChannel" :close-edit="closeEdit" />
  </div>
</template>

<script>
import devicePlayer from '../dialog/jtDevicePlayer.vue'
import channelEdit from './edit.vue'
import dayjs from 'dayjs'
import SecuredImage from '@/components/SecuredImage'
import { issueJtSnapshotTicket } from '@/api/mediaAccess'
import { downloadTicketAsFile } from '@/utils/downloadTicket'

export default {
  name: 'ChannelList',
  components: {
    channelEdit,
    devicePlayer,
    SecuredImage
  },
  props: {
    deviceId: {
      type: String,
      default: null
    }
  },
  data() {
    return {
      device: null,
      deviceChannelList: [],
      updateLooper: 0, // 数据刷新轮训标志
      searchSrt: '',
      channelType: '',
      online: '',
      winHeight: window.innerHeight - 200,
      currentPage: 1,
      count: 15,
      total: 0,
      beforeUrl: '/jtDeviceList',
      isLoging: false,
      jtChannel: null
    }
  },

  mounted() {
    this.initParam()
    this.initData()
  },
  destroyed() {
    this.$destroy('videojs')
    clearTimeout(this.updateLooper)
  },
  methods: {
    initData: function() {
      this.getDeviceChannelList()
    },
    initParam: function() {
      this.currentPage = 1
      this.count = 15
      this.$store.dispatch('jtDevice/queryDeviceById', this.deviceId)
        .then(data => {
          this.device = data
        })
        .catch(err => {
          console.error(err)
        })
    },
    currentChange: function(val) {
      this.currentPage = val
      this.initData()
    },
    handleSizeChange: function(val) {
      this.count = val
      this.getDeviceChannelList()
    },
    getDeviceChannelList: function() {
      if (typeof (this.deviceId) === 'undefined') return
      this.$store.dispatch('jtDevice/queryChannels', {
        page: this.currentPage,
        count: this.count,
        query: this.searchSrt,
        deviceId: this.deviceId
      })
        .then(data => {
          this.total = data.total
          this.deviceChannelList = data.list
          // 防止出现表格错位
          this.$nextTick(() => {
            this.$refs.channelListTable.doLayout()
          })
        })
    },

    // 通知设备上传媒体流
    sendDevicePush: function(itemData) {
      if (!this.$auth.hasPermi(['video:jt:play'])) return
      this.isLoging = true
      const channelId = itemData.channelId
      console.log('通知设备推流1：' + this.device.phoneNumber + ' : ' + channelId)

      this.$store.dispatch('jtDevice/play', {
        phoneNumber: this.device.phoneNumber,
        channelId: channelId,
        type: 0
      })
        .then(data => {
          itemData.streamId = data.stream
          this.$refs.devicePlayer.openDialog('media', this.device.phoneNumber, channelId, {
            streamInfo: data,
            hasAudio: itemData.hasAudio
          })
          setTimeout(() => {
            this.initData()
          }, 1000)
        })
        .catch((error) => {
          this.$message({
            showClose: true,
            message: error,
            type: 'error'
          })
        })
        .finally(() => {
          this.isLoging = false
        })
    },
    moreClick: function(command, itemData) {
      const permissions = {
        records: 'video:jt:record',
        cloudRecords: 'video:cloud-record:list'
      }
      if (!permissions[command] || !this.$auth.hasPermi([permissions[command]])) return
      if (command === 'records') {
        this.queryRecords(itemData)
      } else if (command === 'cloudRecords') {
        this.queryCloudRecords(itemData)
      } else {
        this.$message.info('尚不支持')
      }
    },
    queryRecords: function(itemData) {
      this.$router.push(`/jtDevice/record/${this.device.phoneNumber}/${itemData.channelId}`)
    },
    queryCloudRecords: function(itemData) {
      const deviceId = this.device.phoneNumber
      const channelId = itemData.channelId
      this.$router.push(`/cloudRecord/detail/rtp/jt_${deviceId}_${channelId}`)
    },
    stopDevicePush: function(itemData) {
      if (!this.$auth.hasPermi(['video:jt:play'])) return
      this.$store.dispatch('jtDevice/stopPlay', {
        phoneNumber: this.device.phoneNumber,
        channelId: itemData.channelId
      })
        .then((data) => {
          this.initData()
        })
        .catch((error) => {
          this.$message({
            showClose: true,
            message: error,
            type: 'error'
          })
        })
    },
    showDevice: function() {
      this.$emit('show-device')
    },
    search: function() {
      this.currentPage = 1
      this.total = 0
      this.initData()
    },
    updateChannel: function(row) {
      if (!this.$auth.hasPermi(['video:jt:edit'])) return
      this.$store.dispatch('jtDevice/updateChannel', row)
        .catch((error) => {
          this.$message({
            showClose: true,
            message: error,
            type: 'error'
          })
        })
    },
    refresh: function() {
      this.initData()
    },
    add: function() {
      this.jtChannel = {
        terminalDbId: this.deviceId
      }
    },
    // 编辑
    handleEdit(row) {
      this.jtChannel = row
    },
    // 编辑
    closeEdit(row) {
      this.jtChannel = null
    },
    // 编辑
    shooting(row) {
      if (!this.$auth.hasPermi(['video:jt:record'])) return
      this.$message.success('已申请截图,完成后自动下载', { closed: true })
      const controller = new AbortController()
      const timer = setTimeout(() => {
        this.$message.error('等待截图超时', { closed: true })
        controller.abort('timeout')
      }, 15000)
      issueJtSnapshotTicket(this.device.phoneNumber, row.channelId)
        .then(ticket => downloadTicketAsFile(
          ticket,
          controller.signal,
          `${this.device.phoneNumber}-${row.channelId}-${dayjs().format('YYYYMMDDHHmmss')}.jpg`))
        .catch(error => {
          if (error && error.name === 'AbortError') return
          console.error('下载失败：', error)
        })
        .finally(() => window.clearTimeout(timer))
    }
  }
}
</script>
