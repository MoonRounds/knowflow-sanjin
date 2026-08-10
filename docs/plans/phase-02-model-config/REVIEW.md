# Phase 2 REVIEW：ModelConfig 与 Provider Compatibility

## Review 目标

确认动态模型配置不会泄漏 Secret、覆盖历史 Revision 或把 Provider 差异扩散进业务代码。

## 重点检查

- API Key 是否可能出现在 DTO、日志、异常、OpenAPI example、测试快照或 Git history。
- 加密是否使用认证加密、随机 nonce 和外部主密钥，而非自制算法/固定 IV。
- Revision 是否真正不可变；编辑是否覆盖旧行。
- default/utility 设置是否只引用 Owner 自己的 enabled ModelConfig。
- Test Connection 失败是否仍允许保存配置。
- Utility Capability Test 是否验证真实 Schema，而非只发一句 Hello。
- Base URL 是否可访问 localhost、私网、内嵌凭据或危险 redirect。
- 是否出现 OpenAI/DeepSeek/Qwen 业务 if/else。
- Live tests 是否从默认 CI 隔离。
- 前端掩码更新是否会意外把掩码字符串当新 Key 保存。

## 必跑验证

- 所有 encryption/Revision/Owner tests。
- Stub contract suite。
- SSRF cases。
- OpenAPI 和前端 typecheck/build。
- 检查日志和响应中没有 Secret。
- 在具备本地 Secret 时抽查两家 Live Smoke；没有 Secret 时明确记录未运行，不伪造结果。

## 不应报告为缺陷

- 没有 Conversation/SSE。
- 没有 Router/Extraction 实际调用 Utility Model。
- 没有后台模型健康检查或成本统计。

