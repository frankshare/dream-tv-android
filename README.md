# Dream TV · Android

Dream TV 安卓原生版 —— Kotlin + Jetpack Compose 液态玻璃（Liquid Glass）UI，
壁纸沉浸式风格参考 iOS 播放器「拾音」，与桌面版（`../dream-tv/`，KVideo 定制）同源同款。

## 安装

直接把 **`DreamTV-1.1.0-release.apk`** 传到手机安装（微信/网盘/数据线均可）。

- 首次安装会提示「未知来源应用」，允许即可
- 支持 Android 8.0+（API 26），毛玻璃效果需 Android 12+（低版本自动降级为半透明玻璃）
- 78 个苹果CMS资源站已内置，无需任何配置；收藏/历史/设置存本机

## 功能

| 模块 | 说明 |
|---|---|
| 首页 | 豆瓣热门推荐（电影/剧集/高分/国产剧/美剧…页签），点海报直接聚合搜索片名 |
| 搜索 | 78 源并行聚合（每源 20s 超时、16 并发），结果按片名归并显示「N 个源」 |
| 详情 | 海报元信息 + 简介 + 选集 + 收藏 + 继续播放 |
| 播放 | ExoPlayer(HLS)，横屏全屏，**全手势**：单击显隐控制层 / 双击两侧±10s / 双击中间暂停 / **长按 2x 倍速** / **横滑快进快退**（气泡预览 from→to）/ **左半屏竖滑亮度** / **右半屏竖滑音量** / 锁定防误触 / 倍速菜单 0.5x–3x / ±10s·上下一集按钮 / 选集抽屉 / 5s 节流+退出保存进度 |
| 我的 | 收藏/历史双页签，历史带进度条 |
| 设置 | 亮暗主题 / 6 强调色 + 壁纸取色 / 壁纸（梦境夜空·渐变·相册选图）/ 78 源启停管理 |

## 构建

环境要求（本机已就绪）：
- JDK 21 → `E:\zcode\apps\jdk21`
- Android SDK → `E:\zcode\apps\android-sdk`（`local.properties` 已指向）
- Gradle 8.14 wrapper（`GRADLE_USER_HOME=E:\zcode\apps\gradle-home`，已缓存）

```bash
cd dream-tv-android
set JAVA_HOME=E:\zcode\apps\jdk21
set GRADLE_USER_HOME=E:\zcode\apps\gradle-home
gradlew assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`

### 签名

- keystore：`../design/android/dream-tv-release.jks`（密码/别名见根目录 `keystore.properties`，**换机器构建要保留这两个文件**）
- 丢失后只能重新生成新证书，已装用户需卸载重装

## 工程结构

```
app/src/main/java/com/dream/tv/
├── data/           # Models(对齐web版types) / CmsClient(CMS直连) / DoubanClient / SettingsStore / LibraryStore / SourcesRepo
├── ui/
│   ├── theme/      # GlassColors(variables.css移植) / AccentSampler(accent.ts取色移植) / GlassTheme
│   ├── glass/      # GlassSurface(haze毛玻璃) / WallpaperLayer(壁纸+haze源) / GlassTabBar / DtIcons
│   ├── home|search|detail|player|mine|settings/
│   └── nav/        # DreamNav 路由
├── MainActivity.kt # 主题装配 + 壁纸层
└── DreamApp.kt     # Coil ImageLoader(豆瓣图Referer反盗链)
app/src/main/assets/sources.json   # 78源（tools脚本从 web 版 default-sources.ts 烘焙）
```

## 与桌面版的对应关系

| 桌面版（Next.js） | 安卓版 |
|---|---|
| `/api/search-parallel` SSE | 协程并行直连 CMS（无 CORS 概念，代理不需要） |
| `/api/detail` + parsers.ts | `CmsClient.detail`（`$$$` 播放组、m3u8 优先、`#`/`$` 选集解析逐行移植） |
| `/api/douban/*` 反盗链代理 | OkHttp 直连 + UA/Referer 头（Coil 加载器对豆瓣域自动加头） |
| localStorage stores | DataStore（收藏100条上限/历史去重/进度语义一致） |
| variables.css 玻璃变量 | `GlassColors.kt` 逐值移植 |
| accent.ts 取色算法 | `AccentSampler.kt` 色相桶算法移植 |
| ThemeDock / WallpaperSettings | 设置页（壁纸预设+相册选图+取色） |

## 首版未做（与桌面版的差距）

弹幕、IPTV 直播、Redis 云同步、密码门、m3u8 广告切片过滤、繁转简、源订阅导入
（需要时可在设置-源管理的基础上迭代）。

## UI 自测记录（v1.1.0，Android 35 模拟器）

`ui-shots/` 存有全部验收截图。模拟器自测流程：SDK 装 emulator +
`system-images;android-35;google_apis;x86_64` → avdmanager 建 `dreamtv_test`
（WHPX 加速）→ adb install → screencap 逐页验收；播放器手势用
`adb shell input motionevent DOWN/MOVE/UP` 驱动，长按倍速气泡、横滑快进
气泡（快进 26s · 03:48 → 04:14）、亮度竖条、控制层按钮均有截图实证。

v1.0.1 修复：底部 Tab 栏错位、亮暗壁纸对比度、自绘图标缺 fill 全透明、
搜索源禁用不生效、播放退出不保存进度、78 源点阵改进度胶囊、详情 hero
布局、源管理拆子页、全组件 glassClick 按压反馈。
v1.1.0：播放器全手势重构（长按倍速/横滑 seek/亮度/音量/锁定/倍速菜单）、
控制层渐变 scrim 重设计、播放器小图标热区扩到 34-40dp。
（注：模拟器里出现过一次「0 命中」，宿主机直测证实为 CMS 源对本 IP 限流，非 App 问题。）
