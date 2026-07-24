# 开发变更日志

> 本文档汇总 BPMN Viewer & Editor 插件迄今为止的开发成果，作为后续开发者（或 AI 协作）快速理解项目状态的入口文档。

## 项目概述

- **项目定位**：面向 IntelliJ IDEA 的 Activiti 友好 BPMN 2.0 流程图查看 + 编辑插件。原本为只读查看器（v1.0.0），现已扩展出完整的图形化编辑能力（v2.0.0）。
- **目标用户**：使用 Activiti 5.x / 6.x 工作流引擎、需要在 IDEA 内直接打开/审阅/修改 `.bpmn` 文件的 Java 后端开发者。
- **技术栈**：
  - 语言：Kotlin 1.9.25 / Java 17
  - 平台：IntelliJ Platform Gradle Plugin 2.2.1，目标 IDE 版本 `2024.3.5`（构建号 `243` ~ `251`）
  - UI：Swing + Java2D（无第三方图形库）
  - 解析：JDK 内置 DOM Parser（namespace-aware）
- **主要入口**：[BpmnFileEditorProvider](src/main/kotlin/com/rickytech/bpmn/viewer/BpmnFileEditorProvider.kt) → [BpmnFileEditor](src/main/kotlin/com/rickytech/bpmn/viewer/BpmnFileEditor.kt)，注册见 [plugin.xml](src/main/resources/META-INF/plugin.xml)。
- **当前版本**：`gradle.properties` 与 [plugin.xml](src/main/resources/META-INF/plugin.xml) 已同步对齐为 `2.3.0`。

## 版本历史

### v2.3.2（属性面板文本可选中复制）

- **优化**
  - 查看模式属性面板文本支持选中复制：[BpmnDetailPanel](src/main/kotlin/com/rickytech/bpmn/viewer/ui/BpmnDetailPanel.kt) 中属性值由 `JLabel` 改为只读 `JTextPane`（`editable=false`），允许用户用鼠标选中并 Ctrl+C 复制属性值；字段名（key）与表头仍保持 `JLabel` 不变

### v2.3.0（Manhattan Grid Router）

- **Manhattan Grid Router**：基于网格离散化 + A* 寻路的正交路由算法，替代原规则式路由
  - 画布自动离散化为 10px 步长网格
  - 节点外扩 12px 禁区，路径保证不穿越任何节点
  - A* 带转弯惩罚（TURN_PENALTY=2），最小化折弯数
  - 起止方向强制垂直于节点边缘（90° 出入线）
  - 短边优先路由策略，密集布局效果显著提升
- **重构**
  - 新增 [layout/AnchorUtils.kt](src/main/kotlin/com/rickytech/bpmn/viewer/layout/AnchorUtils.kt)：锚点选择逻辑从 EdgeRouter 独立为共享工具模块（12 锚点模型、角度选边、冲突回退）
  - 新增 [layout/ManhattanGridRouter.kt](src/main/kotlin/com/rickytech/bpmn/viewer/layout/ManhattanGridRouter.kt)：网格路由主体（Grid 数据结构 / A* 搜索 / polyline 后处理）
  - [layout/EdgeRouter.kt](src/main/kotlin/com/rickytech/bpmn/viewer/layout/EdgeRouter.kt)：锚点逻辑委托 AnchorUtils，保留 Z 型路由作为 fallback 参考实现
  - [BpmnFileEditor](src/main/kotlin/com/rickytech/bpmn/viewer/BpmnFileEditor.kt) 与 [BpmnEditGraphPanel](src/main/kotlin/com/rickytech/bpmn/viewer/ui/BpmnEditGraphPanel.kt) 中路由器替换为 ManhattanGridRouter
- **新增**
  - 新建 BPMN 文件：File → New 菜单及项目树右键 New 菜单中新增 "BPMN Process" 选项
    - 输入文件名后自动创建含最小有效模板的 .bpmn 文件
    - 新建文件打开后自动进入编辑模式，可直接拖拽设计流程图

