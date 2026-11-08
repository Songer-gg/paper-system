# 依赖安全扫描（第6批更新：2026-11-08）

状态：扫描已完成；**生产上线安全门禁未通过**。固定 Java 8 / Spring Boot 2.7.18 技术栈可以继续功能开发，不能将构建和功能测试通过当作无漏洞。

第2批OSV扫描62个运行时依赖坐标，19个组件命中公告，共113个去重OSV公告ID；第1批为59个坐标、相同的19个组件及113个公告。第2批引入PDFBox 2.0.35、fontbox及commons-logging，没有新增命中。命中表示版本落在公告范围，不能直接等同于本应用存在相同数量的可利用漏洞；需要逐项分析配置、功能可达性及修复版本。62个JAR的17566个基础class通过Java8字节码检查。

第3批未新增运行时依赖；重新解析、Java8字节码检查及OSV扫描完成，仍为62个坐标、19个受影响组件和113个去重公告。分类/标签归属、SQL可见性、并发锁顺序和敏感计数审计已通过集成测试；这些功能检查不解除依赖上线门禁。

第4批同样未新增运行时依赖；重新解析、Java8基础字节码检查与OSV扫描完成，仍为62个坐标、19个受影响组件和113个去重公告。共享权限、版权双重确认、实际文件哈希、SQL筛选边界、命名锁回收与状态/历史回滚已验证；上线安全门禁继续保持未通过。

原始结果：`target/dependency-audit.json`；依赖树：`target/dependencies.tgf`。可运行 scripts/audit_dependencies.py 重新扫描。报告只包含公开依赖坐标，不上传代码或凭据。

第6批未新增运行时依赖，重新解析、Java8字节码检查及OSV扫描完成：62个坐标、17566个基础class兼容Java8，仍有19个组件命中113个去重公告。资料字段白名单、邮箱并发唯一、BCrypt字节边界、随机重置、版本撤销、管理员互相禁用保护，以及个人数据/私有来源审计失败拒绝均已通过真实MySQL验证；不解除既有依赖上线门禁。

## 命中组件

| 组件                                                | 版本   | 命中公告数 | 示例公告                                                            |
| --------------------------------------------------- | ------ | ---------- | ------------------------------------------------------------------- |
| org.springframework.boot:spring-boot                | 2.7.18 | 2          | [CVE-2025-22235](https://osv.dev/vulnerability/GHSA-rc42-6c7j-7h5r) |
| ch.qos.logback:logback-classic                      | 1.2.12 | 1          | [CVE-2023-6378](https://osv.dev/vulnerability/GHSA-vmq6-5m68-f53m)  |
| ch.qos.logback:logback-core                         | 1.2.12 | 8          | [CVE-2025-11226](https://osv.dev/vulnerability/GHSA-25qh-j22f-pwp8) |
| org.apache.logging.log4j:log4j-api                  | 2.17.2 | 1          | [CVE-2026-49844](https://osv.dev/vulnerability/GHSA-qv9r-c865-cp47) |
| org.yaml:snakeyaml                                  | 1.30   | 7          | [CVE-2022-25857](https://osv.dev/vulnerability/GHSA-3mc7-4q67-w48m) |
| org.apache.tomcat.embed:tomcat-embed-core           | 9.0.83 | 40         | [CVE-2025-55668](https://osv.dev/vulnerability/GHSA-23hv-mwm6-g8jf) |
| org.apache.tomcat.embed:tomcat-embed-websocket      | 9.0.83 | 1          | [CVE-2024-23672](https://osv.dev/vulnerability/GHSA-v682-8vv8-vpwr) |
| org.springframework:spring-web                      | 5.3.31 | 6          | [CVE-2024-38809](https://osv.dev/vulnerability/GHSA-2rmj-mq67-h97g) |
| org.springframework:spring-webmvc                   | 5.3.31 | 16         | [CVE-2026-41845](https://osv.dev/vulnerability/GHSA-3chg-m5w7-qfv5) |
| org.springframework:spring-context                  | 5.3.31 | 2          | [CVE-2024-38820](https://osv.dev/vulnerability/GHSA-4gc7-5j7h-4qph) |
| org.springframework:spring-expression               | 5.3.31 | 5          | [CVE-2026-41849](https://osv.dev/vulnerability/GHSA-775g-4xr8-78h8) |
| org.springframework.security:spring-security-web    | 5.7.11 | 4          | [CVE-2026-47838](https://osv.dev/vulnerability/GHSA-293q-567p-wmwq) |
| org.springframework.boot:spring-boot-autoconfigure  | 2.7.18 | 1          | [CVE-2026-41001](https://osv.dev/vulnerability/GHSA-ggg2-9786-hwc8) |
| com.mysql:mysql-connector-j                         | 8.0.33 | 1          | [CVE-2023-22102](https://osv.dev/vulnerability/GHSA-m6vm-37g8-gqvh) |
| com.fasterxml.jackson.core:jackson-databind         | 2.13.5 | 11         | [CVE-2026-50193](https://osv.dev/vulnerability/GHSA-3wrr-7qpf-2prh) |
| com.fasterxml.jackson.core:jackson-core             | 2.13.5 | 3          | [CVE-2026-89425](https://osv.dev/vulnerability/GHSA-7hhh-6rmp-j9qf) |
| org.springframework:spring-core                     | 5.3.31 | 2          | [CVE-2026-41848](https://osv.dev/vulnerability/GHSA-659m-px2c-25wj) |
| org.springframework.security:spring-security-core   | 5.7.11 | 3          | [CVE-2024-22257](https://osv.dev/vulnerability/GHSA-f3jh-qvm4-mg39) |
| org.springframework.security:spring-security-crypto | 5.7.11 | 1          | [CVE-2025-22228](https://osv.dev/vulnerability/GHSA-mg83-c7gq-rv5c) |

## 当前措施与后续处理

- 保留用户指定的 Boot 2.7.18/BOM，不通过本批静默升级 Spring、Tomcat 或 Security。后续补丁路线须形成兼容性和回归记录。
- 已禁用表单认证、HTTP Basic、默认自动生成账号、静态资源映射及跨域通配；采用 Bearer JWT、状态与版本复核、密码 UTF-8 72字节上限、绑定 SQL、统一错误响应。以上措施不能消除所有依赖公告。
- Spring/Tomcat/Security 为上线前优先复核项；Jackson、SnakeYAML、日志库和驱动按公告受影响条件逐一判断，不能仅因当前入口未使用就永久忽略。
- 密码长度限制有单元/HTTP入口校验，但不以此直接宣告 Security crypto 的全部公告关闭。
- 升级补丁需继续保持 Java 8 约束；若某项修复无法在既定技术栈获得，明确记录受影响范围、缓解措施及上线决策，不标为已修复。
- 本批不执行自动部署；继续业务开发不会自动解除此上线门禁。

## Java 8 兼容性验证

实际 JDK：1.8.0_471。Maven 编译、单元、MySQL 集成及可执行 JAR 的 HTTP 验证通过。另扫描59个运行时JAR的16598个基础class，未发现高于Java8（major=52）的基础字节码。Java8不加载的multi-release分支和module-info已明确排除。兼容性检查与安全公告检查分别报告。
