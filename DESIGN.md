# BPMN Viewer IntelliJ 插件 - 设计方案

## 1. 项目概述

### 1.1 插件用途

BPMN Viewer & Editor 是针对 IntelliJ IDEA 的 BPMN 流程图可视化与编辑插件，专为 Activiti 工作流引擎设计。能解析 BPMN 2.0 XML 并渲染高保真流程图，提供查看模式与编辑模式两种交互体验。

### 1.2 目标平台

- **IDE**: IntelliJ IDEA Community / Ultimate (版本 243 ~ 251)
- **语言**: Kotlin 1.9.25
- **JDK**: Java 17
- **构建**: IntelliJ Platform Gradle Plugin 2.2.1

### 1.3 核心功能

- 解析 Activiti BPMN 2.0 XML（30+ 节点类型、多种连线、扩展元素）
- 自动布局：优先 BPMN DI 原始坐标，无坐标回退 Sugiyama 算法
- 高保真渲染：bpmn.io 风格，支持缩放/平移
- 交互查看：节点/连线点击，右侧面板显示属性
- Java 类导航：直接跳转到 IDE 类定义、子流程 `.bpmn` 跳转
- 编辑模式（v2.0+）：Palette 落子 / 节点拖拽 / Shift 连线 / 属性表单 / undo · redo / 保存前校验

---

## 2. 技术栈

### 2.1 核心技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Kotlin | 1.9.25 | 主编程语言 |
| Java DOM Parser | 标准库 | XML 解析 |
| Swing + Java2D | AWT 标准 | 图形渲染 |
| IntelliJ Platform SDK | 2024.3.5 | IDE 集成 |

### 2.2 依赖声明

```kotlin
// build.gradle.kts
plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3.5")
        bundledPlugin("com.intellij.java")  // Java PSI 导航
    }
}
```

---

## 3. 项目结构

```
bpmn-viewer-plugin/
├── build.gradle.kts                    # Gradle 构建配置
├── gradle.properties                   # 版本号、JDK路径等属性
├── settings.gradle.kts                 # 项目设置 + pluginManagement
├── gradlew.bat / gradlew              # Gradle Wrapper
├── DESIGN.md                          # 本设计文档
└── src/main/
    ├── kotlin/com/rickytech/bpmn/viewer/
    │   ├── model/
    │   │   ├── BpmnNode.kt            # 节点数据模型 (8种类型)
    │   │   ├── BpmnEdge.kt            # 连线数据模型
    │   │   └── BpmnModel.kt           # 流程模型聚合
    │   ├── parser/
    │   │   ├── BpmnParser.kt          # 入口；DOM namespace-aware；DI 坐标 + 协作解析
    │   │   ├── NodeParser.kt          # 各节点类型解析方法（30+ 类型）
    │   │   └── ExtensionParser.kt     # 扩展元素解析（多实例 / 变量映射 / 监听器 / DOM 工具）
    │   ├── layout/
    │   │   ├── AnchorUtils.kt        # 12 锚点选择共享工具（角度选边 / 冲突回退）
    │   │   ├── ManhattanGridRouter.kt # 网格 + A* 正交路由（当前默认）
    │   │   ├── EdgeRouter.kt          # 旧规则式 Z 型路由（fallback 参考实现）
    │   │   ├── RouteUtils.kt          # 碊撞检测 + 逐段迭代避让
    │   │   └── SugiyamaLayout.kt      # Sugiyama 层次布局(兜底)
    │   ├── ui/
    │   │   ├── BpmnGraphPanel.kt      # 查看模式渲染面板
    │   │   ├── BpmnDetailPanel.kt     # 查看模式属性详情面板 + Java 导航
    │   │   ├── BpmnEditGraphPanel.kt  # 编辑模式画布（5 态状态机；含 selectAndScrollTo）
    │   │   ├── BpmnPalettePanel.kt    # 编辑模式元素工具栏（14 种 + 迷你图标）
    │   │   ├── BpmnPropertyPanel.kt   # 编辑模式属性面板（编排各编辑器 + 类型分发）
    │   │   ├── property/
    │   │   │   ├── FormBuilder.kt           # 通用表单构建（颜色/section/field/bind）
    │   │   │   ├── VariableMappingEditor.kt # 变量映射表格编辑器
    │   │   │   └── ListenerEditor.kt        # 执行/任务监听器编辑器
    │   │   └── renderer/              # 节点分类型渲染（事件/任务/网关/容器）
    │   ├── edit/
    │   │   ├── EditableModel.kt       # 可变模型（fromBpmnModel / toSnapshot）
    │   │   ├── EditCommand.kt         # 命令模式 + ChangePropertyCommand 27 个属性键
    │   │   └── CommandStack.kt        # undo/redo + savePoint + onStateChanged
    │   ├── serializer/
    │   │   └── BpmnSerializer.kt      # EditableModel → Activiti BPMN 2.0 XML
    │   ├── validation/
    │   │   └── BpmnValidator.kt       # 保存前 13 条规则校验（ERROR/WARNING + 定位）
    │   ├── BpmnFileEditor.kt          # 编辑器主体 (parseAndLayout / 模式切换 / 校验弹窗带定位 / 空流程自动进入编辑模式)
    │   ├── BpmnFileEditorProvider.kt  # IDE 编辑器提供者 + DumbAware
    │   ├── BpmnFileType.kt            # .bpmn 文件类型注册
    │   └── NewBpmnFileAction.kt       # New File 菜单 action（创建 .bpmn 文件并打开）
    └── resources/META-INF/
        └── plugin.xml                 # 插件声明 (扩展点注册)
```

---

## 4. 架构设计

### 4.1 分层架构

```
┌─────────────────────────────────────────────────┐
│              UI 层 (Swing)                        │
│  ┌─────────────────────┐  ┌──────────────────┐  │
│  │  BpmnGraphPanel     │  │  BpmnDetailPanel  │  │
│  │  (渲染 + 交互)       │  │  (属性 + 导航)    │  │
│  └─────────────────────┘  └──────────────────┘  │
├─────────────────────────────────────────────────┤
│              布局层                               │
│  ┌─────────────────────┐  ┌──────────────────┐  │
│  │  EdgeRouter         │  │  SugiyamaLayout  │  │
│  │  (12锚点路由)        │  │  (层次布局兜底)    │  │
│  └─────────────────────┘  └──────────────────┘  │
├─────────────────────────────────────────────────┤
│              数据模型层                            │
│  BpmnModel / BpmnNode / BpmnEdge                │
├─────────────────────────────────────────────────┤
│              解析层                               │
│  BpmnParser (DOM, namespace-aware)              │
├─────────────────────────────────────────────────┤
│              数据源                               │
│  .bpmn 文件 (BPMN 2.0 XML + Activiti扩展)       │
└─────────────────────────────────────────────────┘
```

### 4.2 数据流

