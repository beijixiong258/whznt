<script setup lang="ts">
import MarkdownIt from 'markdown-it'
import { computed } from 'vue'

const props = defineProps<{ content: string; citationIds?: number[] }>()
const emit = defineEmits<{ citation: [id: number] }>()
const markdown = new MarkdownIt({ html: false, breaks: true }).disable('image')
markdown.renderer.rules.text = (tokens, index, _options, environment) => {
  const safe = markdown.utils.escapeHtml(tokens[index].content)
  return safe.replace(/【资料(\d+)】/g, (label, rawId: string) => {
    if (!(environment?.ids instanceof Set) || !environment.ids.has(Number(rawId))) return label
    return '<button type="button" class="citation-link" data-citation="' + rawId + '">' + label + '</button>'
  })
}
const rendered = computed(() => markdown.render(props.content, { ids: new Set(props.citationIds ?? []) }))

function clickCitation(event: MouseEvent) {
  const target = event.target
  if (!(target instanceof HTMLButtonElement) || !target.dataset.citation) return
  emit('citation', Number(target.dataset.citation))
}
</script>

<template>
  <div class="markdown-content" v-html="rendered" @click="clickCitation" />
</template>
