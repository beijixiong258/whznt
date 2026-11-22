<script setup lang="ts">
import MarkdownIt from 'markdown-it'
import { computed } from 'vue'

const props = defineProps<{ content: string }>()
const markdown = new MarkdownIt({ html: false, breaks: true }).disable('image')
const rendered = computed(() => markdown.render(props.content))
</script>

<template>
  <div class="markdown-content" v-html="rendered" />
</template>