### v2.2.0（连线路由优化 + Palette 连线工具）

- **连线路由全面优化**
  - L 型路由升级为 Z 型（4 点路由），确保从锚点出发的第一段线垂直于节点边缘（至少 20px 延伸）
  - [RouteUtils.kt](src/main/kotlin/com/rickytech/bpmn/viewer/layout/RouteUtils.kt)：新增逐段迭代避让算法，替代原有单次整体绕行策略
  - 碰撞检测后对每个冲突段插入 U 型绕行（最多 30 次迭代），有效解决密集布局中线条穿越节点的问题
  - 路由结果自动精简共线冗余点

- **编辑模式连线修复**
  - 切换到编辑模式时自动调用 `initRoutes()` 重新计算所有边的路由点
  - 确保编辑模式下连线始终可见

- **Palette 新增连线工具**
  - 左侧工具栏新增"连接"分组，包含"顺序流"按钮（带箭头图标）
  - 选中后点击源节点→目标节点即可创建 SequenceFlow
  - 连线工具保持激活状态，支持连续画线
  - 与节点放置工具互斥，ESC 取消当前连线操作

### v2.1.1（代码质量提升）

- **PropertyPanel 模块化拆分**
  - 新增 [ui/property/FormBuilder.kt](src/main/kotlin/com/rickytech/bpmn/viewer/ui/property/FormBuilder.kt)：通用表单构建工具（颜色常量、section/field 构建、bind 方法）
  - 新增 [ui/property/VariableMappingEditor.kt](src/main/kotlin/com/rickytech/bpmn/viewer/ui/property/VariableMappingEditor.kt)：变量映射表格编辑器
  - 新增 [ui/property/ListenerEditor.kt](src/main/kotlin/com/rickytech/bpmn/viewer/ui/property/ListenerEditor.kt)：执行监听器/任务监听器编辑器
  - [BpmnPropertyPanel.kt](src/main/kotlin/com/rickytech/bpmn/viewer/ui/BpmnPropertyPanel.kt) 从 1102 行精简至 ~420 行，仅保留编排逻辑

- **Parser 模块化拆分**
  - 新增 [parser/ExtensionParser.kt](src/main/kotlin/com/rickytech/bpmn/viewer/parser/ExtensionParser.kt)：扩展元素解析（多实例/变量映射/监听器/DOM 工具）
  - 新增 [parser/NodeParser.kt](src/main/kotlin/com/rickytech/bpmn/viewer/parser/NodeParser.kt)：所有节点类型的解析方法
  - [BpmnParser.kt](src/main/kotlin/com/rickytech/bpmn/viewer/parser/BpmnParser.kt) 从 667 行精简至 ~240 行，保留入口/DI/协作解析

- **Validator 增强**
  - 新增 5 条校验规则（边界事件附属引用 / 多实例配置 / 定时器配置 / 不可达节点 / CallActivity 目标），总计 13 条
  - 校验弹窗支持「定位」按钮，点击后画布选中并居中显示问题节点
  - [BpmnEditGraphPanel](src/main/kotlin/com/rickytech/bpmn/viewer/ui/BpmnEditGraphPanel.kt) 新增 `selectAndScrollTo(nodeId)` 公开方法

- **单元测试**
  - [build.gradle.kts](build.gradle.kts) 新增 JUnit 5 依赖与配置
  - 新增 5 个测试文件共 ~940 行、40 个测试用例
  - 覆盖：BpmnParser / BpmnValidator / CommandStack / EditCommand / BpmnSerializer

- **版本号对齐**
  - [plugin.xml](src/main/resources/META-INF/plugin.xml) 和 [gradle.properties](gradle.properties) 统一为 `2.1.0`

- **DESIGN.md 10.1 节更新**
  - 移除过时的「只读模式」描述，替换为当前真实限制清单

- **Bug #30 彻底修复**
  - 抽取 `parseAndLayout()` 方法，保存后从文件重新解析而非依赖编辑模型快照

### v2.0.0（编辑器功能）

对应 commit：`c8103bf feat: add BPMN editor with full Activiti element support`

