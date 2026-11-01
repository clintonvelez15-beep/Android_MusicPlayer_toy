# Android_MusicPlayer_toy
# MusicPlayer —— Android

湖南科技大学《智能终端系统及应用开发》实践课项目（Java / Android）

普通的本地音乐播放器：支持本地音频导入与管理、播放进度控制、
相册自定义背景，并在应用被关闭后完整恢复上次的歌单、歌曲与播放位置。

## 功能特性

- **本地音频导入**：通过系统文件选择器多选导入，`audio/*` 类型过滤，自动查重
- **播放控制**：播放 / 暂停（图标状态同步）、进度条实时刷新、拖动跳转、播完自动循环连播
- **歌单管理**：底部滑出式歌单（PopupWindow + 入场动画）、当前播放高亮（自定义 BaseAdapter）、长按删除、一键清空
- **自定义背景**：从相册选图，`centerCrop` 等比铺满不变形，重启应用后背景完整保留
- **曲绘展示**：读取音频内嵌封面（MediaMetadataRetriever），圆形裁剪 + 旋转动画（ObjectAnimator）
- **全量状态恢复**：强杀进程后重开，歌单 / 当前曲目 / 播放位置 / 背景全部还原

## 技术要点

| 设计         | 说明                                                                                             |
| ------------ | ------------------------------------------------------------------------------------------------ |
| 播放统一入口 | `playSong()` 收敛三条切歌路径（播完连播 / 列表点击 / 后续上下首），新增入口仅需两行            |
| 积极持久化   | 播放位置每 500ms 定期落盘 + 数据变化即存，不依赖 onPause/onDestroy（强杀不丢数据，误差 ≤500ms） |
| Uri 长期权限 | `takePersistableUriPermission` 将读取授权登记入系统授权表，保证背景重启可恢复                  |
| 三级降级     | 歌名/封面：内嵌元数据 → 文件名裁剪 → 默认资源，数据缺失不崩溃                                  |
| 动画分流     | 一次性入场用 View Animation（translate），可控状态旋转用 Property Animation                      |

## 运行方式

1. Android Studio 打开项目（根目录含 `settings.gradle.kts`）
2. 等待 Gradle 同步完成（Gradle Wrapper 已包含，无需本地安装 Gradle）
3. 连接设备（minSdk 24，模拟器 / 真机均可）→ Run

## 目录结构

```
app/src/main/
├── java/com/example/music_0921/MainActivity.java   # 全部业务逻辑（单 Activity 架构）
└── res/
    ├── layout/activity_main.xml                    # FrameLayout 层叠：背景 + 卡片
    ├── layout/sheet_songs.xml                      # 底部歌单面板容器
    ├── anim/slide_up.xml                           # 面板滑入动画
    └── drawable/                                   # 圆角卡片 / 面板背景 / 矢量图标
```

## 已知待改进

- 上一首 / 下一首按钮（实现路径已明确：取模下标 + playSong 统一入口）
- 背景缩放裁剪（拟手写实现，不引入现成库）
- 播放内核迁移至 Service（后台续播 + 通知栏控制）
- 歌单条目视图复用与元数据缓存
