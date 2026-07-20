import store from '@/store'
import { hasPermi } from '@/utils/permission'

export function createHasPermiDirective(grantedProvider) {
  return {
    inserted(el, binding) {
      const granted = grantedProvider()
      if (!hasPermi(binding.value, granted) && el.parentNode) {
        el.parentNode.removeChild(el)
      }
    }
  }
}

export default createHasPermiDirective(() => store.getters.permissions)
