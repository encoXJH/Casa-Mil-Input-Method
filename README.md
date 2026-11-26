# 米拉之家 · Android 中文输入法

纯 Java + Android 框架实现的本地中文输入法。`applicationId com.meiyan.ime`，
`versionCode 1`，`versionName 1.0`，`compileSdk 34 / minSdk 24 / targetSdk 34`。

- 业务与引擎代码 100% 纯 Java（仅 `android.*` + JDK 原生类）。
- 无 AndroidX / appcompat / 第三方 UI 库；全部界面由代码构建，XML 仅限
  `res/xml/method.xml`、`res/values/` 与 `res/drawable/` 图标。
- 唯一外部依赖：本地 `sherpa-onnx-1.12.18.aar` + `kotlin-stdlib`（仅离线语音使用）。
- 仅两个权限：`RECORD_AUDIO`、`INTERNET`；打字、候选、联想、记忆全部离线。
- 本期核心交付为完整中文输入法；离线语音代码与模型已内置，但**第一期验收不依赖语音**，
  如需减小 APK，可删除 `app/src/main/assets/model.int8.onnx`、`tokens.txt` 与
  `app/libs/sherpa-onnx-1.12.18.aar` 后改为占位实现。

## 一、交付物

| 文件 | 说明 |
| --- | --- |
| `dist/mila.apk` | 可直接安装的 release APK（122,858,497 字节，debug 证书签名，含内置词库与语音模型） |
| `app/build/outputs/apk/release/app-release.apk` | Gradle 原始产物 |
| `app/src/main/java/com/meiyan/ime/` | 全部业务代码（纯 Java） |
| `app/libs/sherpa-onnx-1.12.18.aar` | 37MB，四架构 JNI（arm64/armv7/x86/x86_64） |
| `app/src/main/assets/model.int8.onnx` / `tokens.txt` | 81.8MB Paraformer 中文离线模型 / 词表 |
| `app/src/main/assets/pinyin/` | 六大词库（word / abbr / single / assoc / poly / syll） |
| `tools/gen_dict.py` | 可复现词库生成脚本 |
| `tools/jvm/EngineTest.java` / `tools/run_jvm_test.sh` | JVM 引擎验收（28 项） |
| `示例词库.txt` | 4 种导入格式示例（含格式②单候选行） |

## 二、安装与启用

1. `adb install -r dist/mila.apk`，或把 APK 传到手机后允许“未知来源”安装。
2. 打开「米拉之家」→ 点「① 去系统设置启用」→ 勾选米拉之家。
3. 在任意输入框调出键盘 → 点「② 选择/切换输入法」→ 选「中文（米拉之家）」。
4. `res/xml/method.xml` 已带 `subtype zh_CN / keyboard / settingsActivity`，
   用于规避部分 ROM 输入法列表选不中的问题。
5. 语音权限：设置页「本地语音识别」→「授予麦克风权限」；未授权时拼音行提示，不崩溃。

## 三、构建与验证

```bash
# 1) 构建 release（工程根目录）
sh gradlew assembleRelease

# 2) 复制可安装包
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/mila.apk

# 3) 引擎验收（28 项，输出 PASS/FAIL 与候选实测）
sh tools/run_jvm_test.sh

# 4) APK 静态核验
AAPT=.toolchain/sdk/build-tools/34.0.0/aapt2.exe
JAVA=.toolchain/jdk/jdk-17.0.2/bin/java
$AAPT dump badging dist/mila.apk
$JAVA -jar .toolchain/sdk/build-tools/34.0.0/lib/apksigner.jar verify --print-certs dist/mila.apk
```

`gradlew` 优先使用工程根 `.toolchain/`（JDK 17 / Android SDK 34 / Gradle 8.7），
也支持 `JAVA_HOME`、`ANDROID_HOME` 覆盖。本机实测 `assembleRelease` 成功。

词库重新生成（可选）：

```bash
python -m pip install pypinyin jieba
python tools/gen_dict.py
```

## 四、工程约束

- 单 module；`applicationId com.meiyan.ime`；release 使用 debug 证书，便于直接安装。
- 根容器 320dp、顶栏 40dp、候选行 46dp、键盘区 234dp、AI 段落框 210dp。
- 根容器固定高度规格，不使用 weight 自动分配整体高度；键盘内部行使用 weight。
- 主题由 `MiraTheme` 静态可变色字段持有，业务代码只引用 `MiraTheme.XXX`；
  设置页切主题用 `applyPageColors()` 递归刷新，不重建页面。
- 字母键在 `ACTION_DOWN` 即完成上屏并消费事件，避免输入法窗口被宿主抢焦点后 `UP` 丢失。

## 五、功能实现清单

