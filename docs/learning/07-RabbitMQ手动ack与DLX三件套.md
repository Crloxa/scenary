# 07 · RabbitMQ 手动 ack / requeue / DLX 三件套

> 触发：backlog L5 到点（Phase 3-3.7）。本篇所有结论在 `docs/dev/test-thumbnail37.mjs` 的实跑与服务端日志中有对应出处。

## 现象

缩略图管线需要回答三个问题：处理成功后怎么告诉 broker？处理失败的消息会不会无限回炉？彻底失败的去哪儿？实测结果：

```
INFO  thumb ready mediaId=7 800x483 bytes=7683          # 成功路径 basicAck
WARN  malformed media.uploaded payload, dead-lettering   # 毒消息A：解析失败
WARN  attempt 1/2 failed for mediaId=987654321           # 毒消息B：本地第1次重试
WARN  attempt 2/2 failed for mediaId=987654321           # 第2次仍失败
ERROR thumbnail failed after retries -> dlq             # nack(requeue=false)
# DLQ 深度 0 → 2，两条坏消息各自躺好；期间正常上传照常出片
```

## 原因

手动 ack 模式下 broker 只认信令：`basicAck` 删除消息；`basicNack(requeue=true)` 会**立即重新投递**；`requeue=false` 时若队列带 x-dead-letter 参数则转投死信交换机。三者组合出以下纪律：

1. **绝不盲目 requeue**：业务性故障（行不存在、内容不可解码）重试一万次也不会自愈。重投只保留给"broker 自己抖"的场景——而那类瞬时抖动用消费者内部的有限次 try（本项目 2 次）更可控、更可观测。
2. **yml 侧再上一道保险**：`default-requeue-rejected: false` 把监听容器抛出的未捕获异常也默认走 nack，防止某个分支漏 catch 引发 requeue 风暴。
3. **死信不是垃圾场而是档案室**：bad message 带原始字节完整躺在 `media.dlq`，人工排查/补投有据可依；P8 起可识别的媒体处理失败会在进入 DLQ 前自动写入 `status=2`、失败原因和时间，避免前端永久轮询。

## 原理（两个容易踩的暗坑）

- **队列参数声明时固化**：`x-dead-letter-*` 写在首次 declare，之后想改参数只能换新队列名（老队列会以 PRECONDITION_FAILED 拒绝）。拓扑演进要提前想到这一点。
- **发布早于绑定 = 无声黑洞**：本项目 id=6 这条媒体永远停在 status=0——它的 `media.uploaded` 在 3.6 期先于队列绑定发出，exchange 直接丢弃且管理插件的 publish 计数器在这套环境里恒为 0，错误无处可看。"发布成功"必须靠**消费端或队列深度**实证，任何单向计数都不足为凭。

## 我怎么验证的

- 正常链路：1000×604 上传 → ~800ms 轮询到 status=1 → 800×483（限边且等比）；300×200 小图保持原尺寸（验证了 scale=min(1,…,) 不放大的实现）；
- 失败链路：malformed JSON 与幽灵 mediaId 两枚毒消息 → 本地重试日志逐跳可见 → DLQ 深度 0→2；
- 幸存者检验：毒消息倾泻后正常消息依旧出片，证明 manual ack 不阻塞他人。

## 可复用结论

1. 默认姿势：`acknowledge-mode=manual` + 业务内有限重试 + 最终 `nack(requeue=false)` + 队列挂 DLX——四件固定搭配覆盖绝大多数后台队列场景。
2. 死信队列要**常做深度告警**（本期运维基线 TODO），它涨起来就是事故现场签名。
3. 新增队列拓扑时，把"先绑队列再开流量"写进部署顺序；对已发布但无消费者的时段保持零容忍。
