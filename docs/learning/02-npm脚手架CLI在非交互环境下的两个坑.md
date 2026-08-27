# 02 · npm 脚手架 CLI 在非交互环境下的两个坑

> 触发：Phase 1 完成（强制沉淀）。亲测环境：Windows 11 + Git Bash（ZCode 持久化 shell）+ create-vue 3.23。

## 现象

用 `npm create vue@latest . -- --force --router --pinia` 在 `frontend/` 目录内生成 Vue 项目，进程**卡死在 "Package name:" 提示符**不再前进。换姿势 `cd 到上级后 rm -rf frontend` 重来时，又报 `rm: cannot remove 'frontend': Device or resource busy`——可目录明明已经空了。

## 原因

1. **非 TTY 环境不会自动跳过交互**：目标目录写成 `.` 时，create-vue 无法从参数推导包名，于是必然弹 "Package name" 输入框；后台/管道环境里 stdin 是 EOF 或无人应答，进程就挂住。`--force/--router/--pinia` 这些 feature 开关只压制功能选择菜单，压不掉项目名询问。
2. **Windows 的目录锁语义**：POSIX 允许删除"作为某进程当前工作目录"的目录，NTFS 不允许——只要还有任何进程（包括持久化 shell 自己）把 cwd 钉在该目录里，`rm -rf` 就报 busy，即使目录已空。

## 原理

- 生成器类的 CLI 判定是否进入交互模式的依据是"必要信息是否齐全"，而不是"是否有 TTY"。绕过交互的正解是把它要问的信息**全部变成命令行参数**：`create-vue <target-dir> --force --router --pinia`。
- Git Bash 这类常驻 shell 的工作目录跨调用持久：上一次调用结束时停在哪，句柄就钉在哪；下一个调用里想删它就必须先把某个调用 `cd` 出去。
- 目标目录为空时，`create-vue <dir>` 直接可用，无需 `--force`（force 只对"非空覆盖"场景有意义）。

## 我怎么验证的

```bash
# ① 失败复现：在 frontend 目录内部以 '.' 为目标 → 卡 Package name
cd frontend && npm create vue@latest . -- --force --router --pinia   # 挂住
# ② 成功路径：从仓库根以显式目录名为目标 → 静默完成
cd /d/Resume_project/scenary && pwd && \
  npm create vue@latest frontend -- --force --router --pinia          # Done 输出
# ③ busy 复现与解除：pwd 停在 frontend 时 rm -rf frontend 报 busy；
#    单独执行 cd /d/Resume_project/scenary 后再操作即成功
```

两次生成的产物一致（package.json 都含 vue-router/pinia），证明参数化路径没有副作用。

## 可复用结论

1. 凡交互式脚手架（create-vue/create-vite/…）进自动化脚本，**必须目标目录名+全套功能旗标一次给全**，并避免用 `.` 当目标。
2. Windows 上删除目录前先确认没有 shell 停在里面；脚本化时把 `cd` 挪开和删除**拆成两个动作**。
3. 同目录下多次尝试脚手架失败会留下半成品，重试前先检查目录内容再决定 rm 还是复用。
