# 08 · 游标分页 vs OFFSET 分页

> 触发：backlog L6 到点（Phase 3-3.8）。实测载体：`GET /api/v1/feed` 与个人网格两条游标链路。

## 现象

feed 与网格两个列表接口都要求"无限滚动、顺序稳定"。初学者直觉写法是 `LIMIT n OFFSET p` 前端传页码。本项目的实现却让客户端只回传一个 `nextCursor` 数字，首页甚至什么都不传。两种写法在同样数据下跑出来的翻页体验有肉眼可见差别——特别是边刷边删的场景。

## 原因

| | OFFSET | 游标(id < ?) |
|---|---|---|
| 深翻页代价 | O(offset)：数据库仍需扫过并丢弃前 offset 行 | O(log n) 定位 + 读 limit 行（命中 `idx_feed_cursor(visibility,id)` 有序索引）|
| 插入并发语义 | 新行插入会让后页整体后移 → **重复看到已看内容** | 游标锚定 id，新旧无感 |
| 删除并发语义 | 页内行被删会整页错位 | 只少一行，邻近不动 |
| 元素漂移跳变 | 有 | 无 |

一句话：OFFSET 的分页坐标是"第几行"，而行的集合是活的；游标的坐标是"最后看到的 id"，与集合变化解耦。

## 原理

1. WHERE `visibility=1 AND id < #{cursor}` ORDER BY `id DESC` 正好构成复合索引 `(visibility,id)` 的连续区间扫描——EXPLAIN 里就是 range，无 filesort。
2. **hasMore 的探测式判定**：每次取 `limit+1` 行，多于 limit 就说明还有下一页，裁掉最后一行返回——避免为判断"还有没有"多发一条 COUNT。
3. 边界约定集中在两处：首页 cursor 缺省用 `Long.MAX_VALUE` 兜底进同一条 SQL；`hasMore=false` 时 nextCursor 显式置 null（契约 §1.3），前端据此停哨兵。
4. 主键即时间序的自增 id 让"按时间倒序"免费获得；若将来按其它键排序，游标必须换成复合值（排序键+id 联合防重复），这是二期上推荐流时要记得的坑位。

## 我怎么验证的

- `test-e2e38.mjs` ⑧/⑧b：`limit=1` 请求拿到 `nextCursor` 数值且 `hasMore=true`；以该 cursor 取第二页后**不包含第一页元素**；
- ⑬：删除一篇文章后立即重新拉首页，该文章消失且首屏缓存已被 DEL 重建——游标视图实时反映软删；
- EXPLAIN 实测（可自行运行）：`EXPLAIN SELECT ... WHERE visibility=1 AND id < 999999 ORDER BY id DESC LIMIT 11` 显示 key=idx_feed_cursor，type=range。

## 可复用结论

1. 面向无限滚动的列表一律游标分页；带页码跳转的管理后台才配 OFFSET/PageHelper。
2. 探测式 hasMore（limit+1）是零成本方案，永远别为翻页 UI 发 COUNT。
3. 游标的稳定性前提是排序键唯一或复合化；默认主键降序是最安全的起点。
