<script setup lang="ts">
import {nextTick, onMounted, ref, watch} from 'vue'
import {ArrowUp, Close, Picture} from '@element-plus/icons-vue'
import {useChatInput} from '@/hooks/useChatInput.ts'
import {useRoute} from 'vue-router'
import {useI18n} from 'vue-i18n'

const { t } = useI18n()
const {
  inputContent, sendSettings, uploadHeaders, beforePictureUpload,
  handlePictureSuccess, removeImage, send, resetInput, handleExceed,
} = useChatInput()

const route = useRoute()
const editorRef = ref<HTMLElement | null>(null)
const lastRange = ref<Range | null>(null)

const commonEmojis = ['😀', '😃', '😄', '😁', '😆', '😅', '😂', '🤣', '😊', '😇', '🙂', '🙃', '😉', '😌', '😍', '🥰', '😘', '😗', '😙', '😚', '😋', '😛', '😝', '😜', '🤪', '🤨', '🧐', '🤓', '😎', '🤩', '🥳', '😏', '😒', '😞', '😔', '😟', '😕', '🙁', '☹️', '😣', '😖', '😫', '😩', '🥺', '😢', '😭', '😤', '😠', '😡', '🤬', '🤯', '😳', '🥵', '🥶', '😱', '😨', '😰', '😥', '😓', '🤗', '🤔', '🤭', '🤫', '🤥', '😶', '😐', '😑', '😬', '🙄', '😯', '😦', '😧', '😮', '😲', '🥱', '😴', '🤤', '😪', '😵', '🤐', '🥴', '🤢', '🤮', '🤧', '😷', '🤒', '🤕', '🤑', '🤠', '😈', '👿', '👹', '👺', '🤡', '💩', '👻', '💀', '☠️', '👽', '👾', '🤖', '🎃', '😺', '😸', '😹', '😻', '😼', '😽', '🙀', '😿', '😾', '🤲', '👐', '🙌', '👏', '🤝', '👍', '👎', '👊', '✊', '🤛', '🤜', '🤞', '✌️', '🤟', '🤘', '👌', '👈', '👉', '👆', '👇', '☝️', '✋', '🤚', '🖐', '🖖', '👋', '🤙', '💪', '🦾', '🖕', '✍️', '🙏', '🦶', '🦵', '🦿', '💄', '💋', '👄', '🦷', '👅', '👂', '🦻', '👃', '👣', '👁', '👀', '🧠', '🗣', '👤', '👥', '👶', '👧', '🧒', '👦', '👩', '🧑', '👨', '👩‍🦱', '🧑‍🦱']

watch(() => route.params.chatCode, () => {
  resetInput(); if (editorRef.value) editorRef.value.innerText = ''; lastRange.value = null
})

const saveSelection = () => {
  const selection = window.getSelection()
  if (selection && selection.rangeCount > 0) {
    const range = selection.getRangeAt(0)
    if (editorRef.value?.contains(range.commonAncestorContainer)) lastRange.value = range.cloneRange()
  }
}

const restoreSelection = () => {
  const selection = window.getSelection()
  if (selection && lastRange.value) {
    selection.removeAllRanges(); selection.addRange(lastRange.value)
  }
}

const onInput = (e: Event) => {
  const target = e.target as HTMLElement; inputContent.value.text = target.innerText; saveSelection()
}

const onKeyDown = (e: KeyboardEvent) => {
  if (e.key === 'Enter' && sendSettings.value.sendOnEnter && !e.shiftKey) {
    e.preventDefault(); handleSend()
  }
}

const handleSend = () => {
  if (!inputContent.value.text.trim() && inputContent.value.images.length === 0) return
  send()
  if (editorRef.value) {
    editorRef.value.innerText = ''; lastRange.value = null; nextTick(() => editorRef.value?.focus())
  }
}

const onSelectEmoji = (emoji: string) => {
  if (!editorRef.value) return
  editorRef.value.focus(); if (lastRange.value) restoreSelection()
  const selection = window.getSelection()
  if (selection && selection.rangeCount > 0) {
    const range = selection.getRangeAt(0); range.deleteContents()
    const span = document.createElement('span'); span.className = 'input-inline-emoji'; span.innerText = emoji; span.contentEditable = 'false'
    range.insertNode(span); const newRange = document.createRange(); newRange.setStartAfter(span); newRange.collapse(true)
    selection.removeAllRanges(); selection.addRange(newRange); lastRange.value = newRange.cloneRange()
  }
  inputContent.value.text = editorRef.value.innerText; nextTick(() => editorRef.value?.focus())
}

onMounted(() => editorRef.value?.focus())
</script>

