<template>
  <div class="login-container">
    <main class="login-page">
      <section class="login-visual" aria-label="平台状态概览">
        <div class="brand-lockup">
          <span class="brand-mark">WVP</span>
          <div>
            <p class="brand-kicker">GB28181 VIDEO PLATFORM</p>
            <h1>WVP视频平台</h1>
          </div>
        </div>

        <div class="scene-board" aria-hidden="true">
          <div class="radar-panel">
            <div class="radar-surface">
              <span class="radar-line line-x" />
              <span class="radar-line line-y" />
              <span class="radar-node node-a" />
              <span class="radar-node node-b" />
              <span class="radar-node node-c" />
              <span class="radar-node node-d" />
            </div>
          </div>

          <div class="telemetry-panel">
            <div class="telemetry-row is-active">
              <span>SIP</span>
              <strong>REGISTERED</strong>
            </div>
            <div class="telemetry-row">
              <span>MEDIA</span>
              <strong>ONLINE</strong>
            </div>
            <div class="telemetry-row">
              <span>STREAM</span>
              <strong>READY</strong>
            </div>
          </div>
        </div>

        <div class="visual-footer">
          <span class="status-dot" />
          <span>安全通道已就绪</span>
        </div>
      </section>

      <section class="login-panel" aria-label="登录表单">
        <el-form
          ref="loginForm"
          :model="loginForm"
          :rules="loginRules"
          class="login-form"
          auto-complete="on"
          label-position="left"
        >
          <div class="title-container">
            <p class="form-kicker">SECURE SIGN IN</p>
            <h2 class="title">进入控制台</h2>
          </div>

          <el-form-item prop="username">
            <label class="field-label" for="login-username">用户名</label>
            <div class="input-shell">
              <span class="svg-container" aria-hidden="true">
                <svg-icon icon-class="user" />
              </span>
              <el-input
                id="login-username"
                ref="username"
                v-model="loginForm.username"
                aria-label="用户名"
                placeholder="请输入用户名"
                name="username"
                type="text"
                tabindex="1"
                auto-complete="on"
              />
            </div>
          </el-form-item>

          <el-form-item prop="password">
            <label class="field-label" for="login-password">密码</label>
            <div class="input-shell">
              <span class="svg-container" aria-hidden="true">
                <svg-icon icon-class="password" />
              </span>
              <el-input
                id="login-password"
                :key="passwordType"
                ref="password"
                v-model="loginForm.password"
                :type="passwordType"
                aria-label="密码"
                placeholder="请输入密码"
                name="password"
                tabindex="2"
                auto-complete="on"
                @keyup.enter.native="handleLogin"
              />
              <button
                class="show-pwd"
                type="button"
                :aria-label="passwordType === 'password' ? '显示密码' : '隐藏密码'"
                :title="passwordType === 'password' ? '显示密码' : '隐藏密码'"
                @click="showPwd"
              >
                <svg-icon :icon-class="passwordType === 'password' ? 'eye' : 'eye-open'" />
              </button>
            </div>
          </el-form-item>

          <el-button
            :loading="loading"
            type="primary"
            class="login-button"
            @click.native.prevent="handleLogin"
          >
            进入系统
          </el-button>

          <p class="security-note">
            <span class="status-dot" />
            授权访问
          </p>
        </el-form>
      </section>
    </main>
  </div>
</template>

<script>
import { validUsername } from '@/utils/validate'

export default {
  name: 'Login',
  data() {
    const validateUsername = (rule, value, callback) => {
      if (!validUsername(value)) {
        callback(new Error('请输入用户名'))
      } else {
        callback()
      }
    }
    const validatePassword = (rule, value, callback) => {
      callback()
    }
    return {
      loginForm: {
        username: '',
        password: ''
      },
      loginRules: {
        username: [{ required: true, trigger: 'blur', validator: validateUsername }],
        password: [{ required: true, trigger: 'blur', validator: validatePassword }]
      },
      loading: false,
      passwordType: 'password',
      redirect: undefined
    }
  },
  watch: {
    $route: {
      handler: function(route) {
        this.redirect = route.query && route.query.redirect
      },
      immediate: true
    }
  },
  methods: {
    showPwd() {
      if (this.passwordType === 'password') {
        this.passwordType = 'text'
      } else {
        this.passwordType = 'password'
      }
      this.$nextTick(() => {
        this.$refs.password.focus()
      })
    },
    handleLogin() {
      this.$refs.loginForm.validate(valid => {
        if (valid) {
          this.loading = true
          this.$store.dispatch('user/login', this.loginForm).then(() => {
            this.$router.push({ path: this.redirect || '/' })
            this.loading = false
          }).catch(error => {
            const message = typeof error === 'string'
              ? error
              : (error && error.message) || '登录失败'
            this.$message({
              showClose: true,
              message,
              type: 'error'
            })
          }).finally(() => {
            this.loading = false
          })
        } else {
          console.log('error submit!!')
          return false
        }
      })
    }
  }
}
</script>

