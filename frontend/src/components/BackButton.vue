<script setup lang="ts">
import { ArrowLeftIcon } from 'tdesign-icons-vue-next'

defineProps<{ label: string; href?: string; disabled?: boolean }>()
const emit = defineEmits<{ back: [] }>()
</script>

<template>
  <t-button class="page-back" theme="default" variant="outline" :href="href" :disabled="disabled"
    @click="emit('back')">
    <template #icon><ArrowLeftIcon aria-hidden="true" /></template>
    {{ label }}
  </t-button>
</template>
