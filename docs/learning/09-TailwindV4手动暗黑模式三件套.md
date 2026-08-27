# 09 · Tailwind v4 手动暗黑模式的三件套

> 触发：首次为项目引入夜间模式（本轮主题重构：薄荷绿 × 青冥 × 茶白）。

## 现象

需求要"支持黑夜模式"，直觉上以为要给每个组件补一套类名。实际落地后发现只需三层固定搭配，改动集中在两处文件加一轮语义 token 替换，业务组件零逻辑变化。

## 原因

Tailwind v4 把"变体定义"开放给了 CSS 侧。默认 `dark:` 变体绑定系统偏好 `prefers-color-scheme`，无法被用户手动覆盖；要做"记忆用户选择"的开关，必须先把变体指向一个类钩子。

## 三件套

1. **变体重绑**（main.css）：`@custom-variant dark (&:where(.dark, .dark *));` —— 之后所有 `dark:` 类跟随 `html.dark` 而非媒体查询；
2. **语义 token 双层映射**：
   ```css
   @theme { --color-paper: var(--c-paper); ... }   /* inline 映射进工具类 */
   :root  { --c-paper: #f7f4ec }                    /* 茶白日间 */
   html.dark { --c-paper: #101d1a }                 /* 青冥夜间 */
   ```
   组件里写的永远是 `bg-surface text-ink border-line` 这类语义类，配色随变量整体翻转，不存在组件级 if-dark 分支；
3. **FOUC 预判脚本**（index.html head 内联）：应用 JS 加载前就按 localStorage/系统偏好挂好 `html.dark`——放在框架启动后初始化必然闪一帧错误底色。

切换开关只是 `classList.toggle('dark')` + 记忆 localStorage（utils/theme.js，约 20 行）。

## 我怎么验证的

- `npm run build` 通过后访问 ：5173，导航栏 🌙 点击即时互换配色并持久化；
- DevTools 关闭再打开页面，dark 保持；删掉 localStorage 键则回落系统偏好；
- 主色阶直接替换 `--color-brand-*` 数值，全部旧有 `bg-brand-500` 类自动换装薄荷绿——验证了"品牌阶做成主题槽"的收益。

## 可复用结论

1. 先把颜色收敛成语义 token 再谈主题，否则每加一套皮肤都是全局大扫除；
2. v4 的 `@custom-variant dark (&:where(.dark, .dark *))` 是手动模式的官方姿势，别再用 postcss 老插件思路；
3. 防闪烁只能靠 head 内联脚本提前挂类，任何"mounted 时再设置"都是错误答案。
