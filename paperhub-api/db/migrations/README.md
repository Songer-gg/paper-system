# v1.0旧库增量迁移

仅适用于本项目已识别的v1.0结构；应用不自动迁移。用户本轮已授权直接修正旧库。本机迁移已完成，后续启动无需重复执行。

`v1.0_to_v1.1.sql`补充认证版本、逻辑删除、共享去重、历史快照、CHECK及索引，调整保留历史的外键。旧重复字段old_paper_id/similarity重命名为original_paper_id/title_similarity。迁移不删库、不删除原业务行，新增一条系统迁移日志。

运行器从后端config/local.properties读取连接设置，仅接受显式loopback地址的paperhub库。真实凭据不写命令行。先用`scripts/verify_mysql.py`在临时MySQL生成当前schema结构基线。

```powershell
# 在paperhub-back目录；使用已经存在的SQL快照，不生成新备份
python -B scripts/migrate_local_mysql.py --mysql-bin '你的MySQL/bin' --snapshot '已有快照.sql'
# 内置相同的隔离演练及逐行比较，验证通过后才执行本机迁移
python -B scripts/migrate_local_mysql.py --mysql-bin '你的MySQL/bin' --snapshot '已有快照.sql' --apply
# 迁移后的只读复查
python -B scripts/check_local_mysql.py --mysql-bin '你的MySQL/bin'
```

旧结构通过230项基线对应的旧版精确指纹识别；未知、已部分迁移的结构会拒绝更改。已为v1.1时返回无需操作。隔离演练使用临时MySQL、随机端口和核实的数据目录；旧快照中的建表语句仅在该隔离实例运行，不导入现有开发库。演练检查结构、原始各行数据与允许的变更，再执行已有库的增量ALTER。

数据处理口径：

- 私有论文review_status规范化为NULL。没有版权确认的旧共享记录保留并改为rejected，等待上传者确认版权后重新提交；不推断用户已授权。本机有4条此类样例。
- 旧版权时间和PDF哈希保持未知NULL。原/storage/*.pdf为演示路径，不公开提供文件；需重新真实上传。
- 旧历史标题使用迁移时当前标题捕获，原有举报标题优先保留。缺少的提交/创建时间记录迁移捕获时间，不能解释为原始事件发生时间；原笔记updated_at保持不变。
- 原元数据缺失、历史关联已丢失、旧offline原因不明、唯一键冲突及约束不合法时，演练拒绝更改现有库；不得用伪造值绕过。

MySQL DDL隐式提交，普通ROLLBACK不能回退整个迁移。部分失败时停止应用、核对阶段并前向修复，不能重跑schema.sql或盲目重试。若需要回退，应先把已有快照恢复到隔离库并验证；确认无迁移后业务写入后，人工回退数据库及对应应用版本。完整验收见[第2批交付](../../../paperhub-back/BATCH_2_DELIVERY.md)。