```
BPMN XML 文件
    │
    ▼ BpmnParser.parse()
BpmnModel (nodes + edges, 原始坐标已应用)
    │
    ├── hasOriginalCoords = true ──▶ EdgeRouter.route() ──▶ 仅计算连线路由点
    │
    └── hasOriginalCoords = false ──▶ SugiyamaLayout.layout() ──▶ 计算全部坐标
    │
    ▼
BpmnModel (最终：节点坐标 + 边路由点)
    │
    ▼ BpmnGraphPanel.paintComponent()
屏幕渲染 (Java2D Graphics2D)
```

---

## 5. 核心模块设计

### 5.1 BPMN 解析器 (BpmnParser)

**模块化拆分（v2.1.1）：**

为控制单文件大小、提升可测性，解析逻辑拆为三个协作类：

| 类 | 职责 |
|------|------|
| `BpmnParser` | 入口类：DOM 加载、顶层遍历、`<process>` / `<collaboration>` / `<bpmndi:BPMNDiagram>` DI 坐标提取，然后委托下两者 |
| `NodeParser` | 按 BPMN 元素名路由到各类型的解析方法（startEvent / serviceTask / userTask / callActivity / boundaryEvent…），返回 `BpmnNode` |
| `ExtensionParser` | 解析 `<extensionElements>` 中的 `<activiti:in>` / `<activiti:out>` / `<activiti:executionListener>` / `<activiti:taskListener>`、`<multiInstanceLoopCharacteristics>` 及 DOM 通用工具（子元素查找 / 文本读取 / namespace-aware lookup） |

**支持的节点类型 (8种):**

| 类型 | XML元素 | 特有属性 |
|------|---------|---------|
| START_EVENT | `<startEvent>` | - |
| END_EVENT | `<endEvent>` | - |
| SERVICE_TASK | `<serviceTask>` | activiti:class, delegateExpression |
| USER_TASK | `<userTask>` | assignee, candidateUsers/Groups |
| CALL_ACTIVITY | `<callActivity>` | calledElement |
| MANUAL_TASK | `<manualTask>` | - |
| EXCLUSIVE_GATEWAY | `<exclusiveGateway>` | - |
| PARALLEL_GATEWAY | `<parallelGateway>` | - |

**属性解析:**
- Activiti 命名空间 (`http://activiti.org/bpmn`) 属性
- `<extensionElements>` 中的 `<activiti:in>` / `<activiti:out>` 变量映射
- `<multiInstanceLoopCharacteristics>` 多实例配置
- `<conditionExpression>` 条件表达式

**DI 坐标提取:**
- 从 `<bpmndi:BPMNDiagram>` → `<bpmndi:BPMNPlane>` → `<bpmndi:BPMNShape>` → `<omgdc:Bounds>`
- 通过 `bpmnElement` 属性与节点 ID 关联
- namespace-aware DOM 解析确保命名空间正确匹配

### 5.2 布局引擎

#### ManhattanGridRouter（v2.3.0，当前默认路由器）

**算法架构：Grid 构建 → A* 寻路 → 路径后处理**

```
EditableModel / BpmnModel
     │
     ▼ Grid 构建
画布边界 → 按 GRID_STEP 离散化为二维布尔矩阵
节点边界 → 外扩 NODE_PADDING 标记为禁区格
     │
     ▼ A* 寻路（面向起止锁定方向）
从起点邻格出发，启动方向与源节点边缘垂直
代价 = 距离 + TURN_PENALTY × 转弯次数
启发式：到终点邻格的曼哈顿距离
     │
     ▼ 路径后处理
合并共线路由点 → 准入路由点 → polyline
```

**模块分层：**

| 文件 | 职责 |
|------|------|
| `layout/AnchorUtils.kt` | 12 锚点选择共享工具：`AnchorPoint` 枚举、`anchorXY()` 绝对坐标、`selectSide()` 角度选边、`sideSubAnchor()` 侧面子位置、`pickFreeAnchor()` 冲突回退 |
| `layout/ManhattanGridRouter.kt` | 网格路由主体：Grid 数据结构、A* 优先队列、起止方向推断、polyline 后处理；对带节点的边集调用 `route(model)` 刷新所有 `routePoints` |
| `layout/EdgeRouter.kt` | 旧规则式 Z/U/L 路由，委托 `AnchorUtils` 完成锚点选择，作为 fallback 参考实现保留 |
| `layout/RouteUtils.kt` | 碊撞检测 + U 型逐段避让，仅 EdgeRouter fallback 路径使用 |

**关键参数：**

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `GRID_STEP` | 10 | 网格离散化步长（px），越小路径越精细但搜索空间指数增长 |
| `NODE_PADDING` | 12 | 节点外扩禁区半径（px），保证连线不贴着节点边缘 |
| `TURN_PENALTY` | 2 | 每次折弯的额外代价（以格为单位），驱动路径偏好直线 |
| `CANVAS_MARGIN` | 50 | 画布边缘外拓安全区，避免紧贴边界路由 |
| `MAX_EXPANSIONS` | 50000 | A* 节点扩展上限，防御性能退化，超限后返回简单连线 |

**起止方向策略：**
- 调用 `AnchorUtils.selectSide()` / `sideSubAnchor()` 为边选定源/目标锚点
- A* 开始节点仅允许与源节点边缘垂直的方向进入（如 RIGHT 侧锻定 `dx>0`）
- 终点进入以同样方式锻定方向，从而保证连线首尾段 90° 出入节点

**路径后处理：**
- A* 返回逐格路径 → 按转弯点压缩为 polyline（去除同一方向的中间点）
- 准入起点/终点的节点边缘交点，该点作为 `routePoints` 的首/尾点

**性能：**
- 典型 BPMN 图（30～80 节点）全部边路由耗时 < 50ms
- A* 扩展上限 50000 作为安全阈值，理论上 4000×3000 画布不会触发

**调用点：**
- [BpmnFileEditor](src/main/kotlin/com/rickytech/bpmn/viewer/BpmnFileEditor.kt)：DI 坐标阶段后 → `ManhattanGridRouter().route(model)`
- [BpmnEditGraphPanel](src/main/kotlin/com/rickytech/bpmn/viewer/ui/BpmnEditGraphPanel.kt)：节点拖拽释放/连线创建后 `rerouteEdges()` 中使用

#### EdgeRouter (12锚点正交路由、fallback)

**锚点体系 - 每边3个共12个:**

```
          TOP_LEFT   TOP_MID   TOP_RIGHT
            │          │          │
   LEFT_TOP─┼──────────┼──────────┼─RIGHT_TOP
            │                     │
   LEFT_MID─┤       [NODE]       ├─RIGHT_MID
            │                     │
LEFT_BOTTOM─┼──────────┼──────────┼─RIGHT_BOTTOM
            │          │          │
       BOTTOM_LEFT BOTTOM_MID BOTTOM_RIGHT
```

**选择策略:**
1. `selectSide()`: 基于 atan2 角度选择侧面 (4象限90度划分)
2. `sideSubAnchor()`: 在侧面内按垂直/水平偏移比(阈值0.25)选子位置
3. `pickFreeAnchor()`: 冲突时回退到同侧相邻锚点

