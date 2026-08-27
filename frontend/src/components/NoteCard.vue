<script setup>
defineProps({
  card: { type: Object, required: true },
})
defineEmits(['open'])
</script>

<template>
  <article
    data-testid="note-card"
    class="break-inside-avoid mb-4 cursor-pointer group"
    @click="$emit('open', card.id)"
  >
    <!-- 封面：预占位防抖动（宽高未知时按 3:4） -->
    <div
      class="rounded-xl overflow-hidden bg-neutral-100"
      :style="{ aspectRatio: card.coverWidth && card.coverHeight ? `${card.coverWidth}/${card.coverHeight}` : '3/4' }"
    >
      <img
        :src="card.coverUrl"
        :alt="card.title || '风景'"
        loading="lazy"
        decoding="async"
        class="w-full h-full object-cover group-hover:scale-[1.03] transition duration-300"
      />
    </div>

    <h3 class="mt-2 text-sm font-medium line-clamp-2 group-hover:text-brand-600 transition">
      {{ card.title }}
    </h3>

    <p v-if="card.contentPreview" class="mt-0.5 text-xs text-ink-soft line-clamp-1">
      {{ card.contentPreview }}
    </p>

    <div class="mt-1.5 flex items-center gap-2">
      <img
        v-if="card.author?.avatarUrl"
        :src="card.author.avatarUrl"
        class="w-5 h-5 rounded-full object-cover"
        alt=""
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
  </article>
</template>
