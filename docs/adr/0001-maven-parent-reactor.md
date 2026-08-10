# ADR 0001：根 Maven 父工程与后端子模块

- 状态：已接受
- 日期：2026-08-09
- 决策人：项目 Owner

## 背景

仓库根目录 `knowflow-sanjin` 需要作为 Maven 父工程，现有 Spring Boot 工程
`knowflow-app` 作为其子模块。此前只有子模块 POM，无法从仓库根目录形成明确的 Maven Reactor，
依赖和插件版本也只能由子模块独立管理。

## 决策

- 根坐标使用 `knowflow.sanjin:knowflow-sanjin:0.1.0-SNAPSHOT`，`packaging=pom`。
- 根 POM 继承 Spring Boot Parent，并声明 `knowflow-app` 模块。
- Java、Testcontainers、MyBatis-Plus、Springdoc 和 Spotless 版本由根 POM 管理。
- `knowflow-app` 继承根 POM，只保留模块自身依赖、Spring Boot 打包和集成测试配置。
- Maven Wrapper 暂时保留在 `knowflow-app/`，仓库脚本通过该 Wrapper 和根 POM 构建 Reactor。

## 影响

后续新增 Java 模块必须加入根 `<modules>` 并继承根 POM。Vue 前端仍是独立 npm 工程，不作为
Maven Module，也不由 Maven 生命周期隐式构建。
