# BPMN Viewer & Editor

[English](README.md) | **简体中文**

> 面向 IntelliJ IDEA 的 Activiti 友好型 BPMN 2.0 流程图 **查看 + 编辑** 插件。

[![Version](https://img.shields.io/badge/version-2.3.2-blue.svg)](CHANGELOG.md)
[![Platform](https://img.shields.io/badge/IntelliJ%20IDEA-2024.3.5%2B-orange.svg)](https://www.jetbrains.com/idea/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.25-purple.svg)](https://kotlinlang.org/)
[![JDK](https://img.shields.io/badge/JDK-17-red.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

在 IntelliJ IDEA 中直接打开、审阅并编辑 `.bpmn` 文件——无需切换到外部建模工具。插件使用纯 Swing + Java2D 渲染，专为使用 **Activiti 5.x / 6.x** 工作流引擎的 Java 后端开发者设计。

---

## ✨ 功能特性

### 查看模式
- 🎨 **高保真渲染**：bpmn.io 视觉风格，支持 30+ 节点类型（任务 / 网关 / 事件 / 容器）
- 📐 **智能布局**：优先使用 BPMN DI 原始坐标；无坐标时回退 Sugiyama 层次布局
- 🧭 **正交连线路由**：Manhattan Grid Router（网格离散化 + A* 寻路），90° 出入线、自动避让节点
- 🔍 **交互浏览**：Ctrl + 滚轮缩放（0.3x ~ 3.0x）、拖拽平移、节点/连线点击查看属性
- ↪️ **代码导航**：一键跳转到 Service Task 的 Java 实现类；点击 Call Activity 打开对应子流程 `.bpmn`
- 📋 **属性可复制**：属性面板文本支持选中 + Ctrl+C 复制

### 编辑模式
- 🧲 **拖拽建模**：Palette 落子、节点拖拽移动、Shift+点击连线
- 📝 **属性双向编辑**：按节点类型动态生成表单（实现配置 / 用户任务 / 脚本 / 边界事件 / 多实例 / 定时器 / 消息·信号·错误）
- 🔗 **变量映射 & 监听器**：表格式增删改 Call Activity 变量映射、执行/任务监听器
- ↩️ **撤销 / 重做**：命令栈支持 50 步 undo/redo
- ✅ **保存前校验**：13 条建模规则校验，ERROR 阻止保存、WARNING 确认，问题可一键「定位」到画布节点
- 💾 **无损保存**：序列化为 Activiti 兼容的 BPMN 2.0 XML（含命名空间、扩展元素与 DI 坐标）
- 🆕 **快速新建**：File → New → **BPMN Process**，新建后自动进入编辑模式

---

## 📦 安装

### 方式一：从磁盘安装（推荐）

1. 下载或构建插件 ZIP 包（见下方[构建](#-构建)）
2. 打开 IntelliJ IDEA → **Settings / Preferences → Plugins**
3. 点击齿轮图标 → **Install Plugin from Disk...**
4. 选择 `build/distributions/bpmn-viewer-plugin-2.3.2.zip`
5. 重启 IDE

### 方式二：开发/沙箱模式

```bash
./gradlew runIde      # Linux / macOS
gradlew.bat runIde    # Windows
```

会启动一个预装本插件的沙箱 IDE 实例。

---

## 🔨 构建

**环境要求**

- JDK 17+
- IntelliJ IDEA 2024.3.5+（构建号 243 ~ 251）

**命令**

```bash
# 编译并打包插件 ZIP
gradlew.bat buildPlugin

# 运行单元测试
gradlew.bat test

# 启动沙箱 IDE（开发调试）
gradlew.bat runIde
```

产物位置：`build/distributions/bpmn-viewer-plugin-*.zip`

> Windows PowerShell 用户请使用 `gradlew.bat`，其余平台使用 `./gradlew`。

---

## 🚀 使用

1. 在项目中打开任意 `.bpmn` 文件，插件将自动以图形方式渲染流程图
2. **查看**：Ctrl+滚轮缩放、拖拽平移，点击节点/连线在右侧面板查看属性
3. **编辑**：点击工具栏「切换编辑」进入编辑模式，从左侧 Palette 拖放元素、编辑属性
4. **保存**：`Ctrl+S` 保存（自动执行校验并写回 BPMN XML）

### 快捷键

| 快捷键 | 功能 |
|--------|------|
| `Ctrl + S` | 保存（编辑模式） |
| `Ctrl + Z` | 撤销 |
| `Ctrl + Shift + Z` / `Ctrl + Y` | 重做 |
| `Delete` / `Backspace` | 删除选中的节点或连线 |
| `Shift + 点击节点` | 进入连线模式 |
| `Esc` | 取消连线模式 |
| `Ctrl + 滚轮` | 缩放画布（0.3x ~ 3.0x） |

---

## 🏗️ 架构概览

插件采用分层架构，编辑层以「垂直堆叠」方式叠加在只读查看层之上，两者互不侵入：

```
┌─────────────────────────────────────────────────┐
│  UI 层 (Swing)                                    │
│  查看：BpmnGraphPanel + BpmnDetailPanel           │
│  编辑：BpmnEditGraphPanel + Palette + PropertyPanel│
├─────────────────────────────────────────────────┤
│  布局层                                            │
│  ManhattanGridRouter（A* 正交路由，默认）          │
│  EdgeRouter（Z 型 fallback）/ SugiyamaLayout（兜底）│
├─────────────────────────────────────────────────┤
│  编辑层     EditableModel / EditCommand / Stack    │
├─────────────────────────────────────────────────┤
│  数据模型层  BpmnModel / BpmnNode / BpmnEdge       │
├─────────────────────────────────────────────────┤
│  解析 & 序列化  BpmnParser (DOM) / BpmnSerializer  │
├─────────────────────────────────────────────────┤
│  数据源     .bpmn 文件（BPMN 2.0 XML + Activiti 扩展）│
└─────────────────────────────────────────────────┘
```

**核心模块**

| 模块 | 职责 |
|------|------|
| `parser/` | namespace-aware DOM 解析：节点类型、扩展元素、DI 坐标 |
| `layout/` | Manhattan Grid A* 路由 + Sugiyama 兜底布局 |
| `ui/` | 查看/编辑双套 Swing 面板 + 分类型渲染器 |
| `edit/` | 可变模型 + 命令模式 undo/redo |
| `serializer/` | EditableModel → Activiti BPMN 2.0 XML |
| `validation/` | 保存前 13 条规则校验 |

> 完整设计文档见 [DESIGN.md](DESIGN.md)，版本变更见 [CHANGELOG.md](CHANGELOG.md)。

---

## 🧰 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Kotlin | 1.9.25 | 主编程语言 |
| Java | 17 | 运行时 |
| IntelliJ Platform Gradle Plugin | 2.2.1 | IDE 集成与构建 |
| Swing + Java2D | AWT 标准库 | 图形渲染（无第三方图形库） |
| JDK DOM Parser | 标准库 | BPMN XML 解析 |
| JUnit 5 | 5.10.2 | 单元测试 |

---

## ⚠️ 已知限制

- 编辑模式为扁平结构，暂不支持子流程嵌套下钻编辑
- POOL / LANE 仅查看渲染，编辑模式序列化时被跳过
- 单 `<process>` 支持（多 process 文件仅取第一个）
- 拖拽节点时连线释放后才重路由
- UI 文案目前为中文，暂无国际化

---

## 🗺️ 后续规划

- 子流程嵌套编辑（双击下钻 + 面包屑返回）
- 泳道 / 池编辑与序列化
- PNG / SVG 导出
- 按 ID / 名称 / 类名 搜索定位
- 多 process 支持与国际化

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request：

1. Fork 本仓库并创建特性分支
2. 提交改动前请运行 `gradlew.bat test` 确保测试通过
3. 保持提交信息清晰，必要时同步更新 [CHANGELOG.md](CHANGELOG.md) 与 [DESIGN.md](DESIGN.md)

---

## 📄 许可证

本项目基于 [MIT 许可证](LICENSE) 开源。