- **编辑模式核心架构**
  - [EditableModel](src/main/kotlin/com/rickytech/bpmn/viewer/edit/EditableModel.kt)：与 `BpmnModel` 镜像的可变模型，提供 `fromBpmnModel()` 进入和 `toSnapshot()` 出出两个方向的转换
  - [EditCommand](src/main/kotlin/com/rickytech/bpmn/viewer/edit/EditCommand.kt)：Sealed 命令模式，覆盖移动/增删节点、增删连线、改属性、变量映射、监听器
  - [CommandStack](src/main/kotlin/com/rickytech/bpmn/viewer/edit/CommandStack.kt)：50 步深度 undo/redo + savePoint 脏标识 + `onStateChanged` 回调
- **可编辑图形面板** [BpmnEditGraphPanel](src/main/kotlin/com/rickytech/bpmn/viewer/ui/BpmnEditGraphPanel.kt)：5 状态机（IDLE / NODE_SELECTED / DRAGGING_NODE / CONNECTING_FROM / PANNING），支持节点拖拽、Shift 连线、Delete 删除、空白 Palette 落子。
- **属性编辑面板** [BpmnPropertyPanel](src/main/kotlin/com/rickytech/bpmn/viewer/ui/BpmnPropertyPanel.kt)：按节点类型动态构建表单（基本信息/实现配置/脚本/边界/调用活动/变量映射/多实例/定时器/消息·信号·错误/执行监听器/任务监听器）。
- **Palette 元素工具栏** [BpmnPalettePanel](src/main/kotlin/com/rickytech/bpmn/viewer/ui/BpmnPalettePanel.kt)：14 种元素的"单击选中 → 画布点击放置"交互。
- **BPMN 序列化器** [BpmnSerializer](src/main/kotlin/com/rickytech/bpmn/viewer/serializer/BpmnSerializer.kt)：输出 Activiti 兼容 BPMN 2.0 XML，含命名空间、扩展元素、DI 坐标。
- **流程校验器** [BpmnValidator](src/main/kotlin/com/rickytech/bpmn/viewer/validation/BpmnValidator.kt)：8 条规则，ERROR 阻止保存、WARNING 弹窗确认。
- **编辑/查看模式切换**：由 [BpmnFileEditor](src/main/kotlin/com/rickytech/bpmn/viewer/BpmnFileEditor.kt) 统一管理 toggle、保存与脏状态，提供 Ctrl+S / Ctrl+Z / Ctrl+Y / Delete 等快捷键。

### v1.0.0（查看器功能）

对应 commits：`9ffd47f` / `47eeded feat: BPMN Viewer plugin v1.0.0`、`28cb839 fix: exclude build dirs from subprocess navigation & add BPMN file icon`

- **BPMN 文件解析** [BpmnParser](src/main/kotlin/com/rickytech/bpmn/viewer/parser/BpmnParser.kt)：namespace-aware DOM；支持 30+ 节点类型、Activiti 扩展、`<extensionElements>`、多实例、条件表达式以及 `<bpmndi:BPMNDiagram>` DI 坐标提取。
- **自动布局**
  - [SugiyamaLayout](src/main/kotlin/com/rickytech/bpmn/viewer/layout/SugiyamaLayout.kt)：BFS 分层 + 重心排序 + 坐标分配（无 DI 坐标时兜底）
  - [EdgeRouter](src/main/kotlin/com/rickytech/bpmn/viewer/layout/EdgeRouter.kt)：12 锚点正交连线路由 + 锚点冲突回退