<style lang="scss">
$login_text: #f4f7fb;
$login_focus: rgba(35, 211, 176, 0.32);
$login_cursor: #ffffff;

/* reset element-ui css */
.login-container {
  .el-form-item {
    margin-bottom: 22px;
  }

  .el-form-item__content {
    line-height: 1;
  }

  .el-form-item__error {
    color: #ffb4a8;
    padding-top: 7px;
  }

  .el-input {
    display: block;
    width: 100%;

    input {
      height: 46px;
      padding: 0 48px 0 10px;
      color: $login_text;
      caret-color: $login_cursor;
      background: transparent;
      border: 0;
      border-radius: 0;
      -webkit-appearance: none;

      &::placeholder {
        color: rgba(166, 181, 201, 0.72);
      }

      &:-webkit-autofill {
        box-shadow: 0 0 0 1000px #101722 inset !important;
        -webkit-text-fill-color: $login_cursor !important;
      }
    }
  }

  .el-button.login-button {
    width: 100%;
    min-height: 46px;
    margin-top: 4px;
    color: #07120f;
    font-weight: 700;
    background: linear-gradient(135deg, #23d3b0 0%, #92e6ff 100%);
    border: 0;
    border-radius: 6px;
    box-shadow: 0 16px 38px rgba(16, 197, 175, 0.22);
    transition:
      box-shadow 0.2s ease,
      transform 0.2s ease,
      filter 0.2s ease;

    &:hover,
    &:focus {
      color: #07120f;
      background: linear-gradient(135deg, #34e0bf 0%, #a8ebff 100%);
      box-shadow:
        0 0 0 3px $login_focus,
        0 18px 44px rgba(16, 197, 175, 0.3);
      transform: translateY(-1px);
    }

    &:active {
      transform: translateY(0);
      filter: brightness(0.96);
    }

    &.is-loading,
    &.is-disabled {
      color: rgba(7, 18, 15, 0.78);
      background: linear-gradient(135deg, rgba(35, 211, 176, 0.68), rgba(146, 230, 255, 0.68));
      box-shadow: none;
      transform: none;
    }
  }
}
</style>

<style lang="scss" scoped>
$page_bg: #070a10;
$panel_bg: rgba(8, 13, 20, 0.78);
$panel_strong: rgba(11, 18, 28, 0.94);
$border: rgba(149, 171, 205, 0.2);
$text: #f4f7fb;
$muted: #a6b5c9;
$soft: rgba(244, 247, 251, 0.68);
$cyan: #23d3b0;
$sky: #92e6ff;
$amber: #f2c15b;

.login-container {
  width: 100%;
  min-height: 100%;
  overflow: hidden;
  color: $text;
  background: $page_bg;
  letter-spacing: 0;
}

.login-page {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 448px);
  gap: 56px;
  align-items: center;
  min-height: 100vh;
  padding: 56px min(7vw, 92px);
  isolation: isolate;
  background:
    linear-gradient(90deg, rgba(7, 10, 16, 0.92) 0%, rgba(7, 10, 16, 0.74) 48%, rgba(7, 10, 16, 0.9) 100%),
    linear-gradient(135deg, rgba(35, 211, 176, 0.08), rgba(242, 193, 91, 0.05)),
    url('/static/images/bg19.webp') center center / cover no-repeat;

  &::before,
  &::after {
    position: absolute;
    inset: 0;
    z-index: -1;
    pointer-events: none;
    content: '';
  }

  &::before {
    background-image:
      linear-gradient(rgba(146, 230, 255, 0.08) 1px, transparent 1px),
      linear-gradient(90deg, rgba(146, 230, 255, 0.08) 1px, transparent 1px);
    background-size: 72px 72px;
    mask-image: linear-gradient(90deg, rgba(0, 0, 0, 0.68), rgba(0, 0, 0, 0.12));
  }

  &::after {
    background:
      linear-gradient(180deg, rgba(255, 255, 255, 0.06), transparent 14%),
      linear-gradient(0deg, rgba(0, 0, 0, 0.34), transparent 34%);
  }
}

.login-visual {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 560px;
}

.brand-lockup {
  display: flex;
  align-items: center;
  gap: 18px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 72px;
  height: 72px;
  color: #07120f;
  font-size: 21px;
  font-weight: 800;
  line-height: 1;
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.92), rgba(35, 211, 176, 0.84)),
    $cyan;
  border: 1px solid rgba(255, 255, 255, 0.52);
  border-radius: 10px;
  box-shadow:
    0 0 0 1px rgba(35, 211, 176, 0.28),
    0 18px 56px rgba(35, 211, 176, 0.28);
}

