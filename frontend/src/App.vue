<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import TopNav from '@/components/TopNav.vue'

const toasts = ref([])
let seq = 0
function onToast(e) {
  const { type = 'info', msg } = e.detail || {}
  const id = ++seq
  toasts.value.push({ id, type, msg })
  setTimeout(() => {
    toasts.value = toasts.value.filter(t => t.id !== id)
  }, 2600)
}
onMounted(() => window.addEventListener('app-toast', onToast))
onUnmounted(() => window.removeEventListener('app-toast', onToast))
</script>

<template>
  <div class="min-h-screen flex flex-col">
    <TopNav />
    <main class="flex-1 w-full max-w-[1100px] mx-auto px-4 pb-16">
      <RouterView />
    </main>

    <!-- 轻量全局提示栈 -->
    <div class="fixed left-1/2 -translate-x-1/2 bottom-8 z-50 space-y-2 pointer-events-none">
      <TransitionGroup name="toast">
        <div
          v-for="t in toasts"
          :key="t.id"
          class="px-4 py-2 rounded-full shadow-lg text-sm text-white"
          :class="t.type === 'error' ? 'bg-red-500/95' : 'bg-neutral-800/95'"
        >
          {{ t.msg }}
        </div>
      </TransitionGroup>
    </div>
  </div>
</template>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition: all 0.25s ease;
}
.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