- **流程图渲染** [BpmnGraphPanel](src/main/kotlin/com/rickytech/bpmn/viewer/ui/BpmnGraphPanel.kt) + [BpmnNodeRenderer](src/main/kotlin/com/rickytech/bpmn/viewer/ui/BpmnNodeRenderer.kt) + [BpmnEdgeRenderer](src/main/kotlin/com/rickytech/bpmn/viewer/ui/BpmnEdgeRenderer.kt)：bpmn.io 视觉风格、任务图标、网关 marker、事件双线圈、多实例标记，带 HiDPI 兼容的级联坐标变换。
- **详情面板** [BpmnDetailPanel](src/main/kotlin/com/rickytech/bpmn/viewer/ui/BpmnDetailPanel.kt)：节点/连线属性分组展示，含 Java 类导航（JavaPsiFacade）。
- **子流程跳转**：CallActivity 的 `calledElement` 链接化，遍历项目内 `.bpmn` 文件匹配 `<process id>` 后通过 `FileEditorManager` 打开。
- **文件类型注册** [BpmnFileType](src/main/kotlin/com/rickytech/bpmn/viewer/BpmnFileType.kt) + 自定义 SVG 图标 [icons/bpmn.svg](src/main/resources/icons/bpmn.svg)。

## 当前会话开发记录（v2.x 迭代）

以下变更尚未提交（见下文"未提交的变更文件"）。

### Bug 修复

1. **属性面板回调覆盖问题**：`commandStack.onStateChanged` 此前在多处被重复赋值，后赋值会覆盖前者，导致编辑后属性面板不刷新。统一收敛到 [BpmnFileEditor](src/main/kotlin/com/rickytech/bpmn/viewer/BpmnFileEditor.kt) 内一次性设置（`graph.refreshModel()` + `props.refreshFromModel()` + `updateButtons()`）。
2. **点击已有节点属性不显示**：选中回调原顺序 `show → clear` 会先填充表单又被立即清空，调整为 `clear → show`，确保属性正常呈现。
3. **保存后查看模式属性未同步（DESIGN.md Bug #30）**：`saveFile()` 完成后未刷新 `laidOut` 快照，回到查看模式仍显示旧值；现在保存后重建查看模式所用的不可变模型。

### 功能增强

1. **Palette 面板图标**：14 种元素新增 20×20 Java2D 迷你图标（事件/任务/调用活动/网关），与画布渲染风格一致。详见 [BpmnPalettePanel](src/main/kotlin/com/rickytech/bpmn/viewer/ui/BpmnPalettePanel.kt)。
2. **边界事件属性编辑**：`attachedToRef`、`cancelActivity` 字段贯通解析→编辑→序列化全链路，支持非中断（虚线圆）边界事件。
3. **`formKey` 扩展到更多任务类型**：除 USER_TASK 外，SERVICE_TASK / SEND_TASK / MAIL_TASK / BUSINESS_RULE_TASK 也支持 formKey 编辑。
4. **MANUAL_TASK 异步执行支持**：在属性面板新增 `异步` CheckBox，序列化为 `activiti:async`。
5. **SERVICE_TASK 脚本字段编辑**：原仅 SCRIPT_TASK 可编辑 `scriptFormat` / `scriptContent`，现 SERVICE_TASK 也开放（兼容 Activiti 中以脚本承载的服务任务实践）。
6. **Call Activity 变量映射编辑器**：表格式增删改 in/out 映射，每行有方向标签 + 源/目标文本框 + 删除按钮，全部经 `AddVariableMappingCommand` / `RemoveVariableMappingCommand` / `UpdateVariableMappingCommand` 走 undo/redo。
7. **执行监听器 / 任务监听器编辑器**：事件下拉（execution: start/end/take，task: create/assignment/complete/delete）+ 实现类型下拉（class/delegateExpression/expression）+ 实现值文本框 + 删除按钮；通过 `AddListenerCommand` / `RemoveListenerCommand` / `UpdateListenerCommand` 进入命令栈。
8. **多实例 `completionCondition` / `loopCondition` 全链路打通**：[BpmnParser](src/main/kotlin/com/rickytech/bpmn/viewer/parser/BpmnParser.kt) 解析 → [BpmnNode](src/main/kotlin/com/rickytech/bpmn/viewer/model/BpmnNode.kt) 字段 → [BpmnDetailPanel](src/main/kotlin/com/rickytech/bpmn/viewer/ui/BpmnDetailPanel.kt) 展示 → [EditableModel](src/main/kotlin/com/rickytech/bpmn/viewer/edit/EditableModel.kt) 编辑字段 → [BpmnPropertyPanel](src/main/kotlin/com/rickytech/bpmn/viewer/ui/BpmnPropertyPanel.kt) 多行文本框 → [BpmnSerializer](src/main/kotlin/com/rickytech/bpmn/viewer/serializer/BpmnSerializer.kt) 输出。`ChangePropertyCommand` 已将其纳入 27 个属性键列表。

