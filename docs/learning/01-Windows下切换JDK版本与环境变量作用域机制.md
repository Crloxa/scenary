# 01 · Windows 下切换默认 JDK 版本与 JAVA_HOME 的作用域机制

> 触发：完成 Phase 0（环境验证）时的强制沉淀（[AGENTS §4.2](../../AGENTS.md)）。本文记录 2026-08-27 本机 JDK 17 → 21 切换的全过程，亲测环境：Windows 11 + Git Bash + ZCode shell。

## 现象

执行 Phase 0 验收命令 `java -version` 输出 OpenJDK **17.0.17**，而手册要求 21.x。进一步检查发现：

- `where java` 第一个命中是 `C:\Users\admin\.jdks\ms-17.0.17\bin\java.exe`；
- JAVA_HOME 在 **Process / User / Machine 三个作用域**都指向 `ms-17.0.17`；
- User 与 Machine 两级的 PATH 里各有一条 `...\ms-17.0.17\bin`；
- 机器上明明已经装好了 `.jdks\ms-21.0.11` 和 `ms-21.0.9`，只是默认值没切过去。

## 原因

1. 多个 JDK 共存时，`java.exe` 解析完全由 PATH 顺序决定；`mvn` 则由 JAVA_HOME 决定它自己的运行 JVM。两者是独立的两条解析路径。
2. 该机器当年把 JDK 17 写死进了**系统级**（Machine）PATH 与 JAVA_HOME，而不是引用 `%JAVA_HOME%\bin`——所以只改用户级不够：Windows 生效 PATH = **Machine PATH 在前 + User PATH 拼接在后**，Machine 条目永远先命中。

## 原理

- **同名环境变量的合并规则**：登录时先加载 Machine 变量再叠加 User 变量。普通同名变量（如 JAVA_HOME）**User 值覆盖 Machine 值**；唯一例外是 PATH，做拼接而非覆盖，顺序 Machine 前、User 后。
- **REG_EXPAND_SZ 自动展开**：`.NET [Environment]::GetEnvironmentVariable('Path','Machine')` 读出的是**展开后**的字符串。本机 Machine PATH 里那条其实是 `%JAVA_HOME%\bin` 式的引用（REG_EXPAND_SZ），读取时已被展开成当时的实际值（ms-17）——这就是为什么改完 Machine JAVA_HOME 后再读 Path 直接看到 ms-21 路径。
- **提权边界**：普通进程无法写 HKLM / Machine 作用域。本次用 `Start-Process -Verb RunAs -Wait` 弹 UAC 由 PowerShell 子进程代写，结果通过临时日志文件回传判断成败。
- **环境广播**：写完注册表还应 `SendMessageTimeout(HWND_BROADCAST, WM_SETTINGCHANGE, 'Environment')` 通知 Explorer；此后**新启动**的终端才能拿到新值。而**已在运行的父进程（如 ZCode）及其子 shell 继承的是启动瞬间的快照**，广播救不了它们。

## 我怎么验证的

```bash
# 1) Maven 用到了新 JDK（runtime 一行指向 ms-21.0.11 即成功）
export JAVA_HOME="C:\\Users\\admin\\.jdks\\ms-21.0.11"; mvn -version | head -4
#   Java version: 21.0.11 ... runtime: C:\Users\admin\.jdks\ms-21.0.11 ✔
# 2) 注册表回读确认两级持久化（重开终端才见分的部分直接看源头）
powershell -NoProfile -Command '([Environment]::GetEnvironmentVariable("Path","Machine")) -split ";" | Select-String jdk'
#   RAW=[C:\Users\admin\.jdks\ms-21.0.11\bin] ✔
```

附带发现：换到 21 后 `mvn -version` 的 `platform encoding` 从 **GBK 变成 UTF-8**（Java 18+ 默认字符集改为 UTF-8），今后 Maven 构建里中文注释/资源不再乱码。

## 可复用结论

1. 换 JDK 版本优先复用 `.jdks\` 里 IDE 装好的现成目录，不必重新下载安装器。
2. 完整切换需要四处全改：**两级 JAVA_HOME + 两级 PATH 条目**；缺一级就可能出现"mvn 是 21 但 java 还是 17"的割裂。
3. **已运行程序的会话内不受影响**：本项目在本会话后续阶段跑 mvn/java 时须显式前缀 `export JAVA_HOME=C:\Users\admin\.jdks\ms-21.0.11`（或重启 ZCode 应用）。
4. 提权脚本的执行结果拿不到 stdout——**约定一个结果日志文件路径**，主进程 sleep 后读文件判断成败。
5. 回滚方法：把同样四处指回 `C:\Users\admin\.jdks\ms-17.0.17`（Git Bash 无缝可逆）。