**路由类型:**
- 对立路由（RIGHT→LEFT, TOP→BOTTOM 等）：Z 型 4 点路由，中间段取两节点间隙中点
- 同侧路由（RIGHT→RIGHT 等）：U 型绕行
- L 型路由（RIGHT→TOP 等）：升级为 Z 型 4 点路由，确保出入方向垂直于节点边缘且延伸至少 MIN_BEND_GAP (20px)

**障碍物避让 (RouteUtils.kt):**
- 逐段迭代策略：遍历路由每段线段，发现碰撞后对该段插入 U 型局部绕行
- 水平段碰撞：绕上方/下方两个候选，选无碰撞或碰撞最少者
- 垂直段碰撞：绕左侧/右侧两个候选
- 最多 30 次迭代，收敛后输出精简路由（去除共线冗余点）
- OBSTACLE_PADDING = 15px 安全间距

#### SugiyamaLayout (兜底方案)

- BFS 分层
- 重心排序 (12轮上下扫描)
- 坐标分配 + 边路由
- 仅在 BPMN 文件无 DI 坐标时启用

### 5.3 渲染面板 (BpmnGraphPanel)

**bpmn.io 风格渲染:**
- 白色圆角矩形节点，slate-gray 描边
- 开始/结束事件：圆形
- 网关：菱形
- 连线：正交折线 + 箭头

**Task 12 渲染增强 (30+ 节点类型):**
- **任务图标**：在任务节点左上角 16×16 区域绘制类型图标
  - SERVICE(齿轮) / USER(头像) / MANUAL(手掌) / SCRIPT(纸张+斜线) / BUSINESS_RULE(2×3 表格)
  - SEND(实心信封) / RECEIVE(空心信封) / MAIL(同 SEND)
  - CALL_ACTIVITY 不画图标，只用粗边框 (3.0f) 区分
- **多实例标记**：drawTask 末尾根据 `n.isSequential` 在底部居中绘制
  - true → 三条竖线 ‖‖‖ (sequential)
  - false → 三条横线 ≡ (parallel)
- **网关标记** (drawGateway)：菱形中心绘制 4 种 marker
  - X (exclusive) / + (parallel) / ○ (inclusive 空心圆) / ⬠ (event-based 五边形)
- **事件渲染** (drawEventWithIcon)：圆形粗细 + 中央图标
  - 开始事件：细线圆 (1.5f) + 图标(空心)
  - 结束事件：粗线圆 (3.0f) + 图标(实心)
  - 中间事件：双线圆，throwing 图标实心 / catching 图标空心
  - 边界事件：双线圆；`cancelActivity=false` 外圈改为虚线（非中断）
  - 图标：TIMER(时钟) / MESSAGE(信封) / SIGNAL(三角) / ERROR(闪电) / TERMINATE(实心圆) / CANCEL(X) / COMPENSATE(双左箭头)
- **结构容器**：
  - SUB_PROCESS：圆角矩形 + 顶部标题 + 底部居中 `+` 展开标记
  - EVENT_SUB_PROCESS：同上，但边框改为虚线
  - POOL：大矩形 + 左侧 30px 名称栏（竖排文字）
  - LANE：水平/竖直分割线 + 侧边名称（不画名称分隔栏）
- **TEXT_ANNOTATION**：左侧开口方括号 `[` + 文字（灰色细线）

**坐标变换 (HiDPI 兼容):**
```kotlin
// 级联方式保留系统 HiDPI 缩放
g2.translate(translateX, translateY)
g2.scale(scale, scale)
// 禁止使用 g2.transform = tx (会覆盖系统缩放)
```

**点击检测:**
- `screenToModel()`: 使用 AffineTransform.createInverse() 逆变换
- `nodeAt()`: 检查鼠标模型坐标是否在节点边界内
- `edgeAt()`: 点到折线段距离 < 阈值(5px)

### 5.4 详情面板 (BpmnDetailPanel)

**节点属性分组展示:**
- 基本信息：ID、名称、类型
- 执行配置：Java类、委托表达式、被调用流程
- 用户任务：处理人、候选人/组
- 多实例：是否顺序、循环基数、数据输入
- 变量映射：表格展示 (方向/源/目标)

**文本可选中复制（v2.3.2）:**
- 查看模式属性面板中的属性值（value）使用只读 `JTextPane`（`editable=false` + `opaque=false`）替代 `JLabel`，支持光标选中文本与 Ctrl+C 复制
- 字段名（key）以及变量映射表头仍保持 `JLabel`，保持视觉对齐
- `JTextPane` 关闭背景填充，外观与原 `JLabel` 一致，不影响布局

**Java 类导航:**
- 使用 `JavaPsiFacade` 在全局搜索范围查找类
- 找到后通过 `navigate(true)` 跳转到源码

**子流程跳转 (CallActivity):**
- callActivity 节点的 `calledElement` 属性显示为可点击链接
- 点击时在项目范围内搜索 `.bpmn` 文件，查找 `<process id="{calledElement}">` 匹配的文件
- 找到后通过 `FileEditorManager.openFile()` 在编辑器中打开该 BPMN 文件
- 搜索策略：遍历项目中所有 `.bpmn` 文件，读取 XML 提取 process id 进行匹配
- 未找到时显示提示信息

### 5.5 编辑器集成

- `BpmnFileEditorProvider`: 实现 `FileEditorProvider` + `DumbAware`
- `HIDE_DEFAULT_EDITOR`: 隐藏文本编辑器，只显示图形视图
- `JSplitPane`: 左图形 + 右详情，比例 0.7

---

## 6. 交互设计

### 6.1 用户操作

| 操作 | 效果 |
|------|------|
| 点击节点 | 右侧面板显示节点属性 |
| 点击连线 | 右侧面板显示连线属性(含条件表达式) |
| 点击空白 | 清空详情面板 |
| Ctrl + 滚轮 | 缩放 (0.3x ~ 3.0x) |
| 拖拽 | 平移画布 |
| 悬停节点 | 显示 tooltip |
| 点击 Java 类链接 | 跳转到 IDE 中的类定义 |
| 点击子流程链接 | 打开对应的 BPMN 流程文件 |

### 6.2 视觉反馈

- **节点选中**: 蓝色光晕 + 加粗边框
- **连线选中**: 蓝色加粗描边
- **悬停**: 指针变为手型

### 6.3 事件处理

```
mouseReleased
    │
    ├── 拖拽距离 > 5px → 忽略(是平移操作)
    │
    ├── nodeAt(point) 命中 → onNodeSelected(node) [仅触发节点回调]
    │
    ├── edgeAt(point) 命中 → onEdgeSelected(edge) [仅触发连线回调]
    │
    └── 未命中 → onNodeSelected(null) + onEdgeSelected(null) [清空]
```

---

## 7. 连线路由算法

### 7.1 12锚点系统

每个节点有12个离散锚点：

