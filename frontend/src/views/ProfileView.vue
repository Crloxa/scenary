<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { userApi } from '@/api/user'
import { useUserStore } from '@/stores/user'
import { getErrorText } from '@/utils/request'
import { toast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const userId = Number(route.params.id)
const isSelf = ref(false)
const profile = ref(null)
const notFound = ref(false)

const cards = ref([])
const nextCursor = ref(undefined)
const hasMore = ref(true)
const loading = ref(false)
let observer

// 编辑资料弹层
const editing = ref(false)
const editForm = reactive({ nickname: '', bio: '' })
const avatarFile = ref(null)

async function loadProfile() {
  try {
    profile.value = await userApi.profile(userId)
  } catch {
    notFound.value = true
    return
  }
  isSelf.value = store.isLoggedIn && store.userId === userId
  editForm.nickname = profile.value.nickname
  editForm.bio = profile.value.bio
}

async function loadMore() {
  if (loading.value || !hasMore.value) return
  loading.value = true
  try {
    const page = await userApi.notes(userId, { cursor: nextCursor.value, limit: 12 })
    const seen = new Set(cards.value.map(c => c.id))
    cards.value.push(...page.list.filter(c => !seen.has(c.id)))
    nextCursor.value = page.nextCursor
    hasMore.value = Boolean(page.hasMore)
  } catch (e) {
    toast(getErrorText(e), 'error')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadProfile(), loadMore()])
  observer = new IntersectionObserver(
    en => {
      if (en[0].isIntersecting) loadMore()
    },
    { rootMargin: '300px' },
  )
  const el = document.getElementById('grid-sentinel')
  if (el) observer.observe(el)
})

function openNote(id) {
  router.push(`/note/${id}`)
}
function openEditor() {
  editing.value = true
}
async function saveProfile() {
  try {
    const meVo = await userApi.updateProfile({
      nickname: editForm.nickname,
      bio: editForm.bio,
    })
    profile.value = { ...profile.value, nickname: meVo.nickname, bio: meVo.bio }
    store.patchProfile({ nickname: meVo.nickname })
    editing.value = false
    toast('资料已更新')
  } catch (e) {
    toast(getErrorText(e), 'error')
  }
}
async function pickAvatar() {
  avatarFile.value?.click()
}
async function onAvatarPicked(e) {
  const f = e.target.files?.[0]
  e.target.value = ''
  if (!f) return
  try {
    const { avatarUrl } = await userApi.uploadAvatar(f)
    store.patchProfile({ avatarUrl })
    await loadProfile()
    toast('头像已更新')
  } catch (err) {
    toast(getErrorText(err), 'error')
  }
}
function fmt(ts) {
  return new Date(Number(ts)).toLocaleDateString()
}
</script>

<template>
  <!-- 404 态：不存在/已删/他人私密 -->
  <div v-if="notFound" class="py-28 text-center text-ink-soft">
    <p class="text-5xl mb-3">🌫</p>
    <p class="mb-6">这位用户不存在，或主页暂时无法查看</p>
    <button class="h-10 px-6 rounded-full bg-brand-500 text-white" @click="router.push('/')">回首页</button>
  </div>

  <section v-else-if="profile" class="pt-6">
    <!-- 信息卡 -->
    <header class="flex items-center gap-5 bg-surface rounded-2xl border border-line p-6">
      <button
        v-if="isSelf"
        class="relative shrink-0 group"
        title="更换头像"
        @click="pickAvatar"
      >
        <img
          :src="profile.avatarUrl || ''"
          alt=""
          class="w-[72px] h-[72px] rounded-full object-cover bg-brand-50"
          @error="$event.target.src=''"
        />
        <span class="absolute inset-0 rounded-full grid place-items-center bg-black/35 opacity-0 group-hover:opacity-100 transition text-white text-xs">换</span>
      </button>
      <img
        v-else
        :src="profile.avatarUrl || ''"
        alt=""
        class="w-[72px] h-[72px] rounded-full object-cover bg-brand-50 shrink-0"
      />

      <div class="min-w-0 flex-1">
        <div class="flex items-baseline gap-3">
          <h1 data-testid="profile-nickname" class="text-xl font-semibold truncate">{{ profile.nickname }}</h1>
          <span class="text-xs text-ink-soft">{{ fmt(profile.createdAt) }} 加入</span>
        </div>
        <p class="text-sm text-ink-soft mt-1 line-clamp-2">{{ profile.bio || '这个人很懒，什么都没留下' }}</p>
        <p class="mt-2 text-sm"><span class="font-semibold">{{ profile.noteCount }}</span><span class="text-ink-soft ml-1">篇笔记</span></p>
      </div>

      <div v-if="isSelf" class="self-start flex flex-col gap-2">
        <button data-testid="btn-edit-profile" class="h-9 px-4 rounded-full border border-brand-200 text-brand-600 dark:text-brand-300 hover:bg-brand-50 dark:hover:bg-brand-900/30 text-sm" @click="openEditor">
          编辑资料
        </button>
        <button data-testid="btn-logout" class="h-9 px-4 rounded-full border border-line text-ink-soft hover:bg-mute text-sm" @click="store.forceLogout(); router.push('/')">
          退出登录
        </button>
      </div>
    </header>

    <!-- 编辑弹层 -->
    <div v-if="editing" class="fixed inset-0 z-50 bg-black/40 grid place-items-center px-4" @click.self="editing=false">
      <div class="w-full max-w-sm bg-surface rounded-2xl p-5 space-y-3">
        <h2 class="font-medium">编辑资料</h2>
        <input v-model.trim="editForm.nickname" maxlength="32" placeholder="昵称"
               class="w-full h-11 px-3 rounded-xl bg-mute border border-transparent outline-none focus:border-brand-300 text-sm" />
        <textarea v-model="editForm.bio" rows="3" maxlength="200" placeholder="个性签名"
                  class="w-full p-3 rounded-xl bg-mute border border-transparent outline-none focus:border-brand-300 text-sm resize-none"></textarea>
        <div class="flex justify-end gap-2 pt-1">
          <button class="h-9 px-4 rounded-full text-sm text-ink-soft hover:bg-neutral-50" @click="editing=false">取消</button>
          <button data-testid="btn-save-profile" class="h-9 px-5 rounded-full bg-brand-500 text-white text-sm hover:bg-brand-600" @click="saveProfile">保存</button>
        </div>
      </div>
    </div>
    <input ref="avatarFile" type="file" accept="image/jpeg,image/png,image/webp" class="hidden" @change="onAvatarPicked" />

    <!-- 九宫格 -->
    <div v-if="cards.length === 0 && !loading" class="py-20 text-center text-ink-soft">
      {{ isSelf ? '还没有作品，点击右上角发布第一篇吧' : 'TA 还没有公开的作品' }}
    </div>
    <div v-else class="grid grid-cols-3 gap-2 mt-5">
      <button
        v-for="c in cards"
        :key="c.id"
        class="relative aspect-square rounded-lg overflow-hidden group"
        @click="openNote(c.id)"
      >
        <img :src="c.coverUrl || ''" loading="lazy" class="w-full h-full object-cover group-hover:scale-105 transition duration-300" alt="" />
        <span v-if="isSelf && c.visibility === 0" class="absolute top-1.5 left-1.5 px-1.5 py-0.5 rounded bg-black/55 text-white text-[10px]">🔒 私密</span>
        <span class="absolute bottom-1 right-1.5 text-white text-[11px] drop-shadow flex items-center gap-0.5">📄 {{ c.mediaCount }}</span>
      </button>
    </div>
    <div id="grid-sentinel" class="h-8"></div>
  </section>
</template>