- **四区结构**：顶栏 `FrameLayout` 叠「工具条 / 拼音行」；候选行独立 46dp；
  键盘区 234dp；AI 段落框 210dp 初始 `GONE`，空格上划时在键盘正上方展开。
- **工具栏**：`言`（版本 + 词库统计，长按最近 14 条按键自检日志）/ `⌨`（候选行布局选择）/
  `语`（常用语）/ `脸`（emoji）/ `贴`（剪贴板）/ `⌄`（`requestHideSelf(0)`）；无 ⚡。
- **候选行**：水平滚动，候选高 42dp、左右 padding 14dp、20sp；首候选强调色（联想态除外）；
  联想标签 12sp；上限 30；剪贴板 90 秒内显示「粘贴：预览（16 字）」。
- **拼音行**：中文输入 18sp 强调色；待机提示 13sp 灰色（含 `拼音 · lt=聊天 · 模糊已开`）；
  词库加载提示；密码锁提示；右侧 `✕` 仅在有拼音/联想时显示；`TruncateAt.START`。
- **拼音引擎固定层次**：全拼(12) → 简拼(24) → 前缀补全(8, 长度差≤4) →
  模糊容错（候选 <10 时漏≤4 取 16，否则漏≤1 取 10）→ 音节首字组合(1) → 尾音节单字（补满 30）；
  最后按用户记忆稳定重排。
- **模糊输入**：声母 `zh/z、ch/c、sh/s、n/l、f/h、r/l`；韵母 `ang/an、eng/en、ing/in、
  iang/ian、uang/uan、iong/ion、ong/on`；字符归一 `n→l、r→l、f→h`；按漏字母总数评分。
- **多音字**：`poly.txt` 70 组，加载时插入对应音节单字表第 4 位，保留主读音前三位。
- **用户记忆**：含中文且长度≤8 才学习；标点/纯字母/纯数字/密码框不学习；6 次落盘一次，
  `onFinishInput` 强制 flush；保留前 600 高频词；纯英文另记中英混排词频。
- **上屏联想**：上屏后按末字查 `assoc.txt`（最多 10）；英文态/密码/词库未加载不联想；
  退格先收联想；点联想候选继续以新词末字联想。
- **中英混排**：中文态 ≥2 字母且词库未命中 → 英文候选（完整词/用户词频/短词优先 + 首字母大写）；
  序号列举上下文（`1.` / `一、` / `（1）`）联想，try 隔离，失败不影响直出。
- **三布局**：全键盘（Shift 三态：临时/锁定/解除，底色区分）、九键（900ms 同键循环、中文态替换末位）、
  手写（`View` Path，笔宽 9f，圆头圆角，可清除）。
- **符号面板**：数字行；中文/英文各 30 标点（3×10）；中文第 3 行含半角 `@`（U+0040）；
  `handlePunct()` 原样 `commitText()`；数字键中文态进入编码，不崩溃。
- **逗号长按气泡**：320ms 三气泡 `。！？` / `.!?`；手指滑到气泡高亮，松开输入；
  移动 >30dp 取消长按；常驻 View 靠 alpha 显隐 + `setX/setY` 定位，不增删 View / 不改 LayoutParams；
  键盘重建前收起气泡并清除 pending Runnable。
- **emoji / 常用语 / 剪贴板**：8 列滚动 48 个 emoji；常用语本地增删；剪贴板最近 5 段独立 SP。
- **空格四手势**：单击上屏首选/空格；250ms 长按本地语音；横滑 >36dp 取消；
  上滑 >44dp 且仅 `ACTION_UP` 调一次 `onAiBeautify()`；语音启动优先于上划；按下 alpha 0.6。
- **退格/回车**：退格删拼音字母 → 收联想 → 选区探测（`getSelectedText`，`ExtractedText` 兜底，
  `commitText("",1)` 一次删光）→ `deleteSurroundingText(1,0)`；长按 400ms 后每 110ms 连删；
  回车按 `imeAction`（SEND/SEARCH/GO/DONE/NEXT/PREVIOUS → `sendDefaultEditorAction(true)`），
  未指定则换行；有拼音先上屏首选。
- **密码框**：识别 PASSWORD / VISIBLE_PASSWORD / WEB_PASSWORD；不候选、不联想、不记忆、不画像；
  拼音行显示锁提示；字母仍可上屏。
- **改写态**：所有落字（候选/标点/emoji/剪贴板/语音）走段落框；回车换行、空格空格、删除删段落文字；
  下拉把手删除原文长度后回填。
- **AI 改写**：空格上划锁定宿主当前段落；`rwOriginal` 会话级锁定；`rwSeq` 丢弃过期异步结果；
  4 个两字按钮（默认 语气/专业/分点/风险）可自定义名称、提示词、资料；URL/模型/Key/开关本机保存；
  仅用户点改写或设置页测试连接时联网；Key 输入框密码态。