| 侧面 | 锚点 | 坐标 |
|------|------|------|
| LEFT | LEFT_TOP | (x, y + h*0.25) |
| LEFT | LEFT_MID | (x, y + h*0.50) |
| LEFT | LEFT_BOTTOM | (x, y + h*0.75) |
| RIGHT | RIGHT_TOP | (x+w, y + h*0.25) |
| RIGHT | RIGHT_MID | (x+w, y + h*0.50) |
| RIGHT | RIGHT_BOTTOM | (x+w, y + h*0.75) |
| TOP | TOP_LEFT | (x + w*0.25, y) |
| TOP | TOP_MID | (x + w*0.50, y) |
| TOP | TOP_RIGHT | (x + w*0.75, y) |
| BOTTOM | BOTTOM_LEFT | (x + w*0.25, y+h) |
| BOTTOM | BOTTOM_MID | (x + w*0.50, y+h) |
| BOTTOM | BOTTOM_RIGHT | (x + w*0.75, y+h) |

### 7.2 角度选择策略

```
基于 atan2(dy, dx) 将 360度 分为4象限:
  RIGHT  : -45度 ~ +45度
  BOTTOM : +45度 ~ +135度
  TOP    : -135度 ~ -45度
  LEFT   : 其余 (绕 ±180度)
```

侧面内子位置选择：
- LEFT/RIGHT 侧：根据 dy/height 比值 → TOP(< -0.25) / MID / BOTTOM(> 0.25)
- TOP/BOTTOM 侧：根据 dx/width 比值 → LEFT(< -0.25) / MID / RIGHT(> 0.25)

### 7.3 锚点冲突回退

当首选锚点被占用时，依次尝试同侧相邻锚点：
- LEFT_TOP → LEFT_MID → LEFT_BOTTOM
- RIGHT_MID → RIGHT_TOP → RIGHT_BOTTOM
- 等...

### 7.4 正交折线路由 (16种组合)

| 源侧 → 目标侧 | 路由策略 |
|---------------|---------|
| RIGHT → LEFT | 水平中线折线 (Z形) |
| LEFT → RIGHT | 水平中线折线 (反Z形) |
| BOTTOM → TOP | 垂直中线折线 |
| TOP → BOTTOM | 垂直中线折线 |
| RIGHT → RIGHT | U形右绕 |
| LEFT → LEFT | U形左绕 |
| BOTTOM → BOTTOM | U形下绕 |
| TOP → TOP | U形上绕 |
| RIGHT → TOP/BOTTOM | L形转角 |
| LEFT → TOP/BOTTOM | L形转角 |
| BOTTOM → LEFT/RIGHT | L形转角 |
| TOP → LEFT/RIGHT | L形转角 |

---

## 8. 坐标系统

### 8.1 坐标优先级

```
有 BPMNDiagram DI 坐标？
    │
    ├── 是 → 使用原始坐标 + EdgeRouter 路由连线
    │        (节点位置严格保留，仅计算连线路径)
    │
    └── 否 → SugiyamaLayout 计算全部坐标
             (节点位置 + 连线路径全部自动计算)
```

### 8.2 DI 坐标解析

从 BPMN XML 的 `<bpmndi:BPMNDiagram>` 节提取：
```xml
<bpmndi:BPMNShape bpmnElement="servicetask1">
    <omgdc:Bounds height="55" width="105" x="100" y="30"/>
</bpmndi:BPMNShape>
```
→ 设置 node.x=100, node.y=30, node.width=105, node.height=55

### 8.3 HiDPI 兼容

**问题**: `g2.transform = AffineTransform(...)` 会覆盖系统 HiDPI 缩放矩阵，导致点击坐标偏移。

**解决方案**: 使用级联变换：
```kotlin
g2.translate(translateX, translateY)  // 平移
g2.scale(scale, scale)                // 缩放 (追加到系统变换之上)
```

**逆变换** (屏幕→模型坐标):
```kotlin
fun screenToModel(screenX: Int, screenY: Int): Point2D {
    val tx = AffineTransform()
    tx.translate(translateX, translateY)
    tx.scale(scale, scale)
    return tx.createInverse().transform(Point2D.Double(screenX.toDouble(), screenY.toDouble()), null)
}
```

---

## 9. 构建与安装

### 9.1 构建命令

```bash
# 编译并打包插件 ZIP
./gradlew.bat buildPlugin

# 开发模式启动 (新IDE实例)
./gradlew.bat runIde
```

产物位置: `build/distributions/bpmn-viewer-plugin-*.zip`

### 9.2 安装方式

1. **ZIP安装**: Settings → Plugins → Install Plugin from Disk → 选择 ZIP
2. **开发模式**: `./gradlew.bat runIde` 启动沙箱 IDE

### 9.3 环境要求

- JDK 17+ (gradle.properties 中配置 `org.gradle.java.home`)
- IntelliJ IDEA 2024.3.5+ (版本 243+)

---

## 10. 已知限制与后续规划

### 10.1 当前限制

- 编辑模式目前为扁平结构，不支持子流程嵌套编辑（双击下钻）
- POOL / LANE 类型仅查看渲染，编辑模式不支持泳道编排；序列化时被跳过
- 拖拽节点时连线不实时重路由（释放后才重算）
- 单 process 支持（多 process 文件仅取第一个）
- 撤销栈深度硬编码 50 步
- UI 文案硬编码中文，无国际化
- 12 锚点系统，极端密集连线场景仍可能有少量重叠
- 无 DI 坐标时 Sugiyama 布局为启发式，可能非最优
- 子流程跳转依赖项目内存在对应 .bpmn 文件
- 校验器 13 条规则覆盖常见建模错误，但未覆盖 Activiti 全部运行时约束

### 10.2 后续规划

**v1.1:**
- 支持更多节点类型 (subprocess, timer, message event)
- PNG/SVG 导出
- 全屏模式

**v2.0:**
- 搜索功能 (按名称/ID/类名)
- 流程执行追踪可视化
- 多 process 支持

---

## 11. 数据模型

### BpmnNode

```kotlin
data class BpmnNode(
    val id: String,
    val name: String,
    val type: BpmnNodeType,
    val javaClass: String? = null,
    val delegateExpression: String? = null,
    val calledElement: String? = null,
    val assignee: String? = null,
    val candidateUsers: String? = null,
    val candidateGroups: String? = null,
    val isSequential: Boolean? = null,
    val loopCardinality: String? = null,
    val loopDataInputRef: String? = null,
    val inputDataItem: String? = null,
    val variableMappings: List<VariableMapping> = emptyList(),
    var x: Double = 0.0,
    var y: Double = 0.0,
    var width: Double = 160.0,
    var height: Double = 60.0
)
```

### BpmnEdge

```kotlin
data class BpmnEdge(
    val id: String,
    val name: String?,
    val sourceRef: String,
    val targetRef: String,
    val conditionExpression: String? = null,
    var routePoints: List<Pair<Double, Double>> = emptyList()
)
```

---

## 12. 编辑模式架构

### 12.1 整体设计思路

**编辑层垂直堆叠策略（零改动只读代码）：**

编辑模式作为独立层叠加在只读查看模式之上，所有查看模式的类（`BpmnGraphPanel`、`BpmnDetailPanel`、`BpmnParser`、`BpmnNode`/`BpmnEdge`/`BpmnModel`）保持不可变，不做任何修改。编辑功能通过全新的 `edit/` 包和新增 UI 面板实现。

