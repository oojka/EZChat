# Execution Plan - Style JoinChatDialog.vue

## Goal
Optimize `JoinChatDialog.vue` layout and design to match `CreateChatDialog.vue` and `main.css`, ensuring a unified system style.

## Design Reference (`CreateChatDialog.vue` & `main.css`)
1.  **Dialog Structure**:
    *   Glassmorphism background (`var(--bg-glass)`, `backdrop-filter`).
    *   Borderless header (`padding: 0 !important` override).
    *   Custom close button (absolute position top-right).
    *   Padding management (content padding vs header padding).
2.  **Typography & Icons**:
    *   Bold headers (`font-weight: 800`).
    *   Mode toggle pills (`background: var(--bg-page)`, `border-radius: 14px`).
3.  **Form Elements**:
    *   Inputs: `var(--bg-page)`, shadow inset.
    *   Buttons: Primary gradient (if applicable) or solid primary color, `height: 48px`, `border-radius: 14px` (action-btn-full).

## Proposed Changes to `JoinChatDialog.vue`

1.  **Dialog Container**:
    *   Ensure `.ez-modern-dialog` class is used.
    *   Update CSS to match `CreateChatDialog` overrides (reset header/body padding).

2.  **Header**:
    *   Align "Join Chat" title style.
    *   Align "Close Button" style (absolute position, distinct hover effect).

3.  **Mode Switcher**:
    *   Refine the "Passport/Invite" toggle pill to match `CreateChatDialog` or general system aesthetic (cleaner look).

4.  **Form Layout**:
    *   Ensure input field spacing and sizing (Large size).
    *   Standardize "Join" button (full width, improved shadows).

5.  **Result State**:
    *   Use the same "Result Summary" structure as `CreateChatDialog.vue`:
        *   Icon size/animation.
        *   Title weight.
        *   Action buttons style.

## Verification
*   Visual consistency check (via code structural comparison).
*   Ensure all existing functionality (`v-model`, events) remains intact.
