# QR Code Dialog Feature Plan

**Created**: 2026-01-13  
**Status**: Ready for Implementation

## Overview

Add QR code display functionality to invite links. Users click a QR button to see a large QR code in a dialog.

## Scope

| File | Change |
|------|--------|
| `package.json` | Add `qrcode` + `@types/qrcode` dependencies |
| `components/dialogs/QrCodeDialog.vue` | **NEW** - Reusable QR code dialog component |
| `components/dialogs/CreateChatDialog.vue` | Add QR button next to invite link copy button |
| `components/dialogs/room-settings/RoomInviteList.vue` | Add QR button to each invite card + emit event |
| `composables/room/useRoomInviteManager.ts` | Add QR dialog state management |

## Implementation Details

### 1. Install Dependencies

```bash
cd frontend/vue-ezchat
npm install qrcode @types/qrcode
```

### 2. QrCodeDialog.vue Component

**Location**: `src/components/dialogs/QrCodeDialog.vue`

**Props**:
- `visible: boolean` - Dialog visibility (v-model)
- `url: string` - URL to encode in QR code
- `title?: string` - Optional dialog title

**Features**:
- Uses `qrcode.toDataURL()` to generate QR image
- 200x200px QR code, centered
- Glassmorphism styling (match existing dialogs)
- Close button
- Optional: Copy URL button below QR

**Template Structure**:
```vue
<el-dialog class="ez-modern-dialog qr-dialog" ...>
  <div class="qr-content">
    <img :src="qrDataUrl" class="qr-image" />
    <p class="qr-url">{{ url }}</p>
  </div>
</el-dialog>
```

**Styling**:
- Use existing CSS variables: `--bg-glass`, `--blur-glass`, `--border-glass`
- QR image: white background, rounded corners, subtle shadow
- Responsive: smaller on mobile

### 3. CreateChatDialog.vue Changes

**Location**: Result page (Step 4), line ~210-214

**Current**:
```vue
<div class="credential-link-row">
  <div class="credential-link-value">{{ createResult.inviteUrl }}</div>
  <el-button class="copy-icon-btn" :icon="DocumentCopy" @click="copyInviteLink" />
</div>
```

**After**:
```vue
<div class="credential-link-row">
  <div class="credential-link-value">{{ createResult.inviteUrl }}</div>
  <el-tooltip content="QR Code">
    <el-button class="copy-icon-btn" @click="showQrDialog = true">
      <el-icon><Iphone /></el-icon>  <!-- or custom QR icon -->
    </el-button>
  </el-tooltip>
  <el-tooltip :content="t('common.copy')">
    <el-button class="copy-icon-btn copy-icon-btn--primary" :icon="DocumentCopy" @click="copyInviteLink" />
  </el-tooltip>
</div>

<QrCodeDialog v-model="showQrDialog" :url="createResult.inviteUrl" />
```

**Add to script**:
- Import `QrCodeDialog`
- Add `showQrDialog = ref(false)`

### 4. RoomInviteList.vue Changes

**Location**: Each invite card actions area, line ~82-98

**Current**:
```vue
<div class="invite-actions">
  <el-tooltip :content="t('common.copy')">
    <button class="action-mini-btn" @click="$emit('copy', invite.inviteCode)">
      <el-icon><DocumentCopy /></el-icon>
    </button>
  </el-tooltip>
  <el-tooltip :content="t('room_settings.revoke')">
    <button class="action-mini-btn danger" @click="$emit('revoke', invite.id)">
      <el-icon><Delete /></el-icon>
    </button>
  </el-tooltip>
</div>
```

**After**:
```vue
<div class="invite-actions">
  <el-tooltip content="QR Code">
    <button class="action-mini-btn" @click="$emit('showQr', invite.inviteCode)">
      <el-icon><Iphone /></el-icon>
    </button>
  </el-tooltip>
  <el-tooltip :content="t('common.copy')">
    <button class="action-mini-btn" @click="$emit('copy', invite.inviteCode)">
      <el-icon><DocumentCopy /></el-icon>
    </button>
  </el-tooltip>
  <el-tooltip :content="t('room_settings.revoke')">
    <button class="action-mini-btn danger" @click="$emit('revoke', invite.id)">
      <el-icon><Delete /></el-icon>
    </button>
  </el-tooltip>
</div>
```

**Add emit**: `(e: 'showQr', code: string): void`

### 5. useRoomInviteManager.ts Changes

**Location**: `src/composables/room/useRoomInviteManager.ts`

**Add**:
```typescript
// QR Dialog state
const qrDialogVisible = ref(false)
const qrDialogUrl = ref('')

const showQrCode = (inviteCode: string) => {
  qrDialogUrl.value = buildInviteUrl(inviteCode)
  qrDialogVisible.value = true
}

// Export
return {
  // ... existing exports
  qrDialogVisible,
  qrDialogUrl,
  showQrCode,
}
```

### 6. Parent Component Integration

The parent of `RoomInviteList.vue` needs to:
1. Handle `@showQr` event
2. Call `showQrCode(code)` from useRoomInviteManager
3. Include `<QrCodeDialog v-model="qrDialogVisible" :url="qrDialogUrl" />`

## Icon Choice

Option A: Use `Iphone` from `@element-plus/icons-vue` (represents mobile scanning)
Option B: Create/import a dedicated QR code icon

Recommend: Option A for simplicity, or find a QR icon SVG.

## URL Format

Invite URL pattern (already exists in codebase):
```
https://ez-chat.oojka.com/invite/{inviteCode}
```

Use `buildInviteUrl()` from `useRoomInviteManager.ts` (line 98).

## Testing

No test infrastructure exists. Manual verification:
1. Create a new chat room -> verify QR button appears on result page
2. Click QR button -> verify dialog opens with correct QR code
3. Scan QR code with phone -> verify it navigates to invite URL
4. Open room settings -> verify QR button on each invite card
5. Click QR button on invite card -> verify correct QR code

## Verification Checklist

- [ ] `npm install` succeeds
- [ ] `npm run build` succeeds (no type errors)
- [ ] QR dialog opens from CreateChatDialog result page
- [ ] QR dialog opens from RoomInviteList card
- [ ] QR code is scannable and correct
- [ ] UI matches existing glassmorphism style
- [ ] Dark mode works correctly

## Notes

- Keep QrCodeDialog simple and reusable
- Match existing button styles exactly
- No i18n needed for "QR Code" tooltip (universal term)