**编辑/查看模式切换机制：**

- 由 `BpmnFileEditor` 统一管理模式切换，通过 `toggleButton`（"切换编辑" / "切换查看"）触发
- `switchToEditMode()`：以已布局的 `laidOut: BpmnModel` 为基础，调用 `EditableModel.fromBpmnModel()` 生成可变模型，初始化 CommandStack 和编辑 UI
- `switchToViewMode()`：若有未保存修改弹出确认对话框，确认后释放编辑资源，恢复查看 UI
- 两套 UI 完全独立构建，不复用组件实例

**可变模型与不可变快照的分离：**

- `EditableModel`（可变）：编辑期间所有修改直接作用于此
- `BpmnModel`（不可变快照）：通过 `editModel.toSnapshot()` 按需生成，供渲染和路由计算使用
- `BpmnEditGraphPanel` 内部持有 `cachedSnapshot`，每次 `refreshModel()` 时重新生成快照

**统一回调编排：**

`BpmnFileEditor` 作为唯一设置 `commandStack.onStateChanged` 的位置，确保每次命令执行后：
```kotlin
cs.onStateChanged = {
    graph.refreshModel()      // 重绘图形面板
    props.refreshFromModel()  // 刷新属性面板
    updateButtons()           // 更新 undo/redo/save 按钮状态
}
```

---

### 12.2 模块职责

#### EditableModel（可编辑数据模型）

**文件**: `edit/EditableModel.kt`

**EditableNode 结构：**

与 `BpmnNode` 字段一一对应的可变类，所有属性均为 `var`。除基本信息外还包括：
- 脚本配置：`scriptFormat`、`scriptContent`
- 定时器：`timerDuration`、`timeCycle`、`timeDate`
- 消息/信号/错误：`messageRef`、`signalRef`、`errorRef`、`errorCode`
- 异步执行：`isAsync`
- 边界事件：`attachedToRef`、`cancelActivity`
- 监听器：`executionListeners`、`taskListeners`（均为 `MutableList<ListenerDef>`）
- 表单：`formKey`
- 坐标：`x`、`y`、`width`、`height`

**EditableEdge 结构：**

可变连线模型，包含 `id`、`name`、`sourceRef`、`targetRef`、`conditionExpression`、`routePoints`、`isDefaultFlow`、`isMessageFlow`、`isAssociation`。

**EditableModel 结构：**

扁平化流程模型（不区分 pools/lanes），包含 `processId`、`processName`、`nodes: MutableList<EditableNode>`、`edges: MutableList<EditableEdge>`。

**与 BpmnModel 的转换关系：**

| 方法 | 方向 | 说明 |
|------|------|------|
| `EditableModel.fromBpmnModel(model)` | BpmnModel → EditableModel | 进入编辑模式时调用 |
| `EditableNode.fromBpmnNode(node)` | BpmnNode → EditableNode | 复制所有字段为可变 |
| `EditableEdge.fromBpmnEdge(edge)` | BpmnEdge → EditableEdge | 复制所有字段为可变 |
| `editableModel.toSnapshot()` | EditableModel → BpmnModel | 生成不可变快照用于渲染 |
| `editableNode.toBpmnNode()` | EditableNode → BpmnNode | 单节点转换 |
| `editableEdge.toBpmnEdge()` | EditableEdge → BpmnEdge | 单连线转换 |

---

#### EditCommand（编辑命令）

**文件**: `edit/EditCommand.kt`

**命令模式设计：**

`EditCommand` 是 `sealed interface`，每个命令必须实现：
- `execute(model: EditableModel)` — 正向执行
- `undo(model: EditableModel)` — 撤销恢复
- `description(): String` — 可读描述（用于调试）

**所有命令类型列表：**

| 命令类 | 用途 |
|--------|------|
| `MoveNodeCommand` | 移动节点位置，记录 fromX/Y 和 toX/Y |
| `AddNodeCommand` | 添加新节点 |
| `RemoveNodeCommand` | 删除节点及其关联连线（撤销时恢复） |
| `ChangePropertyCommand` | 修改节点属性（支持 27 种属性名） |
| `AddEdgeCommand` | 添加连线 |
| `RemoveEdgeCommand` | 删除连线（撤销时恢复） |
| `ChangeEdgePropertyCommand` | 修改连线属性（name / conditionExpression） |
| `AddVariableMappingCommand` | 为节点新增变量映射 |
| `RemoveVariableMappingCommand` | 删除指定下标的变量映射 |
| `UpdateVariableMappingCommand` | 整体替换指定下标的变量映射 |
| `AddListenerCommand` | 新增执行/任务监听器 |
| `RemoveListenerCommand` | 删除指定下标的监听器 |
| `UpdateListenerCommand` | 整体替换指定下标的监听器 |

**ChangePropertyCommand 支持的属性名清单（27个）：**

`name`、`javaClass`、`delegateExpression`、`assignee`、`candidateUsers`、`candidateGroups`、`calledElement`、`formKey`、`scriptFormat`、`scriptContent`、`isAsync`、`isSequential`、`loopCardinality`、`loopDataInputRef`、`inputDataItem`、`completionCondition`、`loopCondition`、`timerDuration`、`timeCycle`、`timeDate`、`messageRef`、`signalRef`、`errorRef`、`errorCode`、`attachedToRef`、`cancelActivity`

**ListenerKind 常量：**

- `ListenerKind.EXECUTION = "execution"` — 执行监听器
- `ListenerKind.TASK = "task"` — 任务监听器

---

#### CommandStack（命令栈）

**文件**: `edit/CommandStack.kt`

**undo/redo 机制：**

- `undoStack: MutableList<EditCommand>` — 已执行命令栈
- `redoStack: MutableList<EditCommand>` — 已撤销命令栈
- `execute(cmd, model)`：执行命令并入栈，清空 redoStack
- `undo(model)`：从 undoStack 弹出末尾命令执行 `cmd.undo()`，压入 redoStack
- `redo(model)`：从 redoStack 弹出末尾命令执行 `cmd.execute()`，压回 undoStack
- 栈深上限 `maxSize = 50`，超限时移除最早的命令

**savePoint 脏状态追踪：**

- `savePoint: Int` — 记录上次保存时 undoStack 的大小
- `isModified: Boolean` = `undoStack.size != savePoint`
- `markSaved()`：将 savePoint 设为当前 undoStack.size
- `clear()`：清空所有栈，重置 savePoint 为 0

**onStateChanged 回调：**

- `var onStateChanged: (() -> Unit)?` — 每次 execute / undo / redo / markSaved / clear 后自动调用
- 由 `BpmnFileEditor` 统一设置，驱动 UI 刷新链路

---

#### BpmnEditGraphPanel（可编辑图形面板）

**文件**: `ui/BpmnEditGraphPanel.kt`

**交互状态机（5种状态）：**

