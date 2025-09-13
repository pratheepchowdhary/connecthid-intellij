# ConnectHID - IntelliJ Plugin

![IntelliJ Plugin](https://img.shields.io/badge/IntelliJ-Plugin-orange.svg)
![Platform](https://img.shields.io/badge/Platform-IntelliJ%20IDEA-blue.svg)
![Language](https://img.shields.io/badge/Language-Kotlin-purple.svg)

**ConnectHID** is a comprehensive IntelliJ IDEA plugin designed for cloud and server management, enabling developers to seamlessly manage remote servers, synchronize code, and perform various DevOps operations directly from their IDE.

## 🚀 Features

### Core Functionality
- **Two-way Code Synchronization** - Sync your local code with remote servers effortlessly
- **Server Management** - Connect to and manage multiple remote servers
- **Terminal Integration** - Open remote terminals directly in your IDE
- **SFTP File Management** - Browse and manage files on remote servers

### Advanced Capabilities
- **Docker Container Management** - Control Docker containers on remote servers
- **Database Management** - Connect to and manage PostgreSQL, MySQL, and MongoDB databases
- **Script Management** - Execute and manage scripts on remote servers
- **Cron Job Management** - Schedule and manage cron jobs
- **Application Deployment** - Deploy applications to remote servers
- **Server Monitoring** - Monitor server performance and metrics
- **Android Open Source Project (AOSP) Support** - Special tools for AOSP development

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
   - Or use the toolbar button on the right side

2. **Add a Server:**
   - Click the "+" button in the Servers tab
   - Enter server details (host, username, port, authentication method)
   - Choose authentication method: SSH Key or Password
   - Click "Add" to save the connection

3. **Connect to Server:**
   - Select a server from the list
   - Click the connect button
   - Access terminal, file browser, and other tools

### Main Interface

The plugin provides a tabbed interface with two main sections:

#### Servers Tab
- View and manage all your server connections
- Monitor server status and system information
- Quick access to terminal and file management

#### Code Syncing Tab
- Configure and manage file synchronization
- Set up rsync configurations
- Monitor sync status and progress

### Available Actions

Access these actions through the Tools menu or toolbar:

- **Connect Server** - Establish connection to remote servers
- **Open Terminal** - Launch remote terminal sessions
- **Sync Files** - Synchronize local and remote files
- **Manage Docker** - Control Docker containers
- **Manage Database** - Database operations and queries
- **Manage Scripts** - Execute and organize scripts
- **Manage Cron** - Schedule and monitor cron jobs
- **Deploy Application** - Deploy apps to servers
- **Monitor Server** - View server metrics and performance
- **AOSP Tools** - Android development utilities

## 🏗️ Architecture

### Core Components

```
src/main/kotlin/com/connecthid/intellij/
├── ConnectHIDPlugin.kt              # Main plugin entry point
├── models/                          # Data models
│   ├── Server.kt                   # Server connection model
│   ├── AuthenticationMethod.kt     # Authentication types
│   └── SystemInfo.kt              # System information model
├── services/                        # Business logic services
│   ├── ConnectHidServiceImpl.kt    # Main service implementation
│   ├── ServerConnectionService.kt   # SSH/connection management
│   ├── FileSyncService.kt          # File synchronization
│   ├── DockerService.kt            # Docker operations
│   ├── DatabaseService.kt          # Database connectivity
│   ├── MonitoringService.kt        # Server monitoring
│   └── [other services]
├── ui/                             # User interface components
│   ├── servers/                    # Server management UI
│   ├── rsync/                      # File sync UI
│   ├── actions/                    # Action implementations
│   ├── filemanager/               # SFTP file browser
│   └── [other UI components]
└── connection/                     # Connection utilities
    ├── terminal/                   # Terminal integration
    └── vfs/                       # Virtual file system
```

### Technology Stack

- **Language:** Kotlin
- **Platform:** IntelliJ Platform SDK 2023.3.6
- **Build Tool:** Gradle with Kotlin DSL
- **SSH Library:** JSch 0.1.55
- **Docker API:** docker-java 3.3.4
- **Database Drivers:** PostgreSQL, MySQL, MongoDB
- **Monitoring:** Micrometer with Prometheus

## 🔧 Development

### Setting Up Development Environment

1. **Prerequisites:**
   - IntelliJ IDEA Ultimate or Community
   - JDK 17 or later
   - Git

2. **Import Project:**
   ```bash
   git clone https://github.com/pratheepchowdhary/connecthid-intellij.git
   cd connecthid-intellij
   ./gradlew build
   ```

3. **Run Plugin in Development:**
   ```bash
   ./gradlew runIde
   ```

### Building and Testing

```bash
# Build the plugin
./gradlew buildPlugin

# Run tests
./gradlew test

# Check code quality
./gradlew check

# Sign plugin (for distribution)
./gradlew signPlugin

# Publish plugin
./gradlew publishPlugin
```

### Project Structure

- **`build.gradle.kts`** - Main build configuration
- **`gradle.properties`** - Gradle and plugin properties
- **`src/main/resources/META-INF/plugin.xml`** - Plugin descriptor
- **`src/main/kotlin/`** - Main source code
- **`.idea/`** - IntelliJ project files

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. **Fork the repository**
2. **Create a feature branch:** `git checkout -b feature/amazing-feature`
3. **Commit your changes:** `git commit -m 'Add amazing feature'`
4. **Push to the branch:** `git push origin feature/amazing-feature`
5. **Open a Pull Request**

### Development Guidelines

- Follow Kotlin coding conventions
- Write meaningful commit messages
- Add tests for new features
- Update documentation as needed
- Ensure compatibility with IntelliJ Platform SDK

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

- **Issues:** [GitHub Issues](https://github.com/pratheepchowdhary/connecthid-intellij/issues)
- **Documentation:** [Plugin Documentation](https://connecthid.com/docs)
- **Email:** support@connecthid.com
- **Website:** [https://connecthid.com](https://connecthid.com)

## 👤 About the Author

**Pratheep Chowdhary** is the creator and maintainer of ConnectHID. As a passionate developer focused on improving developer productivity and cloud infrastructure management, Pratheep has designed this plugin to bridge the gap between local development and remote server management.

### Connect with Pratheep:
- **GitHub:** [@pratheepchowdhary](https://github.com/pratheepchowdhary)
- **LinkedIn:** [Connect on LinkedIn](https://linkedin.com/in/pratheepchowdhary)
- **Email:** [pratheep@connecthid.com](mailto:pratheep@connecthid.com)
- **Website:** [ConnectHID](https://connecthid.com)

### Professional Background:
Pratheep specializes in:
- **DevOps & Cloud Infrastructure** - Expert in server management and automation
- **IntelliJ Plugin Development** - Deep knowledge of JetBrains Platform SDK
- **Kotlin Development** - Proficient in modern Kotlin practices and patterns
- **Remote Development Tools** - Focus on developer experience and productivity

*"Building tools that make developers' lives easier and more productive is my passion. ConnectHID represents my vision of seamless integration between local development environments and remote infrastructure."* - Pratheep Chowdhary

## 🎉 Acknowledgments

- JetBrains for the IntelliJ Platform
- The Kotlin community
- All contributors and users

---

**Made with ❤️ by ConnectHID Team**