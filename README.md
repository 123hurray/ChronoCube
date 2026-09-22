# ChronoCube

ChronoCube 是一个 Kotlin + Jetpack Compose Android 应用。它从用户选择的视频中均匀提取时间切片，将帧沿深度轴堆叠，并用 OpenGL ES 3.0 实时绘制成可交互的“时空立方体”。

## 已实现

- 使用系统文件选择器导入本地视频，不申请媒体库全量权限
- 默认提取 48 帧，可在 16–256 帧之间调整并重新生成
- 视频帧沿时间轴固定堆叠，播放时高亮帧连续向后推进，并在相邻采样帧之间平滑过渡
- 单指拖动按屏幕 X/Y 方向进行轨道旋转
- 双指捏合缩放，双指扭转绕屏幕 Z 轴旋转，双击或按钮复位视角
- 时间滑杆浏览，按原视频时长连续播放，支持暂停和回到起点
- 可分别调整非高亮亮度、非高亮堆栈透明度、高亮帧透明度与运动轨迹增强；高亮帧独立合成，不会被其他切面覆盖
- 以首帧为背景参考，通过 GPU 差分突出运动对象的时空轨迹
- 适配手机和平板横竖屏

## 技术结构

- UI：Jetpack Compose + Material 3
- 状态：ViewModel + StateFlow
- 解帧：MediaMetadataRetriever，在后台协程执行
- 3D 渲染：OpenGL ES 3.0，自定义顶点/片元着色器
- 最低系统：Android 8.0（API 26）
- 编译 SDK：API 35
- JDK：17

主要代码位置：

- app/src/main/java/com/chronocube/app/ui：Compose 界面与状态
- app/src/main/java/com/chronocube/app/video：视频时间采样与解帧
- app/src/main/java/com/chronocube/app/gl：OpenGL 立方体与视频切片渲染

## 使用

1. 启动应用并点击“导入视频”。
2. 选择 MP4、MOV 或设备解码器支持的其他视频。
3. 等待切片提取完成。
4. 在黑色预览区单指拖动进行轨道旋转；双指捏合缩放、双指扭转画面方向。
5. 点击“播放”，观察高亮帧沿固定时间切片连续向后推进；也可拖动时间滑杆查看任意时刻。
6. 展开参数面板，调整时间深度、非高亮亮度、前后景透明度、运动增强或切片数。

固定机位、主体运动明显的视频最接近参考图效果。手持视频也能生成体积，但相机运动会让整个背景形成轨迹。

## 云构建

推送到 main 或 master 分支后，Android CI 会自动：

1. 配置 JDK 17 和 Gradle 8.9。
2. 生成标准 Gradle Wrapper。
3. 运行单元测试。
4. 构建 Debug APK。
5. 上传名为 ChronoCube-debug 的构建产物，保留 14 天。

在 GitHub 仓库的 Actions 页面进入对应构建，即可从 Artifacts 下载 APK。

## 性能说明

帧纹理会占用设备内存和显存。默认 48 帧时最长边不超过 512 像素；提高到 256 帧时，应用会依据 64 MiB CPU 纹理预算自动降低单帧分辨率。低端设备建议使用 24–64 帧，高端设备可逐步提高。