```
┌──────────────────────────────────────────────────────────────┐
│ IDLE ─────────── 点击节点 ──────────▶ NODE_SELECTED          │
│  │                                       │                   │
│  │ 空白拖拽                 拖拽 > 5px │                   │
│  ▼                                       ▼                   │
│ PANNING                            DRAGGING_NODE             │
│  │                                       │                   │
│  └── 释放 → IDLE              释放 → NODE_SELECTED          │
│                                                              │
│ NODE_SELECTED ── Shift+点击节点 ──▶ CONNECTING_FROM          │
│                                       │                      │
│              点击目标节点 → 创建连线 → IDLE                   │
│              Esc / 点击空白 → 取消 → IDLE                    │
└──────────────────────────────────────────────────────────────┘
```

| 状态 | 含义 |
|------|------|
| `IDLE` | 无选中无操作 |
| `NODE_SELECTED` | 节点被选中（蓝色高亮），等待进一步操作 |
| `DRAGGING_NODE` | 正在拖拽节点（鼠标移动 > 5px 阈值后触发） |
| `CONNECTING_FROM` | 连线模式：从源节点拉出虚线预览至鼠标位置 |
| `PANNING` | 空白区域拖拽平移画布 |

**节点拖拽：**

1. 鼠标按下时记录节点原始位置（`dragNodeOriginX/Y`）和偏移量
2. 拖拽过程中直接修改 `EditableNode.x/y`（实时预览，30ms 节流重绘）
3. 释放时先恢复原始位置，再通过 `MoveNodeCommand` 正式执行移动
4. 移动后调用 `rerouteEdges()` 使用 `EdgeRouter` 重新计算所有连线路由

**连线创建：**

1. Shift + 点击源节点 → 进入 `CONNECTING_FROM` 状态，光标变为十字
2. 绘制蓝色虚线预览（源节点中心到鼠标位置）
3. 点击目标节点 → 生成 `AddEdgeCommand`，初始路由点为两节点中心连线
4. 之后调用 `rerouteEdges()` 计算正交折线路由
5. 禁止自环连线（源 == 目标时自动取消）

**删除操作：**

- Delete / BackSpace 键或右键菜单"删除"
- 节点删除使用 `RemoveNodeCommand`（同时清理关联连线）
- 连线删除使用 `RemoveEdgeCommand`

**与 Palette 的联动：**

- 持有 `var palettePanel: BpmnPalettePanel?` 引用
- 空白点击时检查 `palettePanel?.getSelectedType()`，若有选中类型则调用 `addNodeAt()` 放置节点
- 放置后调用 `palettePanel?.clearSelection()` 清除选中态
- `addNodeAt(type, screenX, screenY)` 根据类型设置默认尺寸（Task 100×80, Gateway 50×50, Event 36×36）

**渲染：**

- 复用查看模式的 `BpmnNodeRenderer` 和 `BpmnEdgeRenderer` 渲染快照
- 选中态：通过 `selectedNodeId` / `selectedEdgeId` 传递给 renderer 高亮绘制
- 坐标变换与查看模式一致：级联 translate + scale，支持 Ctrl+滚轮缩放

---

#### BpmnPropertyPanel（属性编辑面板）

**文件**: `ui/BpmnPropertyPanel.kt`

**按节点类型动态构建编辑区域：**

通过 `rebuildForNode(node)` 根据节点类型条件性展示各属性分组：

| 分组 | 显示条件 | 可编辑字段 |
|------|---------|------------|
| 基本信息 | 所有节点 | ID（只读）、名称、类型（只读） |
| 实现配置 | SERVICE_TASK / SEND_TASK / MAIL_TASK / BUSINESS_RULE_TASK | Java类、委托表达式、异步、表单Key |
| 手工任务 | MANUAL_TASK | 异步 |
| 用户任务 | USER_TASK | 处理人、候选用户、候选组、表单Key |
| 脚本配置 | SCRIPT_TASK / SERVICE_TASK | 脚本格式、脚本内容（多行文本） |
| 边界事件 | BOUNDARY_* 类型 | 附属节点、中断活动 |
| 调用活动 | CALL_ACTIVITY | 调用元素 |
| 变量映射 | CALL_ACTIVITY | 表格式映射列表 |
| 多实例 | isSequential != null | 串行/并行、循环基数、数据输入引用、元素变量、完成条件、循环条件 |
| 定时器配置 | TIMER_START_EVENT / BOUNDARY_TIMER / INTERMEDIATE_TIMER_CATCH | 持续时间、循环表达式、日期 |
| 消息/信号/错误 | 对应事件类型 | 消息引用、信号引用、错误引用、错误码 |
| 执行监听器 | 所有 Task 类型 + CALL_ACTIVITY | 监听器表格 |
| 任务监听器 | USER_TASK | 监听器表格 |

**连线属性：**

| 分组 | 字段 |
|------|------|
| 连线信息 | ID（只读）、名称 |
| 条件表达式 | 表达式（多行文本） |

**变量映射编辑器（表格式增删改）：**

- 每行显示：方向标签（in/out，蓝/橙色）、源变量文本框、目标变量文本框、删除按钮
- 底部两个按钮："+ 添加输入映射" / "+ 添加输出映射"
- 文本框失焦时通过 `UpdateVariableMappingCommand` 全量替换
- 删除通过 `RemoveVariableMappingCommand` 按下标移除

**监听器编辑器（执行监听器/任务监听器）：**

- 每行显示：事件下拉框、实现类型下拉框（class/delegateExpression/expression）、实现值文本框、删除按钮
- 执行监听器事件选项：`start`、`end`、`take`
- 任务监听器事件选项：`create`、`assignment`、`complete`、`delete`
- 变更后通过 `UpdateListenerCommand` 全量替换
- 新增默认：事件=start/create，implementationType=class，implementation=""

**FocusListener 绑定机制：**

- 所有文本字段使用 `addFocusListener` 在 `focusLost` 事件触发提交
- CheckBox 使用 `addActionListener` 在勾选变化时触发
- `updating: Boolean` 标志位：在 `rebuildForNode()` 期间置为 true，防止回填值触发命令提交
- `refreshFromModel()`：由外部调用（CommandStack.onStateChanged），在 EDT 线程重新拉取当前选中实体最新值刷新表单

---

#### BpmnPalettePanel（元素工具栏）

**文件**: `ui/BpmnPalettePanel.kt`

**元素分组和类型：**

| 分组 | 元素 | BpmnNodeType |
|------|------|--------------|
| 事件 | 开始事件 | START_EVENT |
| 事件 | 结束事件 | END_EVENT |
| 事件 | 定时开始 | TIMER_START_EVENT |
| 事件 | 消息开始 | MESSAGE_START_EVENT |
| 任务 | 服务任务 | SERVICE_TASK |
| 任务 | 用户任务 | USER_TASK |
| 任务 | 脚本任务 | SCRIPT_TASK |
| 任务 | 手工任务 | MANUAL_TASK |
| 任务 | 邮件任务 | MAIL_TASK |
| 任务 | 调用活动 | CALL_ACTIVITY |
| 网关 | 排他网关 | EXCLUSIVE_GATEWAY |
| 网关 | 并行网关 | PARALLEL_GATEWAY |
| 网关 | 包含网关 | INCLUSIVE_GATEWAY |
| 网关 | 事件网关 | EVENT_BASED_GATEWAY |