<template>
  <div class="input-container">
    <div class="toolbar">
      <div class="tool-left">
        <el-popover placement="top-start" :width="515" trigger="click" popper-class="ez-emoji-popover">
          <template #reference>
            <div class="tool-btn">
              <el-icon><svg viewBox="0 0 1024 1024"><path fill="currentColor" d="M512 64a448 448 0 1 1 0 896 448 448 0 0 1 0-896zm0 80a368 368 0 1 0 0 736 368 368 0 0 0 0-736zm-144 240a40 40 0 1 1 0 80 40 40 0 0 1 0-80zm288 0a40 40 0 1 1 0 80 40 40 0 0 1 0-80zm-144 200c92.784 0 174.624 53.984 214.752 132.56a40 40 0 0 1-70.912 37.136C473.328 680.16 418.672 644 368 644c-50.672 0-105.328 36.16-131.84 85.696a40 40 0 0 1-70.912-37.136C205.376 613.984 287.216 560 380 560h132z"/></svg></el-icon>
            </div>
          </template>
          <div class="emoji-picker"><div class="emoji-grid"><span v-for="emoji in commonEmojis" :key="emoji" class="emoji-item" @mousedown.prevent @click="onSelectEmoji(emoji)">{{ emoji }}</span></div></div>
        </el-popover>

        <el-upload class="picture-uploader" action="/api/message/upload" :headers="uploadHeaders" multiple :limit="15" :show-file-list="false" :on-success="handlePictureSuccess" :before-upload="beforePictureUpload" :on-exceed="handleExceed">
          <template #trigger><div class="tool-btn"><el-icon><Picture /></el-icon></div></template>
        </el-upload>
      </div>
    </div>

    <div v-if="inputContent.images.length > 0" class="preview-area">
      <div v-for="(img, index) in inputContent.images" :key="index" class="preview-item">
        <img :src="img.objectThumbUrl || img.objectUrl" />
        <div class="preview-delete" @click="removeImage(index)"><el-icon><Close /></el-icon></div>
      </div>
    </div>

    <div class="input-wrapper">
      <div ref="editorRef" class="rich-editor" contenteditable="true" @input="onInput" @keydown="onKeyDown" :placeholder="t('chat.input_placeholder')"></div>
    </div>

    <div class="bottom-container">
      <el-button-group class="send-button-group">
        <el-button class="send-button" type="primary" @click="handleSend">{{ t('chat.send') }}</el-button>
        <el-popover placement="top-end" :width="150" trigger="click" popper-class="ez-send-settings-popover" :offset="12">
          <template #reference>
            <el-button class="settings-button" type="primary">
              <el-icon><ArrowUp /></el-icon>
            </el-button>
          </template>
          <div class="settings-menu">
            <div class="settings-item">
              <span class="item-label">{{ t('chat.send_on_enter') }}</span>
              <el-switch v-model="sendSettings.sendOnEnter" size="small" />
            </div>
          </div>
        </el-popover>
      </el-button-group>
    </div>
  </div>
</template>

<style scoped>
.input-container { display: flex; flex-direction: column; height: 100%; padding: 8px 16px; box-sizing: border-box; background-color: var(--bg-card); border-top: 1px solid var(--el-border-color-light); transition: all 0.3s ease; }
.toolbar { flex-shrink: 0; display: flex; justify-content: space-between; align-items: center; padding-bottom: 4px; }
.tool-left { display: flex; align-items: center; gap: 4px; }
.picture-uploader { display: inline-block; line-height: 0; }
.tool-btn { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; cursor: pointer; transition: all 0.2s; border-radius: 8px; color: var(--text-500); background: transparent; }
.tool-btn:hover { color: var(--text-900); background-color: var(--bg-page); }
.preview-area { flex-shrink: 0; display: flex; flex-wrap: nowrap; overflow-x: auto; gap: 8px; padding: 8px 0; border-bottom: 1px solid var(--el-border-color-light); margin-bottom: 4px; }
.preview-item { position: relative; width: 56px; height: 56px; border-radius: 8px; overflow: hidden; border: 1px solid var(--el-border-color-light); flex-shrink: 0; }
.input-wrapper { flex: 1; min-height: 40px; overflow-y: auto; }
.input-wrapper::-webkit-scrollbar { width: 4px; }
.input-wrapper::-webkit-scrollbar-thumb { background: var(--text-400); border-radius: 10px; opacity: 0.2; }
.rich-editor { height: 100%; outline: none; font-size: 18px; line-height: 1.5; color: var(--text-900); white-space: pre-wrap; word-break: break-word; }
.rich-editor:empty::before { content: attr(placeholder); color: var(--text-400); }

/* --- 发送按钮组布局优化 --- */
.bottom-container {
  flex-shrink: 0;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 8px 0 12px; /* 增加底部内边距 (12px) */
}

.send-button-group {
  border-radius: 12px; overflow: hidden;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.2);
  transition: all 0.3s var(--ease-out-expo);
  border: 1px solid transparent;
}

html.dark .send-button-group {
  background: rgba(59, 130, 246, 0.85);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  box-shadow: 0 0 15px rgba(59, 130, 246, 0.2);
  border-color: rgba(255, 255, 255, 0.05);
}

.send-button {
  height: 40px; padding: 0 24px; font-weight: 800; font-size: 14px; letter-spacing: 0.5px;
  border: none !important;
  background: var(--primary) !important;
}
html.dark .send-button { background: transparent !important; }

.settings-button {
  height: 40px; width: 36px; padding: 0;
  border: none !important;
  border-left: 1px solid rgba(255, 255, 255, 0.1) !important;
  background: var(--primary) !important;
}
html.dark .settings-button {
  background: transparent !important;
  border-left-color: rgba(255, 255, 255, 0.1) !important;
}

.send-button:active, .settings-button:active { transform: scale(0.96); }

:global(.ez-emoji-popover) { background: var(--bg-card) !important; border: 1px solid var(--el-border-color-light) !important; border-radius: 16px !important; box-shadow: var(--shadow-glass) !important; padding: 0 !important; }
:global(.ez-send-settings-popover) {
  background: var(--bg-card) !important;
  border: 1px solid var(--el-border-color-light) !important;
  border-radius: 12px !important;
  padding: 12px !important;
  box-shadow: var(--shadow-glass) !important;
}

.settings-menu { display: flex; flex-direction: column; gap: 8px; }
.settings-item { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.item-label { font-size: 12px; font-weight: 800; color: var(--text-700); white-space: nowrap; }
.emoji-item:hover { background-color: var(--bg-page); }
</style>
