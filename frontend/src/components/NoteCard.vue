<script setup>
import { ref } from 'vue'

const props = defineProps({
  card: { type: Object, required: true },
})
const emit = defineEmits(['open', 'open-user', 'social-action'])
const imageFailed = ref(false)
const authorImageFailed = ref(false)
function openCard(id) { emit('open', id) }
function onKeydown(event, id) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    openCard(id)
  }
}
function socialAction(type) {
  const stateKey = type === 'like' ? 'liked' : 'bookmarked'
  emit('social-action', { type, id: props.card.id, enabled: !props.card.social?.[stateKey] })
}
</script>

<template>
  <article
    data-testid="note-card"
    role="button"
    tabindex="0"
    class="break-inside-avoid mb-4 cursor-pointer group"
    @click="openCard(card.id)"
    @keydown="onKeydown($event, card.id)"
  >
    <!-- 封面：预占位防抖动（宽高未知时按 3:4） -->
    <div
      class="rounded-xl overflow-hidden bg-mute"
      :style="{ aspectRatio: card.coverWidth && card.coverHeight ? `${card.coverWidth}/${card.coverHeight}` : '3/4' }"
    >
      <img
        v-if="!imageFailed"
        :src="card.coverUrl"
        :alt="card.title || '风景'"
        loading="lazy"
        decoding="async"
        class="w-full h-full object-cover group-hover:scale-[1.03] transition duration-300"
        @error="imageFailed = true"
      />
      <div
        v-else
        role="img"
        :aria-label="`${card.title || '风景'}图片暂时无法显示`"
        class="w-full h-full grid place-items-center text-xs text-ink-soft"
      >图片暂时无法显示</div>
    </div>

    <h3 class="mt-2 text-sm font-medium line-clamp-2 group-hover:text-brand-600 transition">
      {{ card.title }}
    </h3>

    <p v-if="card.contentPreview" class="mt-0.5 text-xs text-ink-soft line-clamp-1">
      {{ card.contentPreview }}
    </p>

    <div class="mt-1.5 flex items-center gap-2">
      <img
        v-if="card.author?.avatarUrl && !authorImageFailed"
        :src="card.author.avatarUrl"
        class="w-5 h-5 rounded-full object-cover"
        alt=""
        @error="authorImageFailed = true"
      />
      <span
        v-else
        class="w-5 h-5 rounded-full bg-brand-50 text-brand-400 grid place-items-center text-[10px]"
      >{{ (card.author?.nickname || '山').slice(0, 1) }}</span>
      <span
        class="text-xs text-ink-soft hover:text-brand-500"
        @click.stop="$emit('open-user', card.author?.id)"
      >{{ card.author?.nickname }}</span>
      <span class="ml-auto text-xs text-ink-soft">📄 {{ card.mediaCount }}</span>
    </div>
    <div class="mt-2 flex items-center gap-3 text-xs text-ink-soft" @click.stop>
      <button
        type="button"
        :disabled="card.socialPending"
        class="inline-flex items-center gap-1 hover:text-brand-600"
        :aria-label="`${card.social?.liked ? '取消点赞' : '点赞'} ${card.title || '这篇笔记'}`"
        :aria-pressed="Boolean(card.social?.liked)"
        @click="socialAction('like')"
      >
        <span aria-hidden="true">♥</span>
        <span>{{ card.social?.likeCount || 0 }}</span>
      </button>
      <button
        type="button"
        :disabled="card.socialPending"
        class="inline-flex items-center gap-1 hover:text-brand-600"
        :aria-label="`${card.social?.bookmarked ? '取消收藏' : '收藏'} ${card.title || '这篇笔记'}`"
        :aria-pressed="Boolean(card.social?.bookmarked)"
        @click="socialAction('bookmark')"
      >
        <span aria-hidden="true">▮</span>
        <span>{{ card.social?.bookmarkCount || 0 }}</span>
      </button>
    </div>
  </article>
</template>
