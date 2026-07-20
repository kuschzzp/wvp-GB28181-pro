<template>
  <div id="recordDownload">
    <el-dialog v-if="showDialog" v-el-drag-dialog :title="title" width="45rem" :append-to-body="true" :close-on-click-modal="false" :visible.sync="showDialog" :destroy-on-close="true" center @close="close()">
      <div style="display: grid; grid-template-columns: auto 70px">
        <div>
          <el-progress :percentage="percentage" style="height: 28px; line-height: 25px;"/>
        </div>
        <div>
          <el-button v-if="downloadTicket" icon="el-icon-download" size="mini" title="点击下载" @click="downloadFileClientEvent()">下载</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>

import elDragDialog from '@/directive/el-drag-dialog'
import { openDownloadTicket } from '@/utils/downloadTicket'

export default {
  name: 'RecordDownload',
  directives: { elDragDialog },
  data() {
    return {
      title: '下载中...',
      deviceId: '',
      channelId: '',
      app: '',
      stream: '',
      mediaServerId: '',
      showDialog: false,
      scale: 1,
      percentage: 0.00,
      streamInfo: null,
      taskId: null,
      getProgressRun: false,
      timer: null,
      downloadTicket: null
    }
  },
  created() {
    window.addEventListener('beforeunload', this.stopDownloadRecord)
  },
  destroyed() {
    window.removeEventListener('beforeunload', this.stopDownloadRecord)
  },
  methods: {
    openDialog: function(deviceId, channelId, app, stream, mediaServerId) {
      this.deviceId = deviceId
      this.channelId = channelId
      this.app = app
      this.stream = stream
      this.mediaServerId = mediaServerId
      this.showDialog = true
      this.getProgressRun = true
      this.percentage = 0.0
      this.downloadTicket = null
      this.getProgressTimer()
    },
    getProgressTimer: function() {
      if (!this.getProgressRun) {
        return
      }
      if (this.downloadTicket) {
        return
      }
      setTimeout(() => {
        if (!this.showDialog) return
        this.getProgress(this.getProgressTimer)
      }, 5000)
    },
    getProgress: function(callback) {
      this.$store.dispatch('gbRecord/queryDownloadProgress', [
        this.deviceId, this.channelId, this.stream
      ])
        .then(streamInfo => {
          this.streamInfo = streamInfo
          if (parseFloat(streamInfo.progress) === 1) {
            this.percentage = 100
          } else {
            this.percentage = parseFloat((parseFloat(streamInfo.progress) * 100).toFixed(1))
          }
          if (streamInfo.downloadTicket) {
            this.downloadTicket = streamInfo.downloadTicket
            this.percentage = 100
            this.getProgressRun = false
            this.downloadFileClientEvent()
          }
          if (callback)callback()
        })
    },
    close: function() {
      if (this.streamInfo.progress < 1) {
        this.stopDownloadRecord()
      }

      if (this.timer !== null) {
        window.clearTimeout(this.timer)
        this.timer = null
      }
      this.showDialog = false
      this.getProgressRun = false
    },
    gbScale: function(scale) {
      this.scale = scale
    },

    stopDownloadRecord: function(callback) {
      if (this.deviceId && this.channelId && this.stream) {
        this.$axios({
          method: 'get',
          url: '/api/gb_record/download/stop/' + this.deviceId + '/' + this.channelId + '/' + this.stream
        }).then((res) => {
          if (callback) callback(res)
        })
      }
    },
    downloadFileClientEvent: function() {
      if (!this.downloadTicket) return
      const ticket = this.downloadTicket
      this.downloadTicket = null
      openDownloadTicket({
        ...ticket,
        fileName: this.deviceId + '-' + this.channelId + '.mp4'
      })
    }
  }
}
</script>

<style>

</style>
