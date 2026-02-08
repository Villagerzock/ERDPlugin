# ERD IntelliJ Plugin

This project is an **IntelliJ IDEA plugin** for **visualizing and editing Entity-Relationship Diagrams (ERD)**.  
It supports both **reverse engineering existing databases** and **interactive editing** of ERDs inside a custom editor.

---

## ✨ Features

- 📊 Graphical ERD editor inside IntelliJ IDEA
- 🔄 Reverse engineering of databases into ERD models
- 🧩 Custom file type (`.erd`)
- 🖱️ Drag & drop table nodes
- 🔗 Relationship visualization (1:1, 1:n, n:m)
- 🧠 Automatic layout & collision avoidance
- 🪟 ToolWindow integration

---

## 🗂️ Core Classes

### `ReverseEngineDatabase`
Responsible for **reading database metadata** and generating an internal ERD model.

Typical responsibilities:
- Reading tables
- Analyzing attributes
- Detecting primary and foreign keys
- Deriving relationships

---

### `ErdFileType`
Defines the **custom IntelliJ FileType** for `.erd` files.

- Icon
- Name
- Description
- File extension

---

### `ErdFileEditor`
Implements the **editor for `.erd` files**.

- Hosts the `ErdEditorPanel`
- Integrates with the IntelliJ editor lifecycle
- Handles loading & saving

---

### `ERDToolWindow`
ToolWindow used to display and interact with ERD content.

- Dockable window
- Entry point for ERD actions
- Connected to the editor state

---

### `ErdEditorPanel`
Swing panel that encapsulates the actual editor UI.

- Toolbar
- Canvas integration
- Event handling
- State management

---

### `ErdCanvas`
The **graphical core** of the project.

- Rendering nodes and connections
- Zoom & pan support
- Collision avoidance
- Selection & hover logic
- Custom rendering using `Graphics2D`

---

## 🧱 Architecture Overview

```
.erd file
   ↓
ErdFileEditor
   ↓
ErdEditorPanel
   ↓
ErdCanvas  ←→ NodeGraph / Node / Connection
```

---

## 🛠️ Tech Stack

- Java
- IntelliJ Platform SDK
- Swing / Graphics2D
- IntelliJ FileEditor API
- IntelliJ ToolWindow API

---

## 🚀 Possible Extensions

- SQL / DDL export
- Advanced auto-layout algorithms
- Diff view for database changes
- Undo / redo support
- Dark mode optimizations

---

## 📄 License

Private / Educational – free to adapt for personal or educational projects.
