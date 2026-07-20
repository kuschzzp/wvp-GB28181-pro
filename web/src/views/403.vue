<template>
  <main class="permission-denied" aria-labelledby="permission-denied-title">
    <div class="permission-denied__code" aria-hidden="true">403</div>
    <h1 id="permission-denied-title">无权访问此页面</h1>
    <p>当前账号没有完成此操作所需的权限。如需访问，请联系管理员调整角色授权。</p>
    <router-link class="permission-denied__home" to="/">返回首页</router-link>
  </main>
</template>

<script>
export default {
  name: 'Page403'
}
</script>

<style lang="scss" scoped>
.permission-denied {
  box-sizing: border-box;
  width: min(520px, calc(100% - 48px));
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

  &__home {
    display: inline-block;
    padding: 10px 24px;
    border-radius: 4px;
    background: #409eff;
    color: #fff;
    text-decoration: none;

    &:hover,
    &:focus-visible {
      background: #66b1ff;
    }

    &:focus-visible {
      outline: 2px solid #409eff;
      outline-offset: 3px;
    }
  }
}
</style>
