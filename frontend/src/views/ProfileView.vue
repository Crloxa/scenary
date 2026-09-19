<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { userApi } from '@/api/user'
import { useUserStore } from '@/stores/user'
import { getErrorText } from '@/utils/request'
import { toast } from '@/utils/toast'
import { authApi } from '@/api/auth'
import { socialApi } from '@/api/social'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const userId = computed(() => Number(route.params.id))
const isSelf = ref(false)
const profile = ref(null)
const notFound = ref(false)
const profileError = ref('')
const notesError = ref('')
const brokenAvatar = ref(false)
const brokenCards = ref(new Set())

const cards = ref([])
const nextCursor = ref(undefined)
const hasMore = ref(true)
const loading = ref(false)
let observer

// 编辑资料弹层
const editing = ref(false)
const editForm = reactive({ nickname: '', bio: '' })
const avatarFile = ref(null)
const loggingOut = ref(false)
const followingPending = ref(false)
let loadSeq = 0

// 注销账号弹层（docs/02 §3.8）
const showDeactivate = ref(false)
const deactivatePassword = ref('')
const deactivatingBusy = ref(false)
function openDeactivate() {
  deactivatePassword.value = ''
  showDeactivate.value = true
}
async function confirmDeactivate() {
  if (deactivatingBusy.value) return
  deactivatingBusy.value = true
  try {
    await userApi.deactivateAccount(deactivatePassword.value)
    store.forceLogout()
    showDeactivate.value = false
    toast('账号已注销')
    router.push('/')
  } catch (e) {
    toast(getErrorText(e), 'error')
  } finally {
    deactivatingBusy.value = false
  }
}

async function loadProfile(targetId = userId.value, seq = loadSeq) {
  try {
    const nextProfile = await userApi.profile(targetId)
    if (seq !== loadSeq || targetId !== userId.value) return
    profile.value = nextProfile
  } catch (e) {
    if (seq !== loadSeq || targetId !== userId.value) return
    if (e?.code === 40400) notFound.value = true
    else profileError.value = getErrorText(e)
    return
  }
  profileError.value = ''
  isSelf.value = store.isLoggedIn && store.userId === targetId
  editForm.nickname = profile.value.nickname
  editForm.bio = profile.value.bio
}

