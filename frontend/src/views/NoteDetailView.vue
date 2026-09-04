<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { noteApi } from '@/api/note'
import { getErrorText } from '@/utils/request'
import { toast } from '@/utils/toast'
import { socialApi } from '@/api/social'
import { useUserStore } from '@/stores/user'
import { commentApi } from '@/api/comment'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const detail = ref(null)
const notFound = ref(false)
const loading = ref(false)
const errorMessage = ref('')
const brokenImages = ref(new Set())
const authorImageFailed = ref(false)
const deleting = ref(false)
const armDelete = ref(false)
let disarmTimer = null
let loadSeq = 0
const socialPending = ref('')
const comments = ref([])
const commentsCursor = ref(undefined)
const commentsHasMore = ref(true)
const commentsLoading = ref(false)
const commentsError = ref('')
const commentContent = ref('')
const commentParent = ref(null)
const commentSubmitting = ref(false)
const commentDeletePending = ref(null)

async function loadDetail(targetId = route.params.id, seq = loadSeq) {
  detail.value = null
  notFound.value = false
  errorMessage.value = ''
  brokenImages.value = new Set()
  authorImageFailed.value = false
  comments.value = []
  commentsCursor.value = undefined
  commentsHasMore.value = true
  commentsError.value = ''
  commentContent.value = ''
  commentParent.value = null
  loading.value = true
  try {
    const nextDetail = await noteApi.detail(targetId)
    if (seq !== loadSeq || targetId !== route.params.id) return
    detail.value = nextDetail
    await loadComments(targetId, seq)
  } catch (e) {
    if (seq !== loadSeq || targetId !== route.params.id) return
    if (e?.code === 40400) notFound.value = true
    else {
      errorMessage.value = getErrorText(e)
      toast(errorMessage.value, 'error')
    }
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

async function loadComments(noteId = route.params.id, seq = loadSeq) {
  if (commentsLoading.value || !commentsHasMore.value) return
  commentsLoading.value = true
  try {
    const page = await commentApi.list(noteId, { cursor: commentsCursor.value, limit: 10 })
    if (seq !== loadSeq || noteId !== route.params.id) return
    const seen = new Set(comments.value.map(comment => comment.id))
    comments.value.push(...page.list.filter(comment => !seen.has(comment.id)))
    commentsCursor.value = page.nextCursor
    commentsHasMore.value = Boolean(page.hasMore)
    commentsError.value = ''
  } catch (e) {
    if (seq === loadSeq && noteId === route.params.id) commentsError.value = getErrorText(e)
  } finally {
    if (seq === loadSeq) commentsLoading.value = false
  }
}

function markImageFailed(mediaId) {
  brokenImages.value = new Set(brokenImages.value).add(mediaId)
}

function reloadForRoute() {
  const seq = ++loadSeq
  armDelete.value = false
  clearTimeout(disarmTimer)
  loadDetail(route.params.id, seq)
}

onMounted(reloadForRoute)
watch(() => route.params.id, reloadForRoute)
onUnmounted(() => {
  loadSeq++
  clearTimeout(disarmTimer)
})

function startReply(comment) {
  if (comment.status !== 1) return
  commentParent.value = comment
  nextTick(() => document.getElementById('comment-input')?.focus())
}

function cancelReply() {
  commentParent.value = null
}

async function submitComment() {
  if (!store.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  const content = commentContent.value.trim()
  if (!content || commentSubmitting.value) return
  const limit = commentParent.value ? 300 : 500
  if ([...content].length > limit) {
    toast(`${commentParent.value ? '回复' : '评论'}最长 ${limit} 字`, 'error')
    return
  }
  commentSubmitting.value = true
  commentsError.value = ''
  try {
    const created = await commentApi.create(detail.value.id, {
      content,
      parentId: commentParent.value?.id ?? null,
    })
    comments.value.push(created)
    commentContent.value = ''
    commentParent.value = null
  } catch (e) {
    commentsError.value = getErrorText(e)
    toast(commentsError.value, 'error')
  } finally {
    commentSubmitting.value = false
  }
}

async function removeComment(comment) {
  if (!comment.mine || comment.status !== 1) return
  if (commentDeletePending.value === comment.id) {
    commentDeletePending.value = null
    try {
      await commentApi.remove(comment.id)
      const index = comments.value.findIndex(item => item.id === comment.id)
      if (index >= 0) comments.value[index] = { ...comment, status: 2, content: '该评论已删除', canDelete: false }
    } catch (e) {
      toast(getErrorText(e), 'error')
    }
    return
  }
  commentDeletePending.value = comment.id
  window.setTimeout(() => {
    if (commentDeletePending.value === comment.id) commentDeletePending.value = null
  }, 2500)
}

async function removeNote() {
  // 两段式确认：第一击武装，2.5 秒内第二击执行
  if (!armDelete.value) {
    armDelete.value = true
    clearTimeout(disarmTimer)
    disarmTimer = setTimeout(() => (armDelete.value = false), 2500)
    return
  }
  clearTimeout(disarmTimer)
  deleting.value = true
  try {
    await noteApi.remove(detail.value.id)
    toast('已删除')
    router.push('/')
  } catch (e) {
    toast(getErrorText(e), 'error')
  } finally {
    deleting.value = false
    armDelete.value = false
  }
}
async function toggleSocial(type) {
  if (!detail.value) return
  if (!store.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  const enabled = type === 'like' ? !detail.value.social?.liked : !detail.value.social?.bookmarked
  if (socialPending.value) return
  socialPending.value = type
  try {
    const social = type === 'like'
      ? await socialApi.like(detail.value.id, enabled)
      : await socialApi.bookmark(detail.value.id, enabled)
    detail.value = { ...detail.value, social }
  } catch (e) {
    toast(getErrorText(e), 'error')
  } finally {
    socialPending.value = ''
  }
}
function fmt(ts) {
  return new Date(Number(ts)).toLocaleString()
}
</script>

<template>
  <div v-if="notFound" class="py-28 text-center text-ink-soft">
    <p class="text-5xl mb-3">🍂</p>
    <p class="mb-6">笔记不存在，或作者已将其设为私密</p>
    <button class="h-10 px-6 rounded-full bg-brand-500 text-white" @click="router.push('/')">回首页</button>
  </div>

  <div v-else-if="loading" class="max-w-[720px] mx-auto pt-6 animate-pulse">
    <div class="h-8 w-2/3 rounded bg-mute"></div>
    <div class="mt-4 h-72 rounded-xl bg-mute"></div>
  </div>

  <div v-else-if="errorMessage" class="py-28 text-center text-ink-soft">
    <p class="mb-4">加载失败：{{ errorMessage }}</p>
    <button class="h-10 px-5 rounded-full border border-line hover:bg-mute" @click="reloadForRoute">重试</button>
  </div>

  <article v-else-if="detail" class="max-w-[720px] mx-auto pt-6">
    <h1 data-testid="note-title" class="text-2xl font-semibold leading-snug">{{ detail.title }}</h1>
    <div class="mt-3 flex items-center gap-3">
      <button class="flex items-center gap-2 group" @click="router.push(`/user/${detail.author.id}`)">
        <img v-if="detail.author.avatarUrl && !authorImageFailed" :src="detail.author.avatarUrl" class="w-9 h-9 rounded-full object-cover" alt="" @error="authorImageFailed = true" />
        <span v-else class="w-9 h-9 rounded-full bg-brand-50 grid place-items-center text-brand-400">{{ (detail.author.nickname||'山').slice(0,1) }}</span>
        <span class="text-sm font-medium group-hover:text-brand-600">{{ detail.author.nickname }}</span>
      </button>
      <span class="text-xs text-ink-soft">{{ fmt(detail.createdAt) }}</span>
      <span v-if="detail.placeName" class="ml-auto text-xs px-2.5 py-1 rounded-full bg-brand-50 text-brand-600">📍 {{ detail.placeName }}</span>
      <button
        v-if="detail.mine"
        data-testid="btn-del-note"
        :disabled="deleting"
        class="ml-auto text-xs h-8 px-3 rounded-full border transition"
        :class="armDelete ? 'bg-red-500 text-white border-red-500' : 'border-red-200 text-red-500 hover:bg-red-50'"
        @click="removeNote"
      >
        {{ armDelete ? '再点一次确认删除' : '删除' }}
      </button>
    </div>

    <div class="mt-4 flex flex-wrap items-center gap-2 border-y border-line py-3">
      <button
        type="button"
        data-testid="btn-like-note"
        :disabled="Boolean(socialPending)"
        :aria-pressed="Boolean(detail.social?.liked)"
        class="h-9 px-4 rounded-full border transition text-sm"
        :class="detail.social?.liked ? 'border-brand-400 bg-brand-50 text-brand-600 dark:bg-brand-900/30 dark:text-brand-300' : 'border-line hover:bg-mute'"
        @click="toggleSocial('like')"
      >♥ {{ detail.social?.liked ? '已赞' : '点赞' }} {{ detail.social?.likeCount || 0 }}</button>
      <button
        type="button"
        data-testid="btn-bookmark-note"
        :disabled="Boolean(socialPending)"
        :aria-pressed="Boolean(detail.social?.bookmarked)"
        class="h-9 px-4 rounded-full border transition text-sm"
        :class="detail.social?.bookmarked ? 'border-brand-400 bg-brand-50 text-brand-600 dark:bg-brand-900/30 dark:text-brand-300' : 'border-line hover:bg-mute'"
        @click="toggleSocial('bookmark')"
      >▮ {{ detail.social?.bookmarked ? '已收藏' : '收藏' }} {{ detail.social?.bookmarkCount || 0 }}</button>
      <span class="text-xs text-ink-soft ml-auto">
        {{ detail.social?.followerCount || 0 }} 位关注作者 · 作者关注 {{ detail.social?.followingCount || 0 }} 人
      </span>
    </div>

    <!-- 大图纵向流 -->
    <div class="mt-4 space-y-3">
      <template v-for="(img, i) in detail.images" :key="img.mediaId">
        <img
          v-if="!brokenImages.has(img.mediaId)"
          :src="img.url"
          :alt="`${detail.title} 图 ${i + 1}`"
          loading="lazy"
          class="w-full max-h-[720px] object-contain bg-black/95 rounded-xl"
          @error="markImageFailed(img.mediaId)"
        />
        <div
          v-else
          role="img"
          :aria-label="`${detail.title} 第 ${i + 1} 张图片暂时无法显示`"
          class="w-full min-h-56 grid place-items-center bg-mute rounded-xl text-sm text-ink-soft"
        >图片暂时无法显示</div>
      </template>
    </div>

    <p v-if="detail.content" class="mt-5 whitespace-pre-wrap text-[15px] leading-relaxed">{{ detail.content }}</p>

    <section class="mt-8 border-t border-line pt-5" aria-labelledby="comments-title">
      <div class="flex items-baseline justify-between">
        <h2 id="comments-title" class="text-lg font-semibold">评论</h2>
        <span v-if="comments.length" class="text-xs text-ink-soft">已加载 {{ comments.length }} 条</span>
      </div>

      <div class="mt-4 rounded-xl border border-line bg-surface p-3">
        <p v-if="commentParent" class="mb-2 flex items-center justify-between text-xs text-ink-soft" role="status" aria-live="polite">
          <span>回复 {{ commentParent.author?.nickname || '这条评论' }}</span>
          <button type="button" class="underline" @click="cancelReply">取消回复</button>
        </p>
        <label for="comment-input" class="sr-only">评论内容</label>
        <textarea
          id="comment-input"
          v-model="commentContent"
          :maxlength="commentParent ? 300 : 500"
          rows="3"
          aria-describedby="comment-help"
          :aria-busy="commentSubmitting"
          :placeholder="store.isLoggedIn ? (commentParent ? '写下回复…' : '说说你的感受…') : '登录后参与评论'"
          class="w-full resize-none bg-transparent text-sm outline-none placeholder:text-ink-soft"
          @click="!store.isLoggedIn && router.push({ path: '/login', query: { redirect: route.fullPath } })"
        ></textarea>
        <div class="mt-2 flex items-center justify-between">
          <span id="comment-help" class="text-xs text-ink-soft" aria-live="polite">{{ [...commentContent].length }}/{{ commentParent ? 300 : 500 }}</span>
          <button
            type="button"
            :disabled="commentSubmitting || !commentContent.trim()"
            :aria-busy="commentSubmitting"
            class="h-8 px-4 rounded-full bg-brand-500 text-white text-xs disabled:opacity-50"
            @click="submitComment"
          >{{ commentSubmitting ? '发送中…' : '发送' }}</button>
        </div>
      </div>

      <div v-if="commentsError && comments.length === 0" role="alert" class="py-8 text-center text-sm text-ink-soft">
        <p class="mb-3">评论加载失败：{{ commentsError }}</p>
        <button type="button" class="underline" @click="loadComments(route.params.id, loadSeq)">重试</button>
      </div>
      <div v-else-if="comments.length === 0 && !commentsLoading" class="py-8 text-center text-sm text-ink-soft">
        还没有评论，来留下第一句吧。
      </div>
      <div v-else class="mt-4 space-y-4" aria-label="评论列表">
        <article v-for="comment in comments" :key="comment.id" class="flex gap-3">
          <span class="w-8 h-8 shrink-0 rounded-full bg-brand-50 grid place-items-center text-brand-500 text-xs">
            {{ (comment.author?.nickname || '山').slice(0, 1) }}
          </span>
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-2 text-xs">
              <span class="font-medium">{{ comment.author?.nickname || '已注销' }}</span>
              <span class="text-ink-soft">{{ fmt(comment.createdAt) }}</span>
              <button v-if="comment.status === 1" type="button" class="ml-auto text-ink-soft hover:text-brand-600" :aria-label="`回复 ${comment.author?.nickname || '这条评论'}`" @click="startReply(comment)">回复</button>
              <button v-if="comment.canDelete" type="button" class="text-ink-soft hover:text-red-500" :aria-label="`删除 ${comment.author?.nickname || '这条评论'} 的评论`" @click="removeComment(comment)">
                {{ commentDeletePending === comment.id ? '再点一次删除' : '删除' }}
              </button>
            </div>
            <p class="mt-1 whitespace-pre-wrap text-sm" :class="comment.status === 2 ? 'text-ink-soft italic' : ''">{{ comment.content }}</p>
            <p v-if="comment.parentId" class="mt-1 text-xs text-ink-soft">回复评论 #{{ comment.parentId }}</p>
          </div>
        </article>
      </div>
      <div v-if="comments.length && commentsError" role="alert" class="mt-4 text-center text-sm text-red-500">
        <span>加载更多失败：{{ commentsError }}</span>
        <button type="button" class="ml-2 underline" @click="loadComments(route.params.id, loadSeq)">重试</button>
      </div>
      <button
        v-if="commentsHasMore && !commentsError"
        type="button"
        class="mt-5 w-full h-9 rounded-full border border-line text-sm text-ink-soft hover:bg-mute disabled:opacity-50"
        :disabled="commentsLoading"
        @click="loadComments(route.params.id, loadSeq)"
      >{{ commentsLoading ? '加载中…' : '加载更多评论' }}</button>
    </section>
  </article>
</template>