### 文档更新

- [DESIGN.md](DESIGN.md) 新增第 12 章「编辑模式架构」（977 行），覆盖整体设计思路、模块职责、数据流、快捷键和 UI 布局，并标注 Bug #30。

## 当前代码结构

代码根：[src/main/kotlin/com/rickytech/bpmn/viewer](src/main/kotlin/com/rickytech/bpmn/viewer)

```
viewer/
├── BpmnFileEditor.kt              # 编辑器主体；编辑/查看模式切换、保存、快捷键、回调编排
├── BpmnFileEditorProvider.kt      # FileEditorProvider + DumbAware；HIDE_DEFAULT_EDITOR
├── BpmnFileType.kt                # .bpmn 文件类型 + 自定义图标
│
├── model/                         # 不可变数据模型（查看模式使用）
│   ├── BpmnNode.kt                # 节点 data class（30+ 类型 + Activiti 扩展字段）
│   ├── BpmnEdge.kt                # 连线 data class
│   └── BpmnModel.kt               # process + nodes + edges 聚合
│
├── parser/
│   ├── BpmnParser.kt              # 入口；DOM namespace-aware；DI 坐标 + 协作解析；委托 Node/Extension
│   ├── NodeParser.kt              # 各节点类型解析方法（30+ 类型）
│   └── ExtensionParser.kt         # 扩展元素解析（多实例 / 变量映射 / 监听器 / DOM 工具）
│
├── layout/
│   ├── SugiyamaLayout.kt          # 无 DI 坐标时的层次布局兜底
│   ├── EdgeRouter.kt              # 12 锚点正交连线路由（Z型出线保证90度角）
│   └── RouteUtils.kt              # 碰撞检测 + 逐段迭代避让算法
│
├── ui/
│   ├── BpmnGraphPanel.kt          # 查看模式渲染面板（缩放/平移/选中）
│   ├── BpmnNodeRenderer.kt        # 节点绘制入口（按类型分发到 renderer/）
│   ├── BpmnEdgeRenderer.kt        # 连线绘制（条件菱形、默认流斜杠、箭头）
│   ├── renderer/
│   │   ├── RenderConstants.kt     # 配色、描边等常量
│   │   ├── EventRenderer.kt       # 圆形事件（开始/结束/中间/边界）+ 8 种事件图标
│   │   ├── TaskRenderer.kt        # 矩形任务 + 任务类型图标 + 多实例标记
│   │   ├── GatewayRenderer.kt     # 菱形 + 4 种网关 marker
│   │   └── ContainerRenderer.kt   # 子流程 / 事件子流程 / Pool / Lane / TextAnnotation
│   ├── BpmnDetailPanel.kt         # 查看模式右侧属性详情 + Java 类 / 子流程跳转
│   ├── BpmnEditGraphPanel.kt      # 编辑模式画布（5 态状态机；拖拽/连线/删除/Palette 落子）
│   ├── BpmnPalettePanel.kt        # 编辑模式左侧元素工具栏（14 种节点 + 顺序流连线工具）
│   ├── BpmnPropertyPanel.kt       # 编辑模式右侧属性面板（编排各编辑器 + 类型分发）
│   └── property/
│       ├── FormBuilder.kt         # 通用表单构建（颜色常量 / section / field / bind 工具）
│       ├── VariableMappingEditor.kt # CallActivity 变量映射表格编辑器
│       └── ListenerEditor.kt      # 执行监听器 / 任务监听器表格编辑器
│
├── edit/                          # 编辑模式专用（v2.0.0 引入）
│   ├── EditableModel.kt           # 可变模型 EditableNode / EditableEdge / EditableModel
│   ├── EditCommand.kt             # 13 种命令 + ChangePropertyCommand 27 个属性键
│   └── CommandStack.kt            # undo/redo 栈 + savePoint + onStateChanged 回调
│
├── serializer/
│   └── BpmnSerializer.kt          # EditableModel → Activiti BPMN 2.0 XML（含 DI 输出）
│
└── validation/
    └── BpmnValidator.kt           # 保存前 13 条规则校验（ERROR 阻止 / WARNING 确认 + 定位）
```

