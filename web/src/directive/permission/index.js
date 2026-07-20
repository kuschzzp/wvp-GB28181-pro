import hasPermi from './hasPermi'

export default {
  install(Vue) {
    Vue.directive('hasPermi', hasPermi)
  }
}
