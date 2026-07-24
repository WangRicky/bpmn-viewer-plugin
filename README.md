# BPMN Viewer & Editor

**English** | [简体中文](README.zh-CN.md)

> A **BPMN 2.0 diagram viewer + editor** plugin for IntelliJ IDEA, Activiti-friendly.

[![Version](https://img.shields.io/badge/version-2.3.2-blue.svg)](CHANGELOG.md)
[![Platform](https://img.shields.io/badge/IntelliJ%20IDEA-2024.3.5%2B-orange.svg)](https://www.jetbrains.com/idea/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.25-purple.svg)](https://kotlinlang.org/)
[![JDK](https://img.shields.io/badge/JDK-17-red.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

Open, review and edit `.bpmn` files directly inside IntelliJ IDEA — no need to switch to an external modeling tool. The plugin renders diagrams with pure Swing + Java2D and is designed for Java backend developers using the **Activiti 5.x / 6.x** workflow engine.

---

## ✨ Features

### View Mode
- 🎨 **High-fidelity rendering**: bpmn.io visual style, 30+ node types (tasks / gateways / events / containers)
- 📐 **Smart layout**: prefers original BPMN DI coordinates; falls back to Sugiyama hierarchical layout when absent
- 🧭 **Orthogonal edge routing**: Manhattan Grid Router (grid discretization + A* search) with 90° entry/exit and automatic node avoidance
- 🔍 **Interactive browsing**: Ctrl + wheel zoom (0.3x ~ 3.0x), drag to pan, click nodes/edges to inspect properties
- ↪️ **Code navigation**: jump to a Service Task's Java implementation class; click a Call Activity to open its sub-process `.bpmn`
- 📋 **Copyable properties**: property panel text supports selection + Ctrl+C copy

### Edit Mode
- 🧲 **Drag-and-drop modeling**: place from the palette, drag nodes, Shift+click to connect
- 📝 **Two-way property editing**: forms generated dynamically per node type (implementation / user task / script / boundary event / multi-instance / timer / message·signal·error)
- 🔗 **Variable mappings & listeners**: table-based add/edit/delete of Call Activity variable mappings, execution/task listeners
- ↩️ **Undo / Redo**: command stack with 50-step history
- ✅ **Pre-save validation**: 13 modeling rules — ERROR blocks saving, WARNING asks for confirmation, and issues can be located on the canvas with one click
- 💾 **Lossless save**: serializes to Activiti-compatible BPMN 2.0 XML (with namespaces, extension elements and DI coordinates)
- 🆕 **Quick create**: File → New → **BPMN Process**, automatically entering edit mode after creation

---

## 📦 Installation

### Option 1: Install from disk (recommended)

1. Download or build the plugin ZIP (see [Build](#-build) below)
2. Open IntelliJ IDEA → **Settings / Preferences → Plugins**
3. Click the gear icon → **Install Plugin from Disk...**
4. Select `build/distributions/bpmn-viewer-plugin-2.3.2.zip`
5. Restart the IDE

### Option 2: Development / sandbox mode

```bash
./gradlew runIde      # Linux / macOS
gradlew.bat runIde    # Windows
```

This launches a sandbox IDE instance with the plugin pre-installed.

---

## 🔨 Build

**Requirements**

- JDK 17+
- IntelliJ IDEA 2024.3.5+ (build 243 ~ 251)

**Commands**

```bash
# Compile and package the plugin ZIP
gradlew.bat buildPlugin

# Run unit tests
gradlew.bat test

# Launch the sandbox IDE (development)
gradlew.bat runIde
```

Output location: `build/distributions/bpmn-viewer-plugin-*.zip`

> Windows PowerShell users should use `gradlew.bat`; other platforms use `./gradlew`.

---

## 🚀 Usage

1. Open any `.bpmn` file in your project; the plugin renders the diagram graphically
2. **View**: Ctrl+wheel to zoom, drag to pan, click a node/edge to inspect its properties on the right panel
3. **Edit**: click "Switch to Edit" in the toolbar, then drag elements from the left palette and edit properties
4. **Save**: `Ctrl+S` (runs validation and writes back BPMN XML automatically)

### Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl + S` | Save (edit mode) |
| `Ctrl + Z` | Undo |
| `Ctrl + Shift + Z` / `Ctrl + Y` | Redo |
| `Delete` / `Backspace` | Delete the selected node or edge |
| `Shift + click node` | Enter connect mode |
| `Esc` | Cancel connect mode |
| `Ctrl + wheel` | Zoom the canvas (0.3x ~ 3.0x) |

---

## 🏗️ Architecture

The plugin uses a layered architecture. The edit layer is stacked vertically on top of the read-only view layer, keeping both non-intrusive to each other:

```
┌─────────────────────────────────────────────────┐
│  UI layer (Swing)                                 │
│  View:  BpmnGraphPanel + BpmnDetailPanel          │
│  Edit:  BpmnEditGraphPanel + Palette + PropertyPanel│
├─────────────────────────────────────────────────┤
│  Layout layer                                     │
│  ManhattanGridRouter (A* orthogonal, default)     │
│  EdgeRouter (Z-shape fallback) / SugiyamaLayout   │
├─────────────────────────────────────────────────┤
│  Edit layer   EditableModel / EditCommand / Stack │
├─────────────────────────────────────────────────┤
│  Model layer  BpmnModel / BpmnNode / BpmnEdge     │
├─────────────────────────────────────────────────┤
│  Parse & serialize  BpmnParser (DOM) / BpmnSerializer│
├─────────────────────────────────────────────────┤
│  Data source  .bpmn files (BPMN 2.0 XML + Activiti)│
└─────────────────────────────────────────────────┘
```

**Core modules**

| Module | Responsibility |
|--------|----------------|
| `parser/` | namespace-aware DOM parsing: node types, extension elements, DI coordinates |
| `layout/` | Manhattan Grid A* routing + Sugiyama fallback layout |
| `ui/` | dual view/edit Swing panels + per-type renderers |
| `edit/` | mutable model + command-pattern undo/redo |
| `serializer/` | EditableModel → Activiti BPMN 2.0 XML |
| `validation/` | 13 pre-save validation rules |

> See [DESIGN.md](DESIGN.md) for the full design document and [CHANGELOG.md](CHANGELOG.md) for the version history.

---

## 🧰 Tech Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| Kotlin | 1.9.25 | main language |
| Java | 17 | runtime |
| IntelliJ Platform Gradle Plugin | 2.2.1 | IDE integration & build |
| Swing + Java2D | AWT stdlib | rendering (no third-party graphics library) |
| JDK DOM Parser | stdlib | BPMN XML parsing |
| JUnit 5 | 5.10.2 | unit testing |

---

## ⚠️ Known Limitations

- Edit mode is flat; nested sub-process drill-down editing is not yet supported
- POOL / LANE are view-only and skipped during serialization in edit mode
- Single `<process>` support (only the first process in a multi-process file is used)
- Edges are re-routed only after a node drag is released
- UI text is currently in Chinese; internationalization is pending

---

## 🗺️ Roadmap

- Nested sub-process editing (double-click drill-down + breadcrumb)
- Pool / lane editing and serialization
- PNG / SVG export
- Search by ID / name / class
- Multi-process support and internationalization

---

## 🤝 Contributing

Issues and pull requests are welcome:

1. Fork the repo and create a feature branch
2. Run `gradlew.bat test` before submitting to make sure tests pass
3. Keep commit messages clear, and update [CHANGELOG.md](CHANGELOG.md) / [DESIGN.md](DESIGN.md) when relevant

---

## 📄 License

Licensed under the [MIT License](LICENSE).