**交互模式（单击选择 + 点击画布放置）：**

1. 用户在 Palette 中点击一个元素 → 该项进入选中态（蓝色背景 + 蓝色边框）
2. `BpmnEditGraphPanel` 在空白区域被点击时调用 `getSelectedType()` 获取选中类型
3. 放置完成后由 `BpmnEditGraphPanel` 调用 `clearSelection()` 清除选中态
4. 再次点击已选中元素则取消选中

**图标绘制（BpmnTypeIcon）：**

20×20 像素迷你预览图标，与画布渲染风格保持一致：
- 事件：圆形（开始绿色、结束红色粗线）
- 任务：蓝色圆角矩形 + 左上角类型标记（齿轮/头像/手掌/文档/信封）
- 调用活动：粗边框矩形（无图标）
- 网关：橙色菱形 + 中心符号（X / + / ○ / ★）

**与 EditGraphPanel 的联动：**

- Palette 不持有 EditGraphPanel 引用，仅暴露 `getSelectedType()` / `clearSelection()` 接口
- 由 EditGraphPanel 主动协作：设置 `graph.palettePanel = palette`
- 可选回调 `onNodeTypeSelected: ((BpmnNodeType?) -> Unit)?` 供外部刷新光标等反馈

---

#### BpmnSerializer（序列化器）

**文件**: `serializer/BpmnSerializer.kt`

**EditableModel → Activiti BPMN XML：**

核心方法 `serialize(model: EditableModel): String`，生成完整的 BPMN 2.0 XML 文档。

**命名空间和扩展元素处理：**

根元素 `<definitions>` 声明所有命名空间：

| 前缀 | URI |
|------|-----|
| (默认) | `http://www.omg.org/spec/BPMN/20100524/MODEL` |
| xsi | `http://www.w3.org/2001/XMLSchema-instance` |
| activiti | `http://activiti.org/bpmn` |
| bpmndi | `http://www.omg.org/spec/BPMN/20100524/DI` |
| omgdc | `http://www.omg.org/spec/DD/20100524/DC` |
| omgdi | `http://www.omg.org/spec/DD/20100524/DI` |

**扩展元素输出（`<extensionElements>`）：**
- 变量映射 → `<activiti:in source="" target="">` / `<activiti:out>`
- 执行监听器 → `<activiti:executionListener event="" class/expression/delegateExpression="">`
- 任务监听器 → `<activiti:taskListener event="" class/expression/delegateExpression="">`

**节点序列化特殊处理：**
- 排他/包含网关：输出 `default="edgeId"` 属性（默认流）
- 脚本任务：`<script>` 子元素用 CDATA 包裹
- 多实例：`<multiInstanceLoopCharacteristics>` + 子元素
- 事件定义：根据类型输出 `timerEventDefinition` / `messageEventDefinition` / `signalEventDefinition` / `errorEventDefinition` / `terminateEventDefinition` / `cancelEventDefinition` / `compensateEventDefinition`
- 条件表达式：`<conditionExpression xsi:type="tFormalExpression">` + CDATA
- POOL / LANE 类型跳过不输出
- messageFlow / association 类型连线跳过

**DI 坐标输出：**

```xml
<bpmndi:BPMNDiagram id="BPMNDiagram_1">
  <bpmndi:BPMNPlane bpmnElement="{processId}">
    <bpmndi:BPMNShape bpmnElement="{nodeId}">
      <omgdc:Bounds x="" y="" width="" height=""/>
    </bpmndi:BPMNShape>
    <bpmndi:BPMNEdge bpmnElement="{edgeId}">
      <omgdi:waypoint x="" y=""/>...
    </bpmndi:BPMNEdge>
  </bpmndi:BPMNPlane>
</bpmndi:BPMNDiagram>
```

**XML 输出格式：** UTF-8 编码、带 XML 声明、缩进 2 空格。

---

#### BpmnValidator（校验器）

**文件**: `validation/BpmnValidator.kt`

**校验严重级别：** `Severity.ERROR` / `Severity.WARNING`

**校验结果：** `ValidationIssue(severity, nodeId?, message)`

**验证规则列表（13 条，v2.1.1 新增 5 条）：**

| 规则 | 级别 | 说明 |
|------|------|------|
| `checkStartAndEnd` | ERROR | 流程必须包含至少一个开始事件和一个结束事件 |
| `checkUniqueIds` | ERROR | 节点 ID 不可为空、不可重复 |
| `checkEdgeReferences` | ERROR | 连线的 sourceRef / targetRef 必须引用存在的节点 |
| `checkBoundaryAttachedRef` | ERROR | 边界事件的 `attachedToRef` 必须引用存在的任务/子流程节点 |
| `checkCallActivityTarget` | WARNING | CallActivity 的 `calledElement` 应不为空 |
| `checkGatewayOutEdges` | WARNING | 网关（排他/包含/并行）应至少有一条出边 |
| `checkStartEventNoInEdge` | WARNING | 开始事件不应有入边 |
| `checkEndEventNoOutEdge` | WARNING | 结束事件不应有出边 |
| `checkServiceTaskImpl` | WARNING | 服务任务应配置 javaClass 或 delegateExpression |
| `checkUserTaskAssignment` | WARNING | 用户任务应配置处理人、候选用户或候选组 |
| `checkMultiInstanceConfig` | WARNING | 多实例节点应配置 `loopCardinality` 或 `loopDataInputRef` 之一 |
| `checkTimerConfig` | WARNING | 定时事件应配置 `timerDuration` / `timeCycle` / `timeDate` 之一 |
| `checkUnreachableNodes` | WARNING | 从开始事件出发不可达的节点 |

**校验时机：** 保存前调用。ERROR 级别阻止保存并弹窗展示；WARNING 级别弹出确认对话框由用户决定是否继续。

**问题定位（v2.1.1 新增）：**

校验弹窗中每条带 `nodeId` 的条目附「定位」按钮，点击后调用 `BpmnEditGraphPanel.selectAndScrollTo(nodeId)`，使画布选中该节点并居中显示。

---

### 12.3 数据流

从用户操作到文件保存的完整数据流：

```
用户在画布/属性面板/Palette上操作
    │
    ▼ 创建对应的 EditCommand
    │
    ▼ commandStack.execute(cmd, editModel)
    │
    ├── cmd.execute(editModel)    // 修改 EditableModel
    ├── 命令入 undoStack，清空 redoStack
    │
    ▼ onStateChanged 回调触发
    │
    ├── editGraphPanel.refreshModel()
    │       └── editModel.toSnapshot() → cachedSnapshot → repaint()
    ├── propertyPanel.refreshFromModel()
    │       └── rebuildForNode/Edge(最新数据)
    └── updateButtons()
            └── 更新 undo/redo/save 按钮 enabled 状态

    ... 用户多次编辑 ...

用户点击「保存」/ Ctrl+S
    │
    ▼ BpmnValidator().validate(editModel)
    │
    ├── 有 ERROR → 弹窗阻止保存
    ├── 有 WARNING → 确认对话框
    │
    ▼ BpmnSerializer().serialize(editModel)
    │
    ▼ 生成 Activiti BPMN 2.0 XML 字符串
    │
    ▼ ApplicationManager.runWriteAction {
    │     virtualFile.setBinaryContent(xml.toByteArray(UTF-8))
    │  }
    │
    ▼ commandStack.markSaved() → savePoint = undoStack.size
    │
    ▼ updateButtons() → save 按钮置灰
```

