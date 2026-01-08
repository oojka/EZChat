<script setup lang="ts">
import { computed, ref, watchEffect } from 'vue'
import { Camera, Loading, Picture } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'

/**
 * Avatar: 统一的用户/群组头像组件
 *
 * 功能：
 * 1. 智能 Fallback: 缩略图 -> 原图 -> 文字/图标
 * 2. 统一样式: 支持 circle/square, 统一圆角比例
 * 3. 上传模式: editable=true 时显示悬停遮罩
 * 4. 占位模式: 无图片时显示虚线框占位 (isPlaceholder)
 */

const { t } = useI18n()

type Shape = 'circle' | 'square'

const props = withDefaults(defineProps<{
    // 数据源
    thumbUrl?: string
    url?: string
    text?: string // Fallback 文字

    // 样式配置
    size?: number | string
    shape?: Shape
    borderRadiusRatio?: number // 圆角比例 (默认 0.3)

    // 交互配置
    editable?: boolean //是否显示编辑遮罩
    iconSize?: number // 占位图标大小
}>(), {
    size: 100, // 默认较大，用于 Profile，列表处可传 40
    shape: 'square',
    borderRadiusRatio: 0.3,
    editable: false,
    iconSize: 40,
    thumbUrl: '',
    url: '',
    text: '?',
})

// --- Logic Reuse from SmartAvatar (Simplified) ---

const currentUrl = ref<string>('')
const isError = ref(false)

// 监听 url 变化重置状态
watchEffect(() => {
    const thumb = props.thumbUrl || ''
    const original = props.url || ''
    currentUrl.value = thumb || original || ''
    isError.value = !currentUrl.value
})

const handleError = (e: Event) => {
    const thumb = props.thumbUrl || ''
    const original = props.url || ''

    // 如果当前是缩略图且有原图，降级到原图
    if (currentUrl.value === thumb && original && original !== thumb) {
        currentUrl.value = original
        // 不置 error，继续尝试
    } else {
        // 彻底失败
        isError.value = true
    }
}

// --- Styles ---

const normalizeSize = (s: number | string) => (typeof s === 'number' ? `${s}px` : s)

const containerStyle = computed(() => ({
    width: normalizeSize(props.size),
    height: normalizeSize(props.size),
    '--avatar-radius': props.shape === 'circle'
        ? '50%'
        : `calc(${normalizeSize(props.size)} * ${props.borderRadiusRatio})`
}))

const firstChar = computed(() => (props.text || '?').trim().charAt(0).toUpperCase() || '?')

</script>

<template>
    <div class="ez-user-avatar" :class="[
        `shape-${shape}`,
        { 'is-editable': editable, 'is-placeholder-mode': isError }
    ]" :style="containerStyle">
        <!-- 图片模式 -->
        <el-image v-if="!isError && currentUrl" :src="currentUrl" fit="cover" class="avatar-img" @error="handleError">
            <template #placeholder>
                <div class="image-slot loading">
                    <el-icon class="is-loading">
                        <Loading />
                    </el-icon>
                </div>
            </template>
            <template #error>
                <div class="image-slot error">
                    <span>{{ firstChar }}</span>
                </div>
            </template>
        </el-image>

        <!-- 占位模式 (无图或加载失败) -->
        <div v-else class="avatar-placeholder">
            <!-- 如果有文字，优先显文字，否则显通用 Icon -->
            <span v-if="text && text !== '?'" class="placeholder-text">{{ firstChar }}</span>
            <div v-else class="placeholder-icon-wrapper">
                <el-icon :size="iconSize">
                    <Picture />
                </el-icon>
                <span v-if="editable" class="placeholder-hint">
                    {{ t('auth.select_image') }}
                </span>
            </div>
        </div>

        <!-- 编辑遮罩 (Hover) -->
        <div v-if="editable" class="edit-mask">
            <el-icon>
                <Camera />
            </el-icon>
            <span>{{ t('common.change') }}</span>
        </div>
    </div>
</template>

<style scoped>
.ez-user-avatar {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--bg-page);
    border: 1px solid var(--el-border-color-light);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
    overflow: hidden;
    border-radius: var(--avatar-radius);
    transition: all 0.3s ease;
    flex-shrink: 0;
}

/* 占位模式下的特定样式 (虚线框) */
.ez-user-avatar.is-placeholder-mode {
    border: 2px dashed var(--el-border-color);
    background: var(--bg-fill-0);
    color: var(--text-400);
}

.ez-user-avatar.is-placeholder-mode:hover {
    border-color: var(--primary);
    color: var(--primary);
    background: var(--primary-light-9);
}

.avatar-img {
    width: 100%;
    height: 100%;
    display: block;
}

.image-slot {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--bg-fill-1);
    color: var(--text-400);
    font-size: 14px;
}

.avatar-placeholder {
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 8px;
}

.placeholder-text {
    font-size: calc(var(--size) * 0.4);
    /* 动态字体大小 */
    font-weight: 800;
    color: var(--text-400);
}

.placeholder-icon-wrapper {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
}

.placeholder-hint {
    font-size: 12px;
    font-weight: 700;
}

/* Edit Mask */
.edit-mask {
    position: absolute;
    inset: 0;
    background: rgba(0, 0, 0, 0.5);
    backdrop-filter: blur(2px);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: #fff;
    opacity: 0;
    transition: opacity 0.2s;
    font-size: 12px;
    font-weight: 600;
    gap: 4px;
    z-index: 2;
    cursor: pointer;
}

.ez-user-avatar:hover .edit-mask {
    opacity: 1;
}

/* Shape Modifier */
.shape-circle {
    border-radius: 50% !important;
}
</style>