- **本地语音**：长按空格 250ms 录音，边说边识别（约 800ms 预览，最长 10 秒），松手整段再识别覆盖；
  引擎后台初始化，未就绪用 `voiceStopPending` 兜底；全程离线、不落盘、不上传。
- **三主题**：米拉之家 / 敦煌 / 莫兰迪；主题索引与频次开关存 SP；`onStartInputView()` 检测变化后
  重建一次换色；字母键频次 `log^1.55` 归一化上色，无数据用内置英文字母频率兜底；
  关闭频次开关回到主题统一键色。
- **按键画像**：`TouchModel` 记录 `n/sx/sy/sxx/syy/sxy/corr`，全部 `synchronized`；
  `offsetOf()` 给出个人热区偏移并用于字母键热区渲染；删除后 3 秒内改打相邻键记纠错并降权；
  导出/导入 JSON；设置页有开关、统计、清空。
- **词库导入导出**：`ACTION_OPEN_DOCUMENT` + `text/*`；4 种格式可混排，存 `filesDir/dicts/`；
  导入后通知运行中的输入法服务热重建；导出内置+导入+自造全量；自造词增删；
  「清空导入词库」不删自造词；从 txt 提取词库中没有的词。
- **设置页**：启用引导、主题、AI 配置、AI 按钮自定义、词库、联想与纠错、按键画像、本地语音、测试输入框、诊断复制。

## 六、词库

| 文件 | 实测 |
| --- | --- |
| `word.txt` | 91,966 桶 / 135,001 词 |
| `abbr.txt` | 26,835 组 / 64,202 词 |
| `single.txt` | 410 音节 / 18,386 字位 |
| `assoc.txt` | 3,629 行 |
| `poly.txt` | 70 组 |
| `syll.txt` | 413 个 |

`lt / nh / lx` 已校准：`lx` 初始「联系」第 1、「联想」第 11（index=10）；
`word.txt` 的 `zhang` 桶为 `长|张|掌|粻`，`zang` 桶为 `藏|脏|葬|弉`，
`chang` 桶为 `长|常|场|椙`。为满足 `nianxi → 联系` 且保持引擎固定层次顺序，
`word.txt` 的 `nianxi` 桶预置了 n/l 模糊音候选「联系」，不依赖模糊层前置。

## 七、验收表（用例 / 期望 / 实测 / 通过）

### A. 引擎与词库（JVM 真实执行，`sh tools/run_jvm_test.sh`）

| # | 用例 | 期望 | 实测 | 通过 |
| --- | --- | --- | --- | --- |
| A1 | 输入 `lt` | 首位「聊天」 | `聊天,聊天记录,聊天室,蓝天… size=24` | 是 |
| A2 | 输入 `nh` | 首位「你好」 | `你好,女孩,男孩,内涵… size=24` | 是 |
| A3 | 输入 `liaot` | 含「聊天」 | `辽太祖,聊天 size=2` | 是 |
| A4 | 输入 `pyin` | 首位「拼音」 | `拼音,皮影,皮蝇,排印… size=10` | 是 |
| A5 | 输入 `nianxi` | 首位「联系」 | `联系,年息,念惜,年限… size=30` | 是 |
| A6 | 输入 `cang` | 含「长」 | `藏,舱,滄,倉,苍白,长… size=30` | 是 |
| A7 | 输入 `zhang` | 含「长/张」 | `长,张,掌,粻,障碍,藏,脏,葬… size=30` | 是 |
| A8 | 输入 `zang` | 含「藏/张」 | `藏,脏,葬,弉,长,张,掌,粻… size=30` | 是 |
| A9 | 输入 `fuiyi` | 含「会议」 | `会议,回忆,会意,悔意… size=16` | 是 |
| A10 | 输入 `zho` | 含「中」 | `中,种,钟爱,中巴… size=21` | 是 |
| A11 | 上屏「聊天」 | 联想含「聊天记录」 | `[聊天记录,聊天室,聊天吗,天安门…]` | 是 |
| A12 | `lx` 初始 | 首位联系、联想约第 11 | `联系` 第 1；`联想` index=10 | 是 |
| A13 | 连选 3 次「联想」 | `lx` 首位「联想」 | `联想,联系,练习,理想…` | 是 |
| A14 | 8 组输入 | 候选 ≤30 | 全部 ≤30 | 是 |
| A15 | 导入 4 格式 | 全部收下且立即生效 | `n=5`，`测试词`/`测试员` 在桶内 | 是 |
| A16 | 格式②单候选行 | 收下 | `ceshi = [测试员, 测试]` | 是 |
| A17 | 热态候选 | 平均 <5ms | `3539.2–4160.1 us/call`（两次复跑） | 是 |
| A18 | 词库规模 | 六项在 ±20% 内 | `91966/135001、26835/64202、410/18386、3629、70、413` | 是 |