.brand-kicker,
.form-kicker {
  margin: 0 0 10px;
  color: $cyan;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: 0;
}

h1 {
  max-width: 680px;
  margin: 0;
  color: $text;
  font-size: 48px;
  font-weight: 800;
  line-height: 1.08;
  letter-spacing: 0;
}

.scene-board {
  display: grid;
  grid-template-columns: minmax(280px, 460px) minmax(220px, 300px);
  gap: 18px;
  align-items: end;
  max-width: 800px;
  margin-top: 62px;
}

.radar-panel,
.telemetry-panel,
.login-form {
  border: 1px solid $border;
  border-radius: 8px;
  background: $panel_bg;
  box-shadow:
    0 0 0 1px rgba(35, 211, 176, 0.08),
    0 28px 86px rgba(0, 0, 0, 0.42);
  backdrop-filter: blur(22px);
  -webkit-backdrop-filter: blur(22px);
}

.radar-panel {
  padding: 18px;
}

.radar-surface {
  position: relative;
  min-height: 300px;
  overflow: hidden;
  border: 1px solid rgba(146, 230, 255, 0.22);
  border-radius: 6px;
  background:
    linear-gradient(rgba(146, 230, 255, 0.1) 1px, transparent 1px),
    linear-gradient(90deg, rgba(146, 230, 255, 0.1) 1px, transparent 1px),
    linear-gradient(135deg, rgba(35, 211, 176, 0.12), rgba(242, 193, 91, 0.06)),
    rgba(8, 13, 20, 0.74);
  background-size: 32px 32px, 32px 32px, auto, auto;

  &::before {
    position: absolute;
    inset: 34px 48px;
    border: 1px solid rgba(35, 211, 176, 0.2);
    border-radius: 6px;
    content: '';
  }
}

.radar-line {
  position: absolute;
  background: linear-gradient(90deg, transparent, rgba(146, 230, 255, 0.64), transparent);
}

.line-x {
  top: 48%;
  left: 12%;
  width: 74%;
  height: 1px;
}

.line-y {
  top: 18%;
  left: 48%;
  width: 1px;
  height: 64%;
  background: linear-gradient(180deg, transparent, rgba(146, 230, 255, 0.64), transparent);
}

.radar-node {
  position: absolute;
  width: 12px;
  height: 12px;
  border: 2px solid rgba(7, 18, 15, 0.92);
  border-radius: 50%;
  background: $cyan;
  box-shadow:
    0 0 0 5px rgba(35, 211, 176, 0.16),
    0 0 18px rgba(35, 211, 176, 0.56);
}

.node-a {
  top: 22%;
  left: 28%;
}

.node-b {
  top: 42%;
  left: 68%;
  background: $amber;
  box-shadow:
    0 0 0 5px rgba(242, 193, 91, 0.16),
    0 0 18px rgba(242, 193, 91, 0.56);
}

.node-c {
  right: 22%;
  bottom: 21%;
}

.node-d {
  bottom: 28%;
  left: 18%;
  background: $sky;
}

.telemetry-panel {
  display: grid;
  gap: 10px;
  padding: 16px;
}