资源文件：

- [plugin.xml](src/main/resources/META-INF/plugin.xml)：插件声明、扩展点注册（fileType + fileEditorProvider）
- [icons/bpmn.svg](src/main/resources/icons/bpmn.svg)：BPMN 文件类型图标

构建：[build.gradle.kts](build.gradle.kts) + [gradle.properties](gradle.properties) + [settings.gradle.kts](settings.gradle.kts)。

## v2.1.1 变更文件清单

本次重构涉及的新增 / 修改文件：

```
new file:   src/main/kotlin/com/rickytech/bpmn/viewer/parser/NodeParser.kt
new file:   src/main/kotlin/com/rickytech/bpmn/viewer/parser/ExtensionParser.kt
new file:   src/main/kotlin/com/rickytech/bpmn/viewer/ui/property/FormBuilder.kt
new file:   src/main/kotlin/com/rickytech/bpmn/viewer/ui/property/VariableMappingEditor.kt
new file:   src/main/kotlin/com/rickytech/bpmn/viewer/ui/property/ListenerEditor.kt
new file:   src/test/kotlin/com/rickytech/bpmn/viewer/parser/BpmnParserTest.kt
new file:   src/test/kotlin/com/rickytech/bpmn/viewer/validation/BpmnValidatorTest.kt
new file:   src/test/kotlin/com/rickytech/bpmn/viewer/edit/CommandStackTest.kt
new file:   src/test/kotlin/com/rickytech/bpmn/viewer/edit/EditCommandTest.kt
new file:   src/test/kotlin/com/rickytech/bpmn/viewer/serializer/BpmnSerializerTest.kt
modified:   src/main/kotlin/com/rickytech/bpmn/viewer/parser/BpmnParser.kt           # 委托 Node/Extension Parser，精简至 ~240 行
modified:   src/main/kotlin/com/rickytech/bpmn/viewer/ui/BpmnPropertyPanel.kt       # 委托 property/* 编辑器，精简至 ~420 行
modified:   src/main/kotlin/com/rickytech/bpmn/viewer/ui/BpmnEditGraphPanel.kt      # 新增 selectAndScrollTo(nodeId)
modified:   src/main/kotlin/com/rickytech/bpmn/viewer/validation/BpmnValidator.kt   # 新增 5 条规则
modified:   src/main/kotlin/com/rickytech/bpmn/viewer/BpmnFileEditor.kt             # parseAndLayout() / 校验弹窗带定位
modified:   build.gradle.kts                                                     # JUnit 5 依赖
modified:   gradle.properties                                                    # pluginVersion=2.1.0
modified:   src/main/resources/META-INF/plugin.xml                               # version 2.1.0
modified:   DESIGN.md                                                            # 第 3/5.1/10.1 节更新
modified:   CHANGELOG.md
```

各文件本次会话的主要修改方向（与上文「当前会话开发记录」对应）：