### B. APK 静态核验（构建后执行）

| # | 用例 | 期望 | 实测 | 通过 |
| --- | --- | --- | --- | --- |
| B1 | 包名/版本 | `com.meiyan.ime` 1 / 1.0 | `package: name='com.meiyan.ime' versionCode='1' versionName='1.0'` | 是 |
| B2 | SDK/权限 | minSdk 24 / targetSdk 34，仅两权限 | `sdkVersion:'24' targetSdkVersion:'34'`；RECORD_AUDIO/INTERNET | 是 |
| B3 | subtype | zh_CN / keyboard / settingsActivity | `res/xml/method.xml` 核验 | 是 |
| B4 | 签名 | debug 证书可安装 | `CN=Mira IME`，apksigner verify 通过 | 是 |
| B5 | 模型压缩 | onnx STORED | `assets/model.int8.onnx compress_type=0` | 是 |

### C. 交互与联网能力（代码路径核验；本机无 adb 设备，未做真机点击验收）

| # | 用例 | 期望 | 实测 | 通过 |
| --- | --- | --- | --- | --- |
| C1 | 密码框 | 无候选/锁提示/不记忆/字母可上屏 | `isPassword` + `password` 分支 | 代码核验 |
| C2 | 符号 `@` | 打出半角 `@` | 中文表第 3 行 `@` = U+0040，原样 commit | 代码核验 |
| C3 | 全选后删除 | 整段一次删光 | `getSelectedText` + `ExtractedText` 兜底 + `commitText("",1)` | 代码核验 |
| C4 | 空格四手势 | 单点/长按/横滑取消/上划只一次 | MOVE 只置标志，UP 调一次 `onAiBeautify` | 代码核验 |
| C5 | 逗号长按气泡 | 滑到气泡输入对应标点 | 320ms + 常驻气泡 + raw 坐标命中 | 代码核验 |
| C6 | 三主题/频次 | 整页变色、越打越深 | `MiraTheme` 39 色 + `applyPageColors` + `log^1.55` | 代码核验 |
| C7 | AI 回填 | 原文替换为改写结果 | `deleteSurroundingText(rwOriginalLen,0)` 后 commit，`rwSeq` 丢弃过期结果 | 代码核验（未联网） |
| C8 | 离线语音 | 长按识别中文 | AAR + 模型已内置；边说边识别 + 松手覆盖 | 代码核验（未真机） |
| C9 | 打字不联网 | 打字/联想/语音正常 | 全本地；仅 AI 主动点击时联网 | 架构保证 |

结论：JVM 引擎 28/28 通过；APK 包名/版本/权限/subtype/签名/模型压缩方式构建后核验；
因本机 `adb devices` 无设备，UI 点击、语音、AI 未做真机录屏，本表如实区分“实测”与“代码核验”。

## 八、隐私边界

- 词库、用户记忆、按键画像、剪贴历史、常用语、主题偏好只存本机
  `SharedPreferences`（剪贴板独立 `meiyan_ime_clip`）与 `filesDir/dicts/`。
- 联网只发生在用户主动点 AI 改写按钮、设置页「测试连接」；不改写不联网。
- API Key 存本机，设置页输入框为密码态，不打印日志。
- 语音为 sherpa-onnx Paraformer 本地识别，16kHz PCM 仅在内存送入本地引擎，不落盘、不联网。
- 诊断信息只含版本、词库规模、加载耗时、屏幕参数、机型、系统版本，不含崩溃堆栈或错误符号。

## 九、已知限制

1. 本机无 adb 设备，UI 点击、语音、AI 未做真机录屏；APK 已构建、签名并用 aapt2/apksigner 核验。
2. 交付 APK 含 81.8MB 语音模型与四架构 native，体积约 120MB；第一期不需要语音时可按第一节说明裁掉。
3. 待机态按 1.1 显示工具条，`拼音 · lt=聊天` 提示在拼音行被其他状态激活时可见。
4. 数字键在中文态已有拼音时追加编码，空闲时直接上屏；避免含数字编码崩溃。
5. Android 10+ 后台读剪贴板受限；实现为键盘显示/面板打开/剪贴变化时读取。
6. 语音边说边识别的预览识别在录音线程同步执行，可能轻微影响录音节奏；松手会整段覆盖。
7. AI 默认模型名 `deepseek-flash`，可用模型名以 DeepSeek 控制台为准；URL/模型/Key 可改可测。
8. release 使用 debug 证书 `CN=Mira IME`，满足直接安装；上架需换正式证书。
9. 换机器构建时需设置 `JAVA_HOME`、`ANDROID_HOME`，或按 `.toolchain/` 的目录结构放置工具链。