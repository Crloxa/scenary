# 04 · Flyway 版本化迁移纪律：为什么 V1 绝对不能改

> 触发：首次引入 Flyway（Phase 3-3.1）。以下实验全部为 2026-08-27 本机亲测，日志原文摘录。

## 现象

按 03 手册贴入 `V1__init.sql` 启动应用，日志干净利落：

```
Migrating schema `scenary` to version "1 - init"
Successfully applied 1 migration to schema `scenary`, now at version v1
```

随后我故意在 V1 文件头注释里加了"（实验篡改行）"六个字再重启——**一个字的注释变化让应用直接起不来**：

```
Validate failed: Migrations have failed validation
Migration checksum mismatch for migration version 1
APPLICATION FAILED TO START （退出码 1）
```

把改动原样撤销后再重启：

```
Successfully validated 1 migration
Current version of schema `scenary`: 1
Schema `scenary` is up to date. No migration necessary.
```

## 原因

Flyway 首次执行时把每个脚本的 **CRC32 校验和**写进了库里的 `flyway_schema_history` 表（连同版本号、描述、执行时间）。之后每次启动默认做 validate：拿磁盘上脚本现算的校验和去比对历史表里存的值——文件动了哪怕一个空格，比对就失败，且是**启动期硬失败**而不是告警。

## 原理

这套"多此一举"的严格恰恰是版本化迁移的核心契约：

- `flyway_schema_history` ≈ 数据库结构的 git 历史，V1 的 checksum ≈ 已发布 commit 的 hash；
- 历史表里的记录描述的是"**这个数据库经历过什么**"，不是"脚本现在长什么样"。同一条 V1，张三的环境用它建过表、生产库里它带着当时的校验和——你偷偷改内容，新旧环境就持有两个语义不同的 V1，任何后续迁移都在错误的假设上叠加；
- 因此规矩是：**已应用的迁移 = 不可变产物**。发现 DDL 有错或要加列，永远往前追加 `V2__fix_xxx.sql`，绝不回头编辑 V1~Vn；
- 对应地，本地开发最常见的练习题顺序是：改 V1 → validate 炸 → 追加 V2 勘误 → 绿。这次我只做了前两步加还原（V1 本身没错），第三步留给真实需求驱动。

## 我怎么验证的

```bash
# ① 正常首迁后查历史表：checksum 已经入库
docker exec scenary-mysql-1 mysql -uscenary -pscenary_pwd_2026 scenary \
  -e "SELECT installed_rank,version,description,checksum FROM flyway_schema_history;"
#    → 1 | 1 | init | <负数CRC32>

# ② 注释行插入六个字（用 Edit 工具改，仓库钩子禁止 sed 直接写源码）
#    重启 → Migration checksum mismatch，exit 1

# ③ 还原文本，git diff 确认无差异；重启 → validated 通过、No migration necessary
```

## 可复用结论

1. 启动日志出现 `Schema is up to date` = 历史无损、环境可信；每次前后端联调前看一眼这行比查表更快。
2. 想改"已应用"的迁移？只有一条正路：新增 `V(n+1)__*.sql`。生产勘误也一样。
3. 团队协作时给 V 脚本定共享节奏（PR 合并前不过夜），能避免"同一版本号两人内容不同"的第二类冲突——那会以 duplicate version 报错形式炸出来。