| 文件 | 修改要点 |
|------|----------|
| `DESIGN.md` | 新增第 12 章「编辑模式架构」 |
| `BpmnFileEditor.kt` | 统一 `onStateChanged` 回调；保存后刷新 `laidOut` 修复 Bug #30 |
| `edit/EditCommand.kt` | 新增 `completionCondition` / `loopCondition` 等属性键；监听器/变量映射命令族 |
| `edit/EditableModel.kt` | 新增 `attachedToRef` / `cancelActivity` / 监听器 / 多实例条件等字段 |
| `model/BpmnNode.kt` | 新增多实例 `completionCondition` / `loopCondition` 字段 |
| `parser/BpmnParser.kt` | 解析多实例条件、边界事件 `cancelActivity` 等扩展属性 |
| `serializer/BpmnSerializer.kt` | 输出多实例条件、边界事件、监听器 / 变量映射扩展元素 |
| `ui/BpmnDetailPanel.kt` | 查看模式展示新增字段 |
| `ui/BpmnEditGraphPanel.kt` | 与 Palette 联动落子；选中→属性回调顺序修复 |
| `ui/BpmnPalettePanel.kt` | 14 种元素迷你图标 + 单击选择交互 |
| `ui/BpmnPropertyPanel.kt` | 按类型动态构建表单；变量映射、监听器表格编辑器；formKey 扩展；多实例条件多行文本框 |

> v2.1.1 重构已将文档提交建议合并为单个 refactor commit。

## 后续开发建议

### 已修复（v2.1.1）

- ~~plugin.xml 版本号不一致~~：已对齐为 `2.1.0`。
- ~~DESIGN.md 第 10.1 节「已知限制」过时~~：已重写为 v2.1.0 真实限制清单。
- ~~保存后查看模式同步（Bug #30）~~：已抽取 `parseAndLayout()`，保存后从 `virtualFile` 重新走 `BpmnParser`。

### 待优化

- **拖拽节点视觉跟随**：当前实现是鼠标释放后才提交位置；`DRAGGING_NODE` 状态有 30ms 节流重绘，但拖拽过程中连线不重新路由，看起来像"瞬移"。建议拖拽中临时调用 `EdgeRouter` 仅对相关边做轻量重路由。
- **查看/编辑模式切换性能**：目前两套 UI 完全独立构建、不复用组件实例（DESIGN.md 12.1）；大流程切换有可见卡顿，可考虑组件复用或异步重建。
- **属性面板 UX 改进**：所有分组当前是平铺 BoxLayout，节点字段多时面板很长。建议用可折叠分组（`com.intellij.ui.CollapsiblePanel` 或自绘三角箭头标题栏）；定时器 / 监听器表格可改为 `JBTable`。
- ~~校验提示定位~~：已在 v2.1.1 中完成（弹窗「定位」按钮 → 画布选中并居中节点）。
- **撤销栈深 50** 的限制目前是硬编码（`CommandStack.maxSize`），可挪到设置项。

### 功能扩展方向

- **子流程嵌套编辑**：当前 `SUB_PROCESS` / `EVENT_SUB_PROCESS` 仅作为容器渲染，内部节点没有进入式编辑视图。可考虑双击下钻 + 面包屑返回。
- **泳道 / 池编辑**：`POOL` / `LANE` 在序列化时被跳过（见 [BpmnSerializer](src/main/kotlin/com/rickytech/bpmn/viewer/serializer/BpmnSerializer.kt) 的"跳过"分支），需要新增 `<participant>` / `<laneSet>` 输出与对应 Palette 元素、节点归属管理。
- **导入/导出其他格式**：PNG / SVG 导出（DESIGN.md v1.1 规划）、bpmn.io JSON 互转、Activiti Designer `.activiti` 文件兼容。
- **流程模拟 / 调试可视化**：结合 Activiti 运行时数据高亮当前活动节点、走过的路径，配合断点/单步调试。
- **搜索与导航**（DESIGN.md v2.0 规划）：按 ID / 名称 / 类名 在画布与详情面板中搜索定位。
- **多 process 支持**：当前 `EditableModel` 是单 `processId` 扁平结构，`<definitions>` 内多个 `<process>` 仅取第一个，可扩展为多 tab 或 process 切换器。
- **国际化**：UI 文案目前硬编码中文，可抽 `BpmnBundle.properties` 兼容英文用户。

---

> 文档生成于 v2.1.1 重构后，下次大版本发布前建议同步更新本 CHANGELOG 与 [DESIGN.md](DESIGN.md)。