---

### 12.4 快捷键

| 快捷键 | 功能 |
|--------|------|
| Ctrl+S | 保存（编辑模式下） |
| Ctrl+Z | 撤销 |
| Ctrl+Shift+Z / Ctrl+Y | 重做 |
| Delete / BackSpace | 删除选中的节点或连线 |
| Shift+点击节点 | 进入连线模式 |
| Esc | 取消连线模式 |
| Ctrl+滚轮 | 缩放画布（0.3x ~ 3.0x） |

---

### 12.5 编辑模式 UI 布局

```
┌─────────────────────────────────────────────────────────────────┐
│ HeaderPanel: [title] [↶撤销] [↷重做] [保存] [切换查看]           │
├────────┬──────────────────────────────────┬─────────────────────┤
│        │                                  │                     │
│ Palette│      BpmnEditGraphPanel           │  BpmnPropertyPanel  │
│ (150px)│      (resizeWeight=0.75)          │  (290px)            │
│        │                                  │                     │
│  事件   │   [画布: 缩放/平移/节点拖拽/连线]   │  [动态属性表单]       │
│  任务   │                                  │  [变量映射表格]       │
│  网关   │                                  │  [监听器表格]         │
│        │                                  │                     │
└────────┴──────────────────────────────────┴─────────────────────┘
```

使用双层 `JSplitPane` 组织：外层 Palette | 内层（Graph | Property）。

---

### 12.6 已知问题

- ~~Bug #30~~：已于 v2.1.1 修复。`BpmnFileEditor.parseAndLayout()` 被抽取为独立方法，`switchToViewMode()` / 保存后均从 `virtualFile` 重新走 `BpmnParser`，不再依赖编辑模型快照。

---

## 13. 新建 BPMN 文件

### 13.1 功能描述

在 IntelliJ 的 **File → New** 菜单以及项目树右键 **New** 子菜单中新增 **"BPMN Process"** 选项，让用户在不切换到外部模板的前提下快速创建一个新的 `.bpmn` 文件。该入口与 IDE 内置的 "Java Class"、"File" 等条目并列，在 NewGroup 中注册。

### 13.2 默认行为

1. 用户点击菜单后弹出输入对话框，提示输入文件名（**不含扩展名**）。
2. 创建文件时自动追加 `.bpmn` 扩展名（若用户已带扩展名则去重）。
3. 文件内容使用最小有效 BPMN 2.0 XML 模板（含一个空 `<process>` 元素，无任何节点和连线）。
4. 创建成功后通过 `FileEditorManager.openFile()` 自动打开该文件，并默认进入 **编辑模式**（而非查看模式），以便用户立即开始建模。

### 13.3 文件模板

```xml
<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:activiti="http://activiti.org/bpmn"
             targetNamespace="http://www.activiti.org/processdef">
  <process id="${processId}" name="${processName}" isExecutable="true">
  </process>
</definitions>
```

占位符替换规则：

| 占位符 | 取值规则 |
|--------|---------|
| `${processId}` | 基于文件名生成的合法 ID（去除非字母数字下划线字符，必要时加前缀确保以字母开头） |
| `${processName}` | 与文件名一致（保留原始大小写与中文） |

模板有意保持最小化，不包含 `<bpmndi:BPMNDiagram>` DI 段——因为没有任何节点，DI 信息也无意义；进入编辑模式后用户添加的节点将由 `BpmnSerializer` 在保存时统一输出 DI 坐标。

### 13.4 实现方案

**新增类：`NewBpmnFileAction.kt`**

- 继承 `com.intellij.openapi.actionSystem.AnAction`
- `actionPerformed(e: AnActionEvent)` 中：
  1. 通过 `Messages.showInputDialog` 弹出文件名输入框，并校验非空、非法字符
  2. 拼接 `.bpmn` 后缀，确认目标目录中无同名文件（重名则提示并中止）
  3. 在 `WriteCommandAction.runWriteCommandAction` 中：基于模板字符串替换 `processId` / `processName`，调用目标 `PsiDirectory.createFile()` 或 `VirtualFile.createChildData()` + `setBinaryContent()` 写入内容
  4. 调用 `FileEditorManager.getInstance(project).openFile(virtualFile, true)` 打开新文件
- `update(e: AnActionEvent)`：仅在选中目录或项目视图上下文有效时启用

**plugin.xml 注册：**

```xml
<actions>
    <action id="BpmnViewer.NewBpmnFile"
            class="com.rickytech.bpmn.viewer.NewBpmnFileAction"
            text="BPMN Process"
            description="Create a new BPMN process file"
            icon="/icons/bpmn.svg">
        <add-to-group group-id="NewGroup" anchor="before" relative-to-action="NewFile"/>
    </action>
</actions>
```

通过 `<add-to-group group-id="NewGroup">` 同时覆盖 **File → New** 菜单与项目树右键的 **New** 子菜单（两者共享同一个 `NewGroup` action group）。

**自动进入编辑模式：**

- 修改 `BpmnFileEditor`：在 `parseAndLayout()` 完成后，若解析得到的 `BpmnModel` 既无节点也无边（即空流程），则在初始化阶段直接调用 `switchToEditMode()`，跳过查看模式 UI 构建。
- 该判断以"模型为空"为唯一条件，因此既适用于本 Action 创建的新文件，也适用于用户手工创建的任何空 BPMN 文件，避免引入 Action 与编辑器之间的额外耦合（如临时标志位、文件路径白名单等）。

### 13.5 代码结构

本特性新增/修改的文件：

| 文件 | 类型 | 说明 |
|------|------|------|
| `NewBpmnFileAction.kt` | 新增 | New File 菜单 action 实现 |
| `META-INF/plugin.xml` | 修改 | 注册 `<actions>` 节点 |
| `BpmnFileEditor.kt` | 修改 | 空流程自动进入编辑模式判断 |

### 13.6 交互流程

```
用户：File → New → BPMN Process
    │
    ▼ NewBpmnFileAction.actionPerformed
输入文件名（不含扩展名）
    │
    ├── 取消 / 输入为空 → 中止
    │
    ▼ 校验文件名 + 计算 processId / processName
    │
    ▼ WriteCommandAction：在目标目录写入模板内容（追加 .bpmn 后缀）
    │
    ▼ FileEditorManager.openFile(vf, focus=true)
    │
    ▼ BpmnFileEditorProvider.createEditor → BpmnFileEditor
    │
    ▼ parseAndLayout() → BpmnModel(nodes=[], edges=[])
    │
    ▼ 检测空流程 → switchToEditMode()
    │
    ▼ 用户立即进入编辑画布开始建模
```