async function loadMore(targetId = userId.value, seq = loadSeq) {
  if (loading.value || !hasMore.value) return
  loading.value = true
  try {
    const page = await userApi.notes(targetId, { cursor: nextCursor.value, limit: 12 })
    if (seq !== loadSeq || targetId !== userId.value) return
    const seen = new Set(cards.value.map(c => c.id))
    cards.value.push(...page.list.filter(c => !seen.has(c.id)))
    nextCursor.value = page.nextCursor
    hasMore.value = Boolean(page.hasMore)
    notesError.value = ''
  } catch (e) {
    if (seq === loadSeq && targetId === userId.value) {
      notesError.value = getErrorText(e)
      toast(notesError.value, 'error')
    }
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function setupObserver() {
  observer?.disconnect()
  observer = new IntersectionObserver(
    en => {
      if (en[0].isIntersecting) loadMore(userId.value, loadSeq)
    },
    { rootMargin: '300px' },
  )
  const el = document.getElementById('grid-sentinel')
  if (el) observer.observe(el)
}

async function loadForRoute() {
  const seq = ++loadSeq
  profile.value = null
  brokenAvatar.value = false
  brokenCards.value = new Set()
  notFound.value = false
  profileError.value = ''
  notesError.value = ''
  cards.value = []
  nextCursor.value = undefined
  hasMore.value = true
  loading.value = false
  await Promise.all([loadProfile(userId.value, seq), loadMore(userId.value, seq)])
  if (seq !== loadSeq) return
  await nextTick()
  setupObserver()
}

function markCardImageFailed(id) {
  brokenCards.value = new Set(brokenCards.value).add(id)
}

onMounted(loadForRoute)
watch(() => route.params.id, loadForRoute)
onUnmounted(() => {
  loadSeq++
  observer?.disconnect()
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
    brokenAvatar.value = false
    store.patchProfile({ avatarUrl })
    await loadProfile(userId.value, loadSeq)
    toast('头像已更新')
  } catch (err) {
    toast(getErrorText(err), 'error')
  }
}
async function logout() {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    if (store.accessToken) await authApi.logout()
  } catch {
    // 服务端失败不阻止本地清态
  } finally {
    store.forceLogout()
    loggingOut.value = false
    router.push('/')
  }
}
function fmt(ts) {
  return new Date(Number(ts)).toLocaleDateString()
}
async function toggleFollow() {
  if (!profile.value || isSelf.value) return
  if (!store.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  followingPending.value = true
  try {
    const social = await socialApi.follow(userId.value, !profile.value.social?.following)
    profile.value = { ...profile.value, social }
  } catch (e) {
    toast(getErrorText(e), 'error')
  } finally {
    followingPending.value = false
  }
}
</script>

<template>
  <!-- 404 态：不存在/已删/他人私密 -->
  <div v-if="notFound" class="py-28 text-center text-ink-soft">
    <p class="text-5xl mb-3">🌫</p>
    <p class="mb-6">这位用户不存在，或主页暂时无法查看</p>
    <button class="h-10 px-6 rounded-full bg-brand-500 text-white" @click="router.push('/')">回首页</button>
  </div>

  <div v-else-if="profileError" class="py-28 text-center text-ink-soft">
    <p class="mb-4">{{ profileError }}</p>
    <button class="h-10 px-5 rounded-full border border-line hover:bg-mute" @click="loadForRoute">重试</button>
  </div>

  <div v-else-if="!profile" class="py-28 text-center text-ink-soft">加载中…</div>

  <section v-else-if="profile" class="pt-6">
    <!-- 信息卡 -->
    <header class="flex items-center gap-5 bg-surface rounded-2xl border border-line p-6">
      <button
        v-if="isSelf"
        class="relative shrink-0 group"
        title="更换头像"
        @click="pickAvatar"
      >
        <img v-if="profile.avatarUrl && !brokenAvatar" :src="profile.avatarUrl" alt="" class="w-[72px] h-[72px] rounded-full object-cover bg-brand-50" @error="brokenAvatar = true" />
        <span v-else class="w-[72px] h-[72px] rounded-full bg-brand-50 grid place-items-center text-brand-400 text-xl">{{ (profile.nickname || '山').slice(0,1) }}</span>
        <span class="absolute inset-0 rounded-full grid place-items-center bg-black/35 opacity-0 group-hover:opacity-100 transition text-white text-xs">换</span>
      </button>
      <template v-else>
        <img v-if="profile.avatarUrl && !brokenAvatar" :src="profile.avatarUrl" alt="" class="w-[72px] h-[72px] rounded-full object-cover bg-brand-50 shrink-0" @error="brokenAvatar = true" />
        <span v-else class="w-[72px] h-[72px] rounded-full bg-brand-50 grid place-items-center text-brand-400 text-xl shrink-0">{{ (profile.nickname || '山').slice(0,1) }}</span>
      </template>

      <div class="min-w-0 flex-1">
        <div class="flex items-baseline gap-3">
          <h1 data-testid="profile-nickname" class="text-xl font-semibold truncate">{{ profile.nickname }}</h1>
          <span class="text-xs text-ink-soft">{{ fmt(profile.createdAt) }} 加入</span>
        </div>
        <p class="text-sm text-ink-soft mt-1 line-clamp-2">{{ profile.bio || '这个人很懒，什么都没留下' }}</p>
        <p class="mt-2 text-sm flex items-center gap-4">
          <span><span class="font-semibold">{{ profile.noteCount }}</span><span class="text-ink-soft ml-1">篇笔记</span></span>
          <!-- P16-02 关注关系列表入口 -->
          <router-link
            data-testid="link-followers"
            class="text-ink-soft hover:text-brand-500 transition"
            :to="`/user/${userId}/followers`"
          ><span class="font-semibold text-ink">{{ profile.social?.followerCount || 0 }}</span> 关注者</router-link>
          <router-link
            data-testid="link-following"
            class="text-ink-soft hover:text-brand-500 transition"
            :to="`/user/${userId}/following`"
          ><span class="font-semibold text-ink">{{ profile.social?.followingCount || 0 }}</span> 正在关注</router-link>
        </p>
      </div>

      <div v-if="isSelf" class="self-start flex flex-col gap-2">
        <button data-testid="btn-edit-profile" class="h-9 px-4 rounded-full border border-brand-200 text-brand-600 dark:text-brand-300 hover:bg-brand-50 dark:hover:bg-brand-900/30 text-sm" @click="openEditor">
          编辑资料
        </button>
        <button data-testid="btn-logout" :disabled="loggingOut" class="h-9 px-4 rounded-full border border-line text-ink-soft hover:bg-mute text-sm disabled:opacity-60" @click="logout">
          {{ loggingOut ? '退出中…' : '退出登录' }}
        </button>
        <button data-testid="btn-deactivate" class="h-9 px-4 rounded-full border border-transparent text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 text-sm" @click="openDeactivate">
          注销账号
        </button>
      </div>
      <button
        v-else
        type="button"
        data-testid="btn-follow-user"
        :disabled="followingPending"
        :aria-pressed="Boolean(profile.social?.following)"
        class="self-start h-9 px-4 rounded-full border text-sm transition"
        :class="profile.social?.following ? 'border-brand-400 bg-brand-50 text-brand-600 dark:bg-brand-900/30 dark:text-brand-300' : 'border-brand-200 text-brand-600 hover:bg-brand-50 dark:text-brand-300'"
        @click="toggleFollow"
      >{{ profile.social?.following ? '已关注' : '关注' }} · {{ profile.social?.followerCount || 0 }}</button>
    </header>

    <!-- 编辑弹层 -->
      <div v-if="editing" class="fixed inset-0 z-50 bg-black/40 grid place-items-center px-4" @click.self="editing=false">
      <div class="w-full max-w-sm bg-surface rounded-2xl p-5 space-y-3">
        <h2 class="font-medium">编辑资料</h2>
        <label for="profile-nickname" class="sr-only">昵称</label>
        <input id="profile-nickname" v-model.trim="editForm.nickname" maxlength="32" placeholder="昵称"
               class="w-full h-11 px-3 rounded-xl bg-mute border border-transparent outline-none focus:border-brand-300 text-sm" />
        <label for="profile-bio" class="sr-only">个性签名</label>
        <textarea id="profile-bio" v-model="editForm.bio" rows="3" maxlength="200" placeholder="个性签名"
                  class="w-full p-3 rounded-xl bg-mute border border-transparent outline-none focus:border-brand-300 text-sm resize-none"></textarea>
        <div class="flex justify-end gap-2 pt-1">
          <button class="h-9 px-4 rounded-full text-sm text-ink-soft hover:bg-neutral-50" @click="editing=false">取消</button>
          <button data-testid="btn-save-profile" class="h-9 px-5 rounded-full bg-brand-500 text-white text-sm hover:bg-brand-600" @click="saveProfile">保存</button>
        </div>
      </div>
    </div>
    <input ref="avatarFile" type="file" accept="image/jpeg,image/png" aria-label="上传头像" class="hidden" @change="onAvatarPicked" />

    <!-- 注销账号弹层 -->
    <div v-if="showDeactivate" class="fixed inset-0 z-50 bg-black/40 grid place-items-center px-4" @click.self="showDeactivate=false">
      <div class="w-full max-w-sm bg-surface rounded-2xl p-5 space-y-3" role="dialog" aria-modal="true" aria-labelledby="deactivate-title">
        <h2 id="deactivate-title" class="font-medium">注销账号</h2>
        <p class="text-sm text-ink-soft">注销后你的笔记与评论将不可见，账号无法自行恢复。请输入当前密码确认。</p>
        <label for="deactivate-password" class="sr-only">当前密码</label>
        <input id="deactivate-password" v-model="deactivatePassword" type="password" data-testid="input-deactivate-password"
               placeholder="当前密码" autocomplete="current-password"
               class="w-full h-11 px-3 rounded-xl bg-mute border border-transparent outline-none focus:border-brand-300 text-sm" />
        <div class="flex justify-end gap-2 pt-1">
          <button data-testid="btn-cancel-deactivate" class="h-9 px-4 rounded-full text-sm text-ink-soft hover:bg-neutral-50" @click="showDeactivate=false">取消</button>
          <button data-testid="btn-confirm-deactivate" :disabled="deactivatingBusy || !deactivatePassword"
                  class="h-9 px-5 rounded-full bg-red-500 text-white text-sm hover:bg-red-600 disabled:opacity-60" @click="confirmDeactivate">
            {{ deactivatingBusy ? '注销中…' : '确认注销' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 九宫格 -->
    <div v-if="notesError && cards.length === 0 && !loading" class="py-20 text-center text-ink-soft">
      <p class="mb-4">作品加载失败：{{ notesError }}</p>
      <button class="h-10 px-5 rounded-full border border-line hover:bg-mute" @click="loadMore(userId, loadSeq)">重试</button>
    </div>
    <div v-else-if="cards.length === 0 && !loading" class="py-20 text-center text-ink-soft">
      {{ isSelf ? '还没有作品，点击右上角发布第一篇吧' : 'TA 还没有公开的作品' }}
    </div>
    <div v-if="notesError && cards.length > 0" class="text-center text-sm text-red-500 py-3">
      <span>加载更多失败：{{ notesError }}</span>
      <button class="ml-2 underline" @click="loadMore(userId, loadSeq)">重试</button>
    </div>
    <div v-if="cards.length > 0" class="grid grid-cols-3 gap-2 mt-5">
      <button
        v-for="c in cards"
        :key="c.id"
        class="relative aspect-square rounded-lg overflow-hidden group"
        @click="openNote(c.id)"
      >
        <img v-if="c.coverUrl && !brokenCards.has(c.id)" :src="c.coverUrl" loading="lazy" class="w-full h-full object-cover group-hover:scale-105 transition duration-300" alt="" @error="markCardImageFailed(c.id)" />
        <span v-else role="img" :aria-label="`${c.title || '笔记'}封面暂时无法显示`" class="w-full h-full grid place-items-center bg-mute text-xs text-ink-soft">图片暂时无法显示</span>
        <span v-if="isSelf && c.visibility === 0" class="absolute top-1.5 left-1.5 px-1.5 py-0.5 rounded bg-black/55 text-white text-[10px]">🔒 私密</span>
        <span class="absolute bottom-1 right-1.5 text-white text-[11px] drop-shadow flex items-center gap-0.5">📄 {{ c.mediaCount }}</span>
      </button>
    </div>
    <div id="grid-sentinel" class="h-8"></div>
  </section>
</template>
