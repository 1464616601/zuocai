# 换电脑继续开发 · 迁移说明

这个项目完全在本地，**云端账号不会同步任何代码**。换机器必须手工搬文件、重装构建环境。
本文是按当前项目状态写的，照做即可跑起来。

---

## 一、先搞清楚：哪些要带，哪些不能带

| 内容 | 带走？ | 原因 |
| --- | --- | --- |
| `app/src/`、`web/`、`docs/` | ✅ 必须 | 全部源码 |
| 根目录 `*.gradle.kts`、`gradle.properties`、`settings.gradle.kts` | ✅ 必须 | 构建配置 |
| `gradle/wrapper/`、`gradlew`、`gradlew.bat` | ✅ 必须 | 有了它不用装 Gradle |
| **`keystore/zuocai-release.jks`** | ✅ **绝对不能丢** | 丢了就无法覆盖升级已装的 APK，只能卸载重装 |
| `README.md`、本文件 | ✅ | 文档 |
| `local.properties` | ❌ **不能带** | 里面写死了本机 SDK 绝对路径，到新机器会指向不存在的目录 |
| `app/build/` | ❌ 不带 | 57MB 构建产物，新机器重新生成即可 |
| `.gradle/`、`.idea/` | ❌ 不带 | 缓存和 IDE 配置，带过去反而可能出问题 |
| `zuocai-v1.1.apk` | ⭕ 可选 | 只是产物，重新构建能得到一样的 |

**结论**：真正要搬的只有约 **1.2MB**（源码 + 证书 + 文档），不是 67MB。

---

## 二、新电脑装什么

三样东西，装完就能编译。以下地址 2026-10 实测国内可直连。

### 1. JDK 17（必须 17，不能 8 也不能 21+）

AGP 8.7 的硬性要求。任选其一：

- 微软构建：`https://aka.ms/download-jdk/microsoft-jdk-17.0.13-windows-x64.zip`
- 清华镜像（Adoptium）：`https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jdk/x64/windows/`

解压到任意目录，例如 `D:\dev\jdk-17`。**不需要**设系统环境变量，构建时用参数指定即可。

### 2. Android SDK（命令行工具即可，不用装 Android Studio）

```bash
# 1) 下载命令行工具（约 130MB）
curl -L -o cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip

# 2) 解压到 SDK 目录，注意必须放在 cmdline-tools/latest 下
mkdir -p D:/dev/android-sdk/cmdline-tools
unzip -q cmdline-tools.zip -d D:/dev/android-sdk/cmdline-tools
mv D:/dev/android-sdk/cmdline-tools/{bin,lib,NOTICE.txt,source.properties} \
   D:/dev/android-sdk/cmdline-tools/latest/

# 3) 安装三个必需的组件（约 200MB）
export JAVA_HOME="D:/dev/jdk-17"
yes | D:/dev/android-sdk/cmdline-tools/latest/bin/sdkmanager.bat \
  --sdk_root=D:/dev/android-sdk \
  "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

### 3. Gradle 不用装

项目自带 wrapper，且 `distributionUrl` 已指向腾讯云镜像，首次构建会自动下载 8.11.1（约 130MB）。

---

## 三、还原步骤

```bash
# 1) 解压源码包到目标位置（路径随意，不要带中文和空格）
cd D:/code
unzip zuocai-src-20261030.zip -d zuocai

# 2) 重建 local.properties（唯一必须手工改的文件）
cd zuocai
echo "sdk.dir=D:/dev/android-sdk" > local.properties
```

> `local.properties` 里的路径用正斜杠 `/`，不要用反斜杠，否则可能被当成转义符。

```bash
# 3) 构建
export JAVA_HOME="D:/dev/jdk-17"
./gradlew assembleRelease
```

首次构建会下载 AGP、AndroidX 依赖和 Gradle 本体，视网速 5–20 分钟。
产物在 `app/build/outputs/apk/release/app-release.apk`。

```bash
# 4) 验证产物可安装
BT=D:/dev/android-sdk/build-tools/34.0.0
$BT/apksigner.bat verify app/build/outputs/apk/release/app-release.apk
```

输出 `Verified using v2 scheme` / `v3 scheme` 就是好的。

### 跑单元测试（30 项，含解析器与游戏逻辑）

```bash
./gradlew testReleaseUnitTest
# 报告：app/build/reports/tests/testReleaseUnitTest/index.html
```

### 网页版

零依赖，双击 `web/preview.html` 就能用。要走 AI 功能则：

```bash
cd web
cp config.example.json config.local.json   # 然后填 API Key
python serve.py                             # 起在 127.0.0.1:8765
```

`web/config.local.json` 里是 API Key，**首次迁移时不会带过去**（已在 .gitignore 里），需要重新填。

---

## 四、容易踩的坑

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `SDK location not found` | 没建 local.properties | 按上面第 2 步重建 |
| `Unsupported class file major version 69` | 用了 JDK 21/25 | 换回 JDK 17，确认 `JAVA_HOME` |
| `Could not find com.android.tools.build:gradle` | 直连 google() 超时 | 项目已在 `settings.gradle.kts` 配阿里云镜像，检查网络 |
| 构建成功但装不上手机 | 用了别的证书签名 | 确认 `keystore/zuocai-release.jks` 是原来那份 |
| 网页版 AI 报"网络请求被拦截" | 直接双击 preview.html | 改用 `python serve.py` 后访问 http://127.0.0.1:8765 |

---

## 五、关于会话记录

代码是文件，搬过去就行。但与 AI 助手的对话历史不在项目目录里：

- 项目内记忆：`<工作区>/.workbuddy/memory/YYYY-MM-DD.md`（本次迁移包里未包含，如需保留请单独拷贝）
- 云端会话：登录同一账号后可以搜索历史对话，但**不会恢复本地文件**

换句话说：**登录账号 ≠ 代码跟着走**。文件靠你自己搬。

---

## 六、更省事的长期方案

现在项目没有版本控制。建议第一次到新机器后就初始化 Git：

```bash
git init
git add .
git commit -m "初始提交"
```

`.gitignore` 已经写好了（排除 build、local.properties、config.local.json、密钥），
之后换机器、换协作者都只需 `git clone`，也不用再担心证书丢——

> 但**证书不要提交到公开仓库**。要么单独保管，要么用私有仓库。

如果只有一个私钥证书要转移，单独拷 `keystore/zuocai-release.jks` 即可（2.7KB）。
口令：库 `zuocai2024` / 别名 `zuocai` / 密钥 `zuocai2024`。
