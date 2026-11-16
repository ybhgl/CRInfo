<div align="center">

# CRInfo

### 一款 Xiaomi HyperOS 风格的安卓国铁信息聚合查询应用

![Android](https://img.shields.io/badge/Platform-Android-green?logo=android)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue)
![Xiaomi HyperOS](https://img.shields.io/badge/Design-Xiaomi%20HyperOS-orange)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin)
![License](https://img.shields.io/badge/License-GPLv3-orange)

</div>

---

## 项目简介

CRInfo 是一款安卓国铁信息聚合查询应用，使用 **Kotlin + Jetpack Compose** 构建，界面基于 [MIUIX](https://github.com/compose-miuix-ui/miuix) 组件库，采用 **Xiaomi HyperOS 设计风格**。

应用把车票、车次、车站三类查询整合到一个界面里。三类查询均支持**在线与离线两种数据源**：在线接入 12306 官方接口与第三方公开数据源，离线则依托离线数据库（按需下载）——即使完全不联网，也能完成完整的车次、时刻与站到站查询。

> [!NOTE]
> **数据来源说明：**
> 车票余票、票价等实时数据来自 12306 官方接口；离线数据来自离线数据库（按需下载）。时刻表、正晚点、车站大屏等信息部分来自第三方公开数据源，仅作聚合展示。出行与购票决策请以 **12306 官方信息为准**。

## 功能概览

### 🚄 车票查询

- 站到站查询车次、发到时刻、历时与票价余票，支持日期选择、出发/到达站快速选取与互换
- 多条件筛选与排序：车次类型、出发时段、席别、始发/终到、只看有票，支持模糊查询（同城扩站）
- **在线 / 离线一键切换**：离线时直达车次、发到时刻、历时与区间里程全部取自离线数据库

### 🚆 车次查询

- 按车次号查询图定时刻表、逐站停靠、开行日历与担当企业、车底型号等信息
- 支持当天是否开行判定，并给出下一个图定开行日
- **离线可用**：车次号输入即时联想、时刻与径路详情均可从离线数据库解析

### 🏫 车站查询

- 按站查看出发 / 到达大屏，展示车次、到发时刻、检票口、参考站台等信息
- 支持按车次号、通到站名、车次类型筛选
- **离线可用**：车站车次可由离线时刻库还原到发信息与参考站台

### 🎨 外观与个性化

- 主题色（跟随系统 / 自定义取色）、深浅色模式、纯黑背景、调色板风格
- 悬浮底栏、顶栏 / 底栏模糊与渐进顶栏模糊、触感反馈
- 预测性返回手势，可选 MIUIX / AOSP 两种转场样式

### ⭐ 收藏与历史

- 收藏常用路线、车站与车次，保留查询历史，支持一键清空

## 技术栈

- **语言：** Kotlin（JVM Target 21）
- **UI：** Jetpack Compose + MIUIX 组件库（含模糊、偏好设置、导航等模块）
- **架构：** Compose + ViewModel + UiState 单向数据流
- **网络与数据：** OkHttp、kotlinx.serialization、Kotlin Coroutines、DataStore Preferences
- **构建：** Gradle Kotlin DSL + Version Catalog，`minSdk 26` / `targetSdk 37`

## 构建

环境要求：Android Studio、JDK 21、Android SDK（compileSdk 37）。

1. 克隆仓库：
   ```bash
   git clone https://github.com/ybhgl/CRInfo.git
   cd CRInfo
   ```
2. 在项目根目录创建 `local.properties`，填入你本机的 Android SDK 路径：
   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```
   > 该文件已被 `.gitignore` 忽略，请勿提交。若需要自行签名，可在此文件中追加 `keyAlias`、`keyPassword`、`storePassword`、`storeFile` 等签名配置。
3. 用 Android Studio 打开项目，或直接执行：
   ```bash
   ./gradlew assembleDebug
   ```

## 开源致谢

CRInfo 的实现受益于以下开源项目与数据源，感谢所有贡献者：

- [MIUIX](https://github.com/compose-miuix-ui/miuix)：Compose / KMP 风格的 UI 组件库；
- [OkHttp](https://github.com/square/okhttp)：网络请求；
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)：JSON 序列化；
- [12306](https://www.12306.cn/)：官方车票数据来源。

## 许可证与第三方声明

CRInfo 主体代码以 [GNU General Public License v3](LICENSE) 发布。任何二次分发或衍生作品须遵循 GPLv3 条款同样开源。

第三方库不因本项目的 GPLv3 而自动转为 GPLv3，各自遵循其原始许可证（主要为 Apache License 2.0 与 MIT）。数据来源相关说明见「项目简介」中的注意事项。

## 反馈与贡献

欢迎在 [Issues](https://github.com/ybhgl/CRInfo/issues) 中提交 bug 反馈或功能建议，也欢迎直接提交 Pull Request 参与改进。如果这个项目对你有帮助，别忘了点个 Star 支持一下。

## 免责声明

本项目仅供学习、研究和个人使用。应用内的列车时刻、正晚点、车站大屏等信息来自第三方公开数据源，可能与实际情况存在偏差，请以 12306 官方发布的信息为准。项目维护者不对因使用本项目产生的任何后果承担责任。
