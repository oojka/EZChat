<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Message, ChatRoom } from '@/type'

const props = defineProps<{
    msg: Message
    currentChat: ChatRoom | undefined
}>()

const { t } = useI18n()

const systemText = computed(() => {
    if (props.msg.type === 10) {
        return t('system.room_created')
    } else if (props.msg.type === 11) {
        // Type 11: Member Join (Sender is the user who joined)
        const member = props.currentChat?.chatMembers?.find(m => m.uid == props.msg.sender)
        const nickname = member ? member.nickname : (props.msg.sender || 'Unknown')
        return t('system.member_join', [nickname])
    }
    // Future extension for other system message types (12, 13)
    return props.msg.text || ''
})

const timeText = computed(() => {
    return props.msg.createTime?.replace('T', ' ').slice(0, 16)
})

</script>

<template>
    <li class="system-message-row">
        <div class="system-message-content">
            <span class="system-text">{{ systemText }}</span>
            <!-- Optional: Display time for system messages if needed, currently hidden or subtle -->
            <!-- <span class="system-time">{{ timeText }}</span> -->
        </div>
    </li>
</template>

<style scoped>
.system-message-row {
    display: flex;
    justify-content: center;
    width: 100%;
    margin: 8px 0;
}

.system-message-content {
    background-color: var(--bg-card-hover, rgba(0, 0, 0, 0.05));
    padding: 4px 12px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    gap: 8px;
}

.system-text {
    font-size: 12px;
    color: var(--text-400);
}

html.dark .system-message-content {
    background-color: rgba(255, 255, 255, 0.05);
}
</style>
