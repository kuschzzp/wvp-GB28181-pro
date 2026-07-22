<template>
  <main class="permission-denied" aria-labelledby="permission-denied-title">
    <div class="permission-denied__code" aria-hidden="true">403</div>
    <h1 id="permission-denied-title">无权访问此页面</h1>
    <p>当前账号没有访问此页面的权限。你可以返回已授权的首页，或退出后切换其他账号。</p>
    <div class="permission-denied__actions">
      <el-button type="primary" @click="goHome">返回首页</el-button>
      <el-button :loading="loggingOut" @click="logout">退出登录</el-button>
    </div>
  </main>
</template>

<script>
export default {
  name: 'Page403',
  data() {
    return {
      loggingOut: false
    }
  },
  methods: {
    goHome() {
      this.$router.replace('/')
    },
    async logout() {
      this.loggingOut = true
      try {
        await this.$store.dispatch('user/logout')
      } catch (error) {
        await this.$store.dispatch('user/resetToken')
      } finally {
        this.loggingOut = false
        this.$router.replace('/login')
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.permission-denied {
  box-sizing: border-box;
  width: min(560px, calc(100% - 48px));
  margin: 18vh auto 0;
  color: #303133;
  text-align: center;

  &__code {
    margin-bottom: 12px;
    color: #409eff;
    font-size: 72px;
    font-weight: 700;
    line-height: 1;
  }

  h1 {
    margin: 0 0 16px;
    font-size: 24px;
  }

  p {
    margin: 0 auto 28px;
    color: #606266;
    font-size: 14px;
    line-height: 1.8;
  }

  &__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    justify-content: center;
  }
}
</style>
