# iWeather

一个 Android 上的现代天气应用。UI 参考 iOS 18 版「天气」的布局与材质，
数据来自彩云天气与中国气象局，动效支持 PAG，玻璃材质做了真实背景模糊。

[![CI](https://github.com/MokoKing666/iWeather/actions/workflows/ci.yml/badge.svg)](../../actions)
![Platform](https://img.shields.io/badge/platform-Android%2015%2B-3DDC84)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF)
![Compose](https://img.shields.io/badge/Compose-BOM%202024.10-4285F4)

---

## 目录

- [项目简介](#项目简介)
- [功能说明](#功能说明)
- [安装与使用](#安装与使用)
- [目录结构](#目录结构)
- [架构与关键技术决策](#架构与关键技术决策)
- [贡献指南](#贡献指南)
- [授权与致谢](#授权与致谢)

---

## 项目简介

这是一个**从零重写**的天气应用。起点是一个 2016 年的仿 iPhone 天气 Demo
（`compileSdk 23` + Gradle 2.x + jcenter，早已无法编译），
现代码完全基于 Jetpack Compose，与旧工程没有任何共享代码。

做这个项目时踩了不少坑，其中一部分是**第三方服务的实际行为与文档不符**，
另一部分是**平台本身的硬限制**。这些结论都记在[架构与关键技术决策](#架构与关键技术决策)里，
改动相关代码前建议先读。

### 它长什么样

iOS 18 版「天气」的三层结构：单城市详情页、城市列表卡片、天气地图。

> 截图待补。`legacy/screenshots/` 里是 2016 年旧版的，别用。

### 技术栈

| 层 | 选型 |
|---|---|
| UI | Jetpack Compose + Material 3 |
| 状态 | `StateFlow` + `AndroidViewModel`（单一状态源） |
| 网络 | OkHttp（手写请求，未用 Retrofit）+ kotlinx.serialization |
| 后台 | WorkManager（每小时天气检查与通知） |
| 地图 | osmdroid（OpenStreetMap，无需 API Key） |
| 小组件 | Glance（2×2 / 3×2 / 4×2） |
| 动效 | 自绘 Canvas 粒子 + PAG（可选） |
| 日志 | Timber |

`minSdk 35`（Android 15）· `targetSdk 35` · AGP 8.6 · Kotlin 2.0.21

---

## 功能说明

下面按 iOS「天气」的官方功能逐项对照，**如实标注实现状态**。

### ✅ 天气预报

| 功能 | 状态 | 说明 |
|---|---|---|
| 当前天气 | ✅ | 温度、天气现象、体感温度、最高/最低 |
| 未来 10 天预报 | ✅ | 温度区间条（全周统一尺度）+ 降水概率 |
| 逐小时预报 | ✅ | 24 小时，支持温度 / 降水 / 风三种视图切换 |
| 温度 / 风 / 湿度 / 体感 / 紫外线 / 气压 / 能见度 | ✅ | 摄氏度 ⇄ 华氏度可切换 |
| 极端天气警报 | ✅ | 按蓝 / 黄 / 橙 / 红级别配色 |
| 露点 | ✅ | Magnus 公式本地计算（温度 + 湿度） |
| 未来一小时每分钟降水 | ⚠️ | 已实现，但**属彩云企业套餐**，免费账号拿不到该字段 |
| 相关新闻报道 | ❌ | 见[未实现的功能](#未实现的功能) |

### ✅ 位置

| 功能 | 状态 | 说明 |
|---|---|---|
| 搜索任意位置 | ✅ | 中国城市走**内置离线库**（2567 条），全球走系统地理编码 |
| 存储常用位置 | ✅ | 分页浏览、长按拖拽排序、删除 |
| 跨设备同步 | ⚠️ | 依赖 Android 自动备份（换机恢复）。**没有**账号体系与实时同步 |

### ✅ 天气地图

| 功能 | 状态 | 说明 |
|---|---|---|
| 降水 / 气温 / 空气质量 / 风 四个图层 | ✅ | 带数值刻度图例 |
| 点任意位置查询 | ✅ | 逆地理编码 + 拉取该点实况，弹锚定气泡 |
| 逐分钟 / 24 小时动画 | ❌ | 只有当前时刻的图层，没有预报播放器 |

### ✅ 空气质量与天文

| 功能 | 状态 | 说明 |
|---|---|---|
| 空气质量 | ✅ | 国标 AQI 六段色带 + 六项污染物 |
| 与昨天对比 / 健康建议 | ❌ | 未实现 |
| 日出日落 | ✅ | 彩云优先，缺失时用 **NOAA 天文公式**兜底（含极昼极夜） |
| 月相 / 月出月落 / 农历 | ✅ | **全部本地计算**，无网络请求 |
| 历史趋势 | ❌ | 未实现 |

### ✅ 通知

用 WorkManager 每小时后台检查一次。

| 功能 | 状态 |
|---|---|
| 雨雪即将开始 / 停止 | ✅ |
| 极端天气警报 | ✅ |

⚠️ **依赖彩云的企业套餐**：免费 Token 的响应里**没有 `alert` 也没有 `minutely`**，
所以免费账号下这两类通知不会触发。

### ✅ 小组件与外观

- **2×2 / 3×2 / 4×2** 三种桌面小组件，可固定城市或优先跟随定位
- 跟随系统深色模式 / 可关闭动画 / 城市分页带橡皮筋阻尼 / 下拉刷新
- 天气背景随现象与昼夜渐变，雨雪云雾尘用 Canvas 粒子绘制

### 未实现的功能

`通知` 已完成，以下仍未做：

| 功能 | 原因 |
|---|---|
| 相关新闻报道 | **没有合理的免费数据源**。Apple 用的是 Apple News（Apple 专有）；Google News 国内不可达；百度/新浪的接口基本已下线，剩下的都要商业 Key。爬网页既不稳定也有合规风险 |
| 历史趋势 | 数据源已验证可用（中国气象局气候平均 + Open-Meteo 存档），**只差 UI** |
| 空气质量对比昨天 / 健康建议 | 数据不全，需要另找源 |
| 地图逐分钟 / 24 小时动画 | 需要逐帧的雷达数据，当前的站点数据只有单一时刻 |
| 繁体字形 | 字体子集基于 GB2312，繁体字会缺字。`tools/build_fonts.py` 里已预留 `TRADITIONAL_WEIGHTS` |

---

## 安装与使用

### 前置条件

- **JDK 17**
- **Android SDK**：`compileSdk 35`、`build-tools 35.0.0`
- 一台 **Android 15+** 的设备或模拟器
- Python 3（**仅**重新生成字体与图标时需要）

### 1. 克隆并准备密钥

```bash
git clone https://github.com/MokoKing666/iWeather.git
cd iWeather
```

天气数据需要彩云的 Token。在项目根创建 `local.properties`：

```properties
# 彩云天气（必填，否则拿不到实时天气）
# 免费领取：https://caiyunapp.com/user/token
caiyun.token=你的Token

# 或改用 AppKey + Secret（官方推荐，走 HMAC-SHA256 签名）
caiyun.appKey=你的AppKey
caiyun.appSecret=你的AppSecret

# 高德地图 Web 服务（可选，用于把定位坐标解析成区/县级城市名）
amap.key=你的Key
```

> `local.properties` 已在 `.gitignore` 里，**不会被提交**。
> 也支持环境变量 `CAIYUN_TOKEN` / `CAIYUN_APP_KEY` / `CAIYUN_APP_SECRET` / `AMAP_KEY`。

### 2. 生成字体（可选，但推荐）

仓库里**不含字体文件**，原因见[授权与致谢](#授权与致谢)。

```bash
python tools/build_fonts.py
```

脚本会从你**本机**的 `C:\Windows\Fonts` 读取 SF Pro 与 PingFang.ttc，
子集化 + 合并后写入 `app/src/main/res/font/`。

**跳过这一步也能编译和运行** —— 只是会退回系统字体。
`AppFonts` 是运行时按资源名解析的，找不到就整体不用。

> 脚本默认路径是 Windows 的。macOS / Linux 上改一下 `tools/build_fonts.py`
> 顶部的 `WIN_FONTS` / `PINGFANG_TTC` 即可。macOS 上苹方在
> `/System/Library/Fonts/PingFang.ttc`。

### 3. 构建

```bash
# 调试包
./gradlew assembleDebug

# 发布包（本工程用 debug key 签名，可直接安装）
./gradlew assembleRelease

# 单元测试
./gradlew testDebugUnitTest
```

产物在 `app/build/outputs/apk/`。

### 4. 安装

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

或者把 apk 传到手机点击安装（需允许「安装未知来源应用」）。

### 5. 首次启动

会走一个三步引导：使用说明 → **申请定位权限** → 写入定位城市。
不想给定位权限可以跳过，之后在城市页手动添加。

---

## 目录结构

```
.
├── app/
│   ├── proguard-rules.pro          R8 规则（PAG 的 keep 规则是必须的，见下文）
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/
│       │   │   ├── china_cities.json    内置中国城市离线库（2567 条，137 KB）
│       │   │   └── pag/moonlight.pag    PAG 动效素材（仅晴朗夜晚使用）
│       │   ├── java/com/moko/iweather/
│       │   │   ├── core/                数据层：API 客户端、映射、缓存、定位、天文计算
│       │   │   │   ├── CaiyunClient.kt        彩云 v2.6 客户端（限流 + 429 退避 + HMAC 签名）
│       │   │   │   ├── CaiyunDtos.kt          彩云响应 DTO
│       │   │   │   ├── CmaMapClient.kt        中国气象局全国站点气象图
│       │   │   │   ├── SnapshotMapper.kt      响应 → 领域模型
│       │   │   │   ├── WeatherRepository.kt   网络 + 缓存
│       │   │   │   ├── Prefs.kt               SharedPreferences 持久化
│       │   │   │   ├── WeatherNotifications.kt WorkManager 后台检查与通知
│       │   │   │   ├── Astronomy.kt           露点 / 月相 / 月出月落 / 农历
│       │   │   │   ├── TimeZoneResolver.kt    时区、昼夜判定、NOAA 日出日落
│       │   │   │   ├── LocationProvider.kt    系统定位
│       │   │   │   ├── PlaceResolver.kt       逆地理编码回退链
│       │   │   │   ├── ChineseGeocoder.kt     高德逆地理编码
│       │   │   │   ├── NominatimClient.kt     OSM 逆地理编码
│       │   │   │   ├── ChinaCityRepository.kt 内置城市库
│       │   │   │   ├── MapTileSources.kt      地图底图源
│       │   │   │   ├── WeatherMapLayer.kt     地图图层定义与配色
│       │   │   │   └── Models.kt              领域模型
│       │   │   ├── ui/
│       │   │   │   ├── theme/           配色、深色模式、字体
│       │   │   │   ├── weather/         天气现象 → 图标 / 配色 / 粒子；动画背景；PAG 封装
│       │   │   │   ├── components/      玻璃材质、图表、卡片等复用组件
│       │   │   │   ├── home/            主页分页 + 单城市详情页
│       │   │   │   ├── city/            城市管理与搜索
│       │   │   │   ├── map/             天气地图（Overlay 绘制）
│       │   │   │   ├── onboarding/      首次启动引导
│       │   │   │   └── settings/        设置、开源许可
│       │   │   ├── widget/              Glance 小组件（三种布局）
│       │   │   ├── MainActivity.kt      极简页面栈
│       │   │   ├── WeatherApplication.kt
│       │   │   └── WeatherViewModel.kt  单一状态源
│       │   └── res/
│       │       ├── font/                SF Pro + 苹方（**不进版本库**，见下）
│       │       ├── mipmap-*/            启动图标（含自适应图标）
│       │       └── xml/                 备份规则、小组件声明
│       └── test/                        单元测试（34 个，全是纯计算/解析）
├── tools/
│   ├── build_fonts.py              从系统字体生成 SF Pro + 苹方（子集化 + 合并）
│   └── make_icon.py                从一张源图生成全套启动图标
├── .github/workflows/ci.yml        CI：assembleDebug + testDebugUnitTest + lint
└── legacy/                         旧版工程（2016 年的 Java Demo，未参与编译）
```

---

## 架构与关键技术决策

**这几条都是踩过坑之后定下来的，改动前建议先看原因。**

### 彩云限流：间隔必须 ≥ 1200ms

彩云免费额度大约**每秒只放行 1 个请求**。实测：

```
间隔 300ms  → 25 个请求只成功 1 个     HTTP 429 TOO MANY REQUESTS
间隔 1200ms → 6/6 全部成功
```

`CaiyunClient` 的最小间隔是 1200ms，并带 429 退避重试。
**不要为了「快一点」调小** —— 这会同时打挂多城市刷新。

### 免费 Token 拿不到 `minutely` 与 `alert`

实测响应里 `result` 只有：

```
realtime, hourly, daily, primary, forecast_keypoint
```

**没有 `minutely`，也没有 `alert`。** 也就是说免费账号下：
逐分钟降水卡片不显示、降水开始/停止通知不触发、灾害预警卡片不显示。
代码对这些字段做了空值兜底，不会崩，但功能是静默失效的。

### 逐时用 `datetime`、逐天用 `date`，不能按下标 zip

彩云的时间字段名两套不同，且 `hourly`/`daily` 是"按变量分数组、每项自带时间"，
必须**按时间对齐**。另外时间格式是 `2026-10-06T18:00+08:00`（分钟精度、带偏移、无秒）。

### 地图图层走气象局，不走彩云

彩云按坐标计费，画一张图要几百个采样点，配额撑不住。改用：

```
https://weather.cma.cn/api/map/weather/1
```

一次请求 = **2440 个观测站**（约 310 KB），**不需要 Key**，裸 User-Agent 即可。

返回的 `city` 是**无字段名的混合类型数组**，按下标读。各列含义用
`api/now/54511`（北京）逐列比对确认：

```
[0] 站号  [1] 城市名  [2] 国家  [3] 保留
[4] 纬度  [5] 经度    [6] 气温(℃)
[7] 白天天气现象  [8] 代码  [9] 白天风向  [10] 白天风力  [11] 保留
[12..15] 夜间的现象/代码/风向/风力
[16] 城市拼音缩写  [17] 行政区划码
```

降水由天气现象文字反推，风由风力等级换算。**下标写错不会编译报错、只会画错图**，
所以有 `CmaMapParsingTest` 用真实响应样例锁住语义。

> 风向表有个坑：「东北风」**包含子串**「北风」，用 `contains` 匹配时
> 三字方位必须排在二字之前，否则 45° 会被判成 0°。

### 天气图层用 osmdroid Overlay，不用 Compose Canvas

最初把图层画在叠于 `AndroidView` 之上的 Compose `Canvas` 里，
靠 `MapListener` 自增状态触发重绘 —— **地图缩放时点位不跟随**。
因为地图的绘制节奏和 Compose 的重组节奏是两套东西。

改用 `Overlay`：`MapView` 每帧直接调用 `draw()`，投影天然同步。

### 字体：必须在构建阶段合并

Compose 的 `FontFamily` 只能按**字重**选字体，**不支持按字符回退**。
而唯一能自定义回退的 `Typeface.CustomFallbackBuilder` 产出
`android.graphics.Typeface`，Compose 侧包装它的 `AndroidTypeface` 是
**`internal`** 的，App 代码实现不了。

所以只能让字体文件自己同时含西文与汉字。但 `fontTools.merge`
**拒绝合并 CID-keyed CFF**，而苹方正好是。绕法是双方都转成 TrueType 轮廓
（cu2qu）+ 苹方 upem 从 1000 缩放到 2048 + 删掉不一致的 `vhea`/`vmtx`，再合并。
路上还踩了 `maxp` 字段缺失、空字形没有 bbox、`post` 2.0 需要额外字形名表。

### 玻璃材质的分层问题

早期卡片只有 17%→7% 的白色渐变、没有深色垫底，
压在地图瓦片或热力图这种高频背景上几乎透明，上下层文字糊在一起。
现在统一垫了一层暗色底衬。

**真·背景模糊**用 `GraphicsLayer` 录制背景再带 `BlurEffect` 重绘 ——
Compose 没有 `backdrop-filter`，`Modifier.blur()` 只能模糊自己。
需要 Android 12+，且 **`AndroidView` 里的内容录不进 Compose 图层**
（所以地图上的浮层做不了真模糊）。

### PAG 的 ProGuard 规则是必须的

PAG 是 native 实现，通过 JNI 反射回调 Java 层。
**不写 `-keep class org.libpag.** { *; }`，R8 会把成员当成"没被引用"删掉** ——
编译期不报错、debug 也不复现（debug 不混淆），
只有 release 里播到对应动效时才会在 native 层崩。

### 通知排期用 `KEEP` 而不是 `UPDATE`

`UPDATE` 会在每次冷启动时把下次执行时间往后推，**等于永远跑不到**。

### Android 13 的通知权限

`POST_NOTIFICATIONS` 是运行时权限。开关打开时必须**先申请权限再启用** ——
只开开关不申请，通知会被系统丢掉，用户看到的是"开了但从来没通知过"。

---

## 贡献指南

欢迎提 Issue 和 PR。这个项目还很小，没有繁重的流程，但请遵守以下几点。

### 开发环境

```bash
./gradlew assembleDebug        # 构建
./gradlew testDebugUnitTest    # 单元测试（提交前请确保全绿）
./gradlew lintDebug            # 静态检查
```

### 提交前

1. **跑通 `testDebugUnitTest`**。目前 34 个测试，全是纯计算与解析，不依赖网络。
2. **不要提交 `local.properties`、`res/font/*.otf`、`dist/`** —— 都已在 `.gitignore` 里，
   如果被你不小心 `git add -f` 了请撤回。
3. **改动了"架构与关键技术决策"里提到的代码，请在 PR 描述里说明原因**，
   那些地方看着奇怪但都有实测依据。

### 代码风格

- 注释用中文，**解释"为什么"而不是"做了什么"**。
  这个项目里大部分注释记录的是踩坑原因，请不要当成噪音删掉。
- 涉及第三方接口的魔数（下标、字段名、时间格式）请在旁边注明来源。
- 新增解析逻辑请**补一个用真实响应样例的回归测试** ——
  这类代码改错了不会编译报错，只会静默算错（风向表就是个例子）。

### 适合新手的任务

- 繁体字形支持（`tools/build_fonts.py` 里已预留 `TRADITIONAL_WEIGHTS`）
- 历史趋势卡片（数据源已验证：中国气象局气候平均 + Open-Meteo 存档）
- Compose UI 测试与截图测试
- 文案抽到 `stringResource`（目前是硬编码中文）

### 已知的技术债

按优先级排：

1. **`WeatherViewModel` 无法单元测试** —— 它在构造函数里直接 new 了
   client / 定位 / 地理编码。需要抽 Repository 接口 + 构造注入。
2. **依赖落后**：Kotlin 2.0.21、Compose BOM 2024.10.01、OkHttp 4.12.0、
   `targetSdk 35`。升级请**一步一验**，尤其注意 osmdroid / Glance / PAG 的兼容性。
3. **release 零可观测性** —— 没有接 Crashlytics / Sentry。
4. **包体 41.6 MB**，其中 29 MB 是字体、16 MB 是 PAG 的 native 库。

---

## 授权与致谢

### ⚠️ 字体：请不要分发本安装包

| 字体 | 版权 | 授权 |
|---|---|---|
| SF Pro Display / SF Pro Text | Apple Inc. | **Apple 专有** |
| 苹方-简 PingFang SC | Apple Inc. | **Apple 专有** |

两者的许可**都只覆盖 Apple 平台**。把字形打进 Android 包属于超出授权范围，
更不能再分发。因此：

- **字体文件不进版本库**（见 `.gitignore`）
- 需要的话自己跑 `python tools/build_fonts.py`，从**本机**系统字体生成
- **请不要把自己构建的、含字体的 APK 公开分发**

### 开源组件

App 内 **设置 → 关于 → 开源许可与数据来源** 有完整页面。主要依赖：

| 组件 | 版权 | 许可证 |
|---|---|---|
| AndroidX（Compose / Activity / Core / Lifecycle / Glance / Graphics） | The Android Open Source Project | Apache-2.0 |
| OkHttp | Square, Inc. | Apache-2.0 |
| Kotlin / kotlinx.serialization | JetBrains s.r.o. | Apache-2.0 |
| osmdroid | osmdroid contributors | Apache-2.0 |
| PAG（libpag） | Tencent | Apache-2.0 |
| WorkManager | The Android Open Source Project | Apache-2.0 |
| Timber | Jake Wharton | Apache-2.0 |

### 数据来源

| 用途 | 来源 | 是否需 Key |
|---|---|---|
| 天气数据 | [彩云天气](https://caiyunapp.com/) API v2.6 | 需要 |
| 全国站点气象图 | [中国气象局](https://weather.cma.cn/) | 不需要 |
| 中国城市库 | 本项目内置离线资源 | — |
| 全球城市检索 | Android `Geocoder` | — |
| 地点解析 | [高德地图](https://lbs.amap.com/) Web 服务 | 需要 |
| 地图底图 | CARTO / [OpenStreetMap](https://www.openstreetmap.org/) | — |

底图数据 © OpenStreetMap 贡献者（ODbL 1.0）。

### 本项目自身

未指定许可证。如果要公开分享，**请自行补一个**（MIT / Apache-2.0 都是常见选择），
同时记得处理上面提到的字体问题。
