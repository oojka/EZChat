# Composables (Vue Hooks)

**Purpose**: Reusable stateful logic. All business logic lives here, not in components.

## STRUCTURE

```
composables/
├── useLogin.ts              # Login + cooldown + navigation
├── useRegister.ts           # 2-step registration
├── useGuest.ts              # Guest join flow
├── useChatInput.ts          # Message + image upload
├── useChatMemberList.ts     # Member pagination
├── useChatRoomActions.ts    # Room CRUD
├── useCreateChat.ts         # Chat creation
├── useUpload.ts             # Image upload + dedup
├── useInfiniteScroll.ts     # Scroll pagination
├── useAppRefreshToken.ts    # Token refresh
├── useCooldown.ts           # Rate limiting
├── useIsMobile.ts           # Responsive
├── useKeyboardVisible.ts    # Mobile keyboard
├── useViewportHeight.ts     # iOS vh fix
├── room/                    # Room settings
│   ├── useRoomBasicSettings.ts
│   ├── useRoomPasswordSettings.ts
│   ├── useRoomMemberManagement.ts
│   └── useRoomInviteManager.ts
└── chat/join/               # Join flows
    ├── useGuestJoin.ts
    ├── useLoginJoin.ts
    └── useJoinInput.ts
```

## WHERE TO LOOK

| Task | File |
|------|------|
| Auth flow | `useLogin.ts`, `useGuest.ts`, `useRegister.ts` |
| Send message | `useChatInput.ts` |
| Pagination | `useInfiniteScroll.ts` |
| Room mgmt | `room/useRoom*.ts` |
| Token | `useAppRefreshToken.ts` |

## CONVENTIONS

**Naming**: `useXxx.ts` → `export default function()` or `export const useXxx`

**Return**: Object with refs + methods
```typescript
export default function() {
  const state = ref(...)
  const action = async () => {...}
  return { state, action }
}
```

**Dependencies**:
- `useXxxStore()` for stores
- `storeToRefs()` for reactive props
- `useI18n()` for translations

**Cooldown**: Module-level instance
```typescript
const lock = new Cooldown(3000, 5, 15000)
const { isLocked, tryExecute } = useCooldown(lock)
```

## ANTI-PATTERNS

- Business logic in .vue → Extract here
- Direct API in components → Use composables
- `any` type → Use `@/type`
- Duplicate Cooldown → Module level
