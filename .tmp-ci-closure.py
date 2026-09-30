import io

# learning 索引
p = 'docs/learning/00-学习笔记索引.md'
s = io.open(p, encoding='utf-8').read()
anchor = '| L26 | E2E 视频媒体夹具三重坑（WMF 绕过拦截/PNA/假 Range） | P12-E4 | webkit 媒体进程绕过路由拦截→按浏览器分夹具策略；headless Chromium PNA 拦回环无放行手段；自建 Range 服务必须真切片（[26](26-E2E视频媒体夹具的三重坑.md)） |'
new_line = '\n| L27 | CI 真栈排障三层陷阱（YAML数字化/Hub镜像下架/注解取证） | E5/CI | env数字值必须引号；第三方镜像会从Hub消失（preflight探针+bitnamilegacy）；compose依赖失败清场→commit comment回传现场（[27](27-CI真栈排障的三层陷阱.md)） |'
if 'L27' not in s:
    assert anchor in s
    s = s.replace(anchor, anchor + new_line, 1)
    io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
    print('L27 registered')

# CHANGELOG v2.48.1
p2 = 'CHANGELOG.md'
s2 = io.open(p2, encoding='utf-8').read()
header = '# Changelog · Scenary\n'
if 'v2.48.1' not in s2:
    addition = header + '\n## [v2.48.1] · 2026-09-28 · CI 真栈转绿（checks 修复闭环）\n\n'
    addition += '- **根因与修复**（排障全程见 [learning/27](docs/learning/27-CI真栈排障的三层陷阱.md)）：① job env `JWT_SECRET` 无引号被 YAML 解析为科学计数法（30 字符 < 32 位下限）致 backend 启动即崩——加引号修复；② minio/minio、minio/mc 官方 Docker Hub 镜像已下架（含 GitHub releases 与 dl.min.io 渠道，探针实证）——compose 切 `bitnamilegacy/minio:2025.7.23-debian-12-r5` + mirror.gcr.io + preflight 重试拉齐 + `up --pull never`；③ 建桶容器 mc 镜像无可得 tag——改 `amazon/aws-cli`（s3 mb，桶默认私有=E2 语义）；④ e2e38 ⑧/⑧b 翻页断言对既有数据的隐式依赖——第二公开笔记自种且前移至 feed 缓存预热之前（影响 `.github/workflows/ci.yml`、`docker-compose*.yml`、`docs/dev/test-e2e38.mjs`）。\n'
    addition += '- **诊断通道**：失败现场经内置 GITHUB_TOKEN 写 commit comment（base64+gzip，匿名 API 可读）；workflow 曾临时声明 `permissions: contents: write`，排障完成后已回收；失败 artifact（stack-diagnostics）保留。诊断注入曾两次破坏 workflow 语法（extglob / 块内缩进）——workflow 改动现在本地 yaml+bash 双校验后再推。\n'
    addition += '- **结果**：run 36666790653 全绿——mvn test / vitest 66 / build / 真栈 Compose + chromium e2e / 黑盒 4 套件（auth 18、e2e 31、e3 8、e5 13）/ compose config / 密钥扫描。\n\n'
    s2 = addition + s2
    io.open(p2, 'w', encoding='utf-8', newline='\n').write(s2)
    print('CHANGELOG v2.48.1 done')

# HANDOVER 检查点替换为转绿结论
p3 = 'docs/HANDOVER.md'
s3 = io.open(p3, encoding='utf-8').read()
start = s3.find('> **检查点（2026-09-28')
if start != -1:
    end = s3.find('\n', s3.find('本地 Windows 全绿')) + 1
    s3 = s3[:start] + s3[end:].lstrip('\n')
new5 = '5. **CI 已转绿（2026-09-28，run 36666790653）**：真栈流水线全通过（mvn test/vitest/build/Compose+chromium e2e/黑盒 4 套件/密钥扫描）。根因链与修复沉淀于 learning 27；失败现场诊断走 artifact（stack-diagnostics）。'
old5 = '5. **回滚提示**：'
assert old5 in s3
s3 = s3.replace(old5, new5 + '\n6. **回滚提示**：', 1)
io.open(p3, 'w', encoding='utf-8', newline='\n').write(s3)
print('HANDOVER updated')
