# ConnectHID - IntelliJ Plugin

![IntelliJ Plugin](https://img.shields.io/badge/IntelliJ-Plugin-orange.svg)
![Platform](https://img.shields.io/badge/Platform-IntelliJ%20IDEA-blue.svg)
![Language](https://img.shields.io/badge/Language-Kotlin-purple.svg)

**ConnectHID** is an IntelliJ IDEA plugin for seamless remote server management. It allows developers to manage SSH connections, access remote terminals, and browse files via SFTP directly from the IDE.

## 🚀 Features

### ✅ Implemented
These features are currently available and functional:

- **Server Connection Management**
  - Support for **SSH Key** and **Password** authentication.
  - Connection pooling for performance.
  - Automatic retrieval of remote system information (OS, CPU, RAM, Storage).
  - Organized Server List with status indicators.

- **Integrated Terminal**
  - Open fully functional SSH terminal sessions directly within IntelliJ.
  - Seamless integration with the IDE layout.

- **SFTP File Management**
  - Browse remote file systems using a familiar file tree interface.
  - Built on a custom Virtual File System implementation (`SftpFileSystem`).

- **Workspaces & Tasks**
  - **Workspaces**: Group and organize your server contexts.
  - **Task Library**: Save and manage frequently used script snippets (`TasksPanel`).

### 🚧 Roadmap (Coming Soon)
These features are planned or currently under development (stubs may exist but are not fully operational):

- **Real-time Server Monitoring** (CPU/RAM/Disk metrics)
- **Docker Container Management**
- **Database Management** (PostgreSQL, MySQL, MongoDB)
- **Advanced Two-way Code Synchronization** (Rsync integration)
- **Cron Job Manager**
- **AOSP (Android Open Source Project) Build Tools**

## 📋 Requirements

- **IntelliJ IDEA 2023.3.6 or later**
- **Java 17 or later**
- **Kotlin support** (bundled with IntelliJ)

## 🛠️ Installation

### From Source

1. **Clone the repository:**
   ```bash
   git clone https://github.com/pratheepchowdhary/connecthid-intellij.git
   cd connecthid-intellij
   ```

2. **Build the plugin:**
   ```bash
   ./gradlew buildPlugin
   ```

3. **Install in IntelliJ IDEA:**
   - Go to `File > Settings > Plugins`
   - Click the gear icon and select `Install Plugin from Disk...`
   - Select the built plugin file from `build/distributions/`

### From JetBrains Marketplace
*(Coming soon)*

## 🎯 Usage

### Getting Started

1. **Open the ConnectHID Tool Window:**
   - Navigate to `View > Tool Windows > Connect HID`
   - Or use the toolbar button on the right side.
   - You will see tabs for **Servers**, **Workspaces**, and **Tasks**.

2. **Add a Server:**
   - In the "Servers" tab, click the **+** (Add) button.
   - Enter your Host IP, Username, and Port (default 22).
   - Select Authentication: **Password** or **Private Key**.
   - Click **Add**.

3. **Connect & Interact:**
   - Click the **Connect** button on a server item.
   - Once green (connected), you can:
     - **Open Terminal**: Click the terminal icon to launch a shell.
     - **Browse Files**: Click the folder icon to open the SFTP browser.

## 🏗️ Architecture

### Core Components

```
src/main/kotlin/com/connecthid/intellij/
├── ConnectHIDPlugin.kt              # Main ToolWindow entry point
├── services/
│   ├── ServerConnectionService.kt   # Manages connection state & persistence
│   └── ConnectHIDConfigService.kt   # Configuration management
├── connection/
│   ├── ssh/                         # SSHJ wrapper & connection pooling
│   ├── sftp/                        # Virtual File System for SFTP
│   └── terminal/                    # Remote terminal widgets
├── ui/
│   ├── servers/                     # Server list & connection UI
│   ├── workspaces/                  # Workspace management UI
│   └── tasks/                       # Task/Script library UI
└── models/                          # Data classes (Server, SystemInfo, etc.)
```

### Technology Stack

- **Language:** Kotlin
- **Platform:** IntelliJ Platform SDK
- **SSH Library:** SSHJ + Connection Pooling
- **Persistence:** IntelliJ `PersistentStateComponent`

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. **Fork the repository**
2. **Create a feature branch:** `git checkout -b feature/amazing-feature`
3. **Commit your changes:** `git commit -m 'Add amazing feature'`
4. **Push to the branch:** `git push origin feature/amazing-feature`
5. **Open a Pull Request**

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

- **Issues:** [GitHub Issues](https://github.com/pratheepchowdhary/connecthid-intellij/issues)
- **Email:** support@connecthid.com
- **Website:** [https://connecthid.com](https://connecthid.com)

---

**Made with ❤️ by ConnectHID Team**