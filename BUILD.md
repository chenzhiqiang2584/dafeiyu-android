# 编译说明

## 环境要求

| 工具 | 版本 | 说明 |
|---|---|---|
| **JDK** | **17** | AGP 8.x 硬性要求，不能用 JDK 8 |
| **Android SDK** | 34 | Platform 34 + Build-Tools 34.x |
| Gradle | 8.0+ | 用自带的 `gradlew` 即可，不用单独装 |

### JDK 17 在哪

- **装了 Android Studio**：自带位于 `Android Studio/jbr/`，直接能用
- **没装**：去 [Adoptium](https://adoptium.net/temurin/releases/?version=17) 下载

---

## 编译步骤

### 方式一：命令行

```bash
git clone <仓库地址>
cd dafeiyu-android

# 1. 指定 SDK 路径
# Windows
echo sdk.dir=C\:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk > local.properties
# macOS / Linux
echo "sdk.dir=/Users/你的用户名/Library/Android/sdk" > local.properties

# 2. 编译 debug 包
./gradlew assembleDebug          # macOS / Linux
gradlew.bat assembleDebug        # Windows

# 3. 产物
# app/build/outputs/apk/debug/app-debug.apk
```

### 方式二：Android Studio（推荐新手）

1. `File → Open` 选项目根目录
2. 等它自动同步（第一次会下载 Gradle 和 AGP，需要几分钟）
3. `Build → Build Bundle(s) / APK(s) → Build APK(s)`
4. 右下角点 `locate` 找到 APK

---

## 发布签名版

1. 生成 keystore：
   ```bash
   keytool -genkey -v -keystore my.jks -keyalg RSA -keysize 2048 \
           -validity 10000 -alias mykey
   ```

2. 项目根目录建 `keystore.properties`（已被 .gitignore 排除）：
   ```properties
   storeFile=/绝对路径/my.jks
   storePassword=你的密码
   keyAlias=mykey
   keyPassword=你的密码
   ```

3. 编译：
   ```bash
   ./gradlew assembleRelease
   ```

产物在 `app/build/outputs/apk/release/`。

---

## 常见报错

### `Unsupported class file major version` / `无效的源发行版`

**JDK 版本太低。** AGP 8.x 需要 JDK 17。

```bash
java -version        # 看是不是 17
```

如果是 8 或 11，在 `gradle.properties` 里指定：
```properties
org.gradle.java.home=C:/Program Files/Android/Android Studio/jbr
```

### `SDK location not found`

**没写 `local.properties`。** 按上面步骤 1 创建。

或者设环境变量 `ANDROID_HOME`。

### `Failed to find target with hash string 'android-34'`

**没装 SDK 34。** 在 Android Studio 的 SDK Manager 里勾选安装，或：
```bash
sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

### `Could not resolve com.android.tools.build:gradle:8.1.4`

**网络问题。** 国内可以在 `build.gradle` 里加阿里云镜像：
```gradle
repositories {
    maven { url 'https://maven.aliyun.com/repository/google' }
    maven { url 'https://maven.aliyun.com/repository/central' }
    google()
    mavenCentral()
}
```

### 编译成功但装不上（vivo / iQOO）

**签名冲突。** 旧版本用了不同的签名，要先卸载旧版。

或者用 `adb install -r` 覆盖。

---

## 项目为什么不是标准结构

Android 默认要求源码在 `app/src/main/java/`，但本项目把它放在了根目录的 `src/`。

所以 `app/build.gradle` 里这样指定：

```gradle
sourceSets {
    main {
        manifest.srcFile '../AndroidManifest.xml'
        java.srcDirs = ['../src']
        res.srcDirs = ['../res']
        assets.srcDirs = ['../assets']
    }
}
```

**功能上完全等价，只是目录位置不同。**

如果你想改成标准结构，把四个目录移到 `app/src/main/` 下，然后删掉这段 `sourceSets`。