.telemetry-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  padding: 0 12px;
  color: $muted;
  font-size: 12px;
  background: rgba(244, 247, 251, 0.04);
  border: 1px solid rgba(149, 171, 205, 0.12);
  border-radius: 5px;
  font-variant-numeric: tabular-nums;

  strong {
    color: $text;
    font-size: 12px;
  }

  &.is-active {
    border-color: rgba(35, 211, 176, 0.42);
    box-shadow: inset 3px 0 0 $cyan;
  }
}

.visual-footer,
.security-note {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  color: $soft;
  font-size: 13px;
  line-height: 1.5;
}

.status-dot {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: $cyan;
  box-shadow: 0 0 0 4px rgba(35, 211, 176, 0.14);
}

.login-panel {
  width: 100%;
}

.login-form {
  width: 100%;
  padding: 34px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.08), transparent 35%),
    $panel_strong;
}

.title-container {
  margin-bottom: 30px;
}

.title {
  margin: 0;
  color: $text;
  font-size: 28px;
  font-weight: 800;
  line-height: 1.18;
  letter-spacing: 0;
}

.field-label {
  display: block;
  margin-bottom: 9px;
  color: $muted;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.4;
}

.input-shell {
  position: relative;
  display: flex;
  align-items: center;
  min-height: 48px;
  overflow: hidden;
  background: rgba(5, 9, 15, 0.68);
  border: 1px solid rgba(149, 171, 205, 0.18);
  border-radius: 6px;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease;

  &:hover {
    border-color: rgba(146, 230, 255, 0.34);
  }

  &:focus-within {
    border-color: rgba(35, 211, 176, 0.74);
    box-shadow:
      0 0 0 3px rgba(35, 211, 176, 0.18),
      0 0 22px rgba(35, 211, 176, 0.16);
  }
}

.svg-container {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  min-width: 42px;
  color: $muted;
  font-size: 16px;
}

.show-pwd {
  position: absolute;
  top: 50%;
  right: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  padding: 0;
  color: $muted;
  cursor: pointer;
  background: rgba(244, 247, 251, 0.04);
  border: 1px solid transparent;
  border-radius: 5px;
  transform: translateY(-50%);
  transition:
    color 0.2s ease,
    background-color 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease;

  &:hover {
    color: $text;
    background: rgba(146, 230, 255, 0.08);
    border-color: rgba(146, 230, 255, 0.2);
  }

  &:focus-visible {
    color: $text;
    border-color: rgba(35, 211, 176, 0.72);
    box-shadow: 0 0 0 3px rgba(35, 211, 176, 0.2);
  }
}

.security-note {
  display: flex;
  justify-content: center;
  margin-top: 18px;
}

@media (prefers-reduced-motion: reduce) {
  .login-container *,
  .login-container *::before,
  .login-container *::after {
    transition-duration: 0.01ms !important;
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    scroll-behavior: auto !important;
  }
}

@media (max-width: 1100px) {
  .login-page {
    grid-template-columns: minmax(0, 1fr) minmax(340px, 420px);
    gap: 34px;
    padding: 40px;
  }

  .scene-board {
    grid-template-columns: 1fr;
    max-width: 520px;
  }

  .telemetry-panel {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .telemetry-row {
    display: grid;
    gap: 4px;
    justify-items: start;
    min-height: 54px;
  }
}

@media (max-width: 860px) {
  .login-page {
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 28px;
    min-height: 100vh;
    padding: 28px 20px;
  }

  .login-visual {
    min-height: auto;
    width: 100%;
    max-width: 448px;
  }

  .brand-mark {
    width: 56px;
    height: 56px;
    font-size: 17px;
    border-radius: 8px;
  }

  h1 {
    font-size: 32px;
  }

  .brand-kicker {
    margin-bottom: 6px;
    font-size: 11px;
  }

  .scene-board {
    display: none;
  }

  .visual-footer {
    margin-top: 18px;
  }

  .login-panel {
    max-width: 448px;
  }

  .login-form {
    padding: 28px 22px;
  }
}

@media (max-width: 420px) {
  .login-page {
    padding: 22px 14px;
  }

  .brand-lockup {
    gap: 12px;
  }

  .title {
    font-size: 24px;
  }

  .login-form {
    padding: 24px 18px;
  }
}
</style>
