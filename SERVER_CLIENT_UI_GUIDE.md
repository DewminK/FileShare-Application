# File Sharing System - Server & Client UI Guide

## 🖥️ System Overview

This distributed file sharing system now includes **separate JavaFX UIs** for both the **Server** and **Client**, using the same modern design structure.

## 📁 Components

### Server Side (Member 1)
- **ServerMain.java** - Core server logic with TCP socket handling
- **ServerUI.java** - JavaFX GUI for server management

### Client Side (Member 2)
- **ClientMain.java** - Core client logic with TCP socket connection
- **ClientUI.java** - JavaFX GUI for client file operations
- **FileTransferHandler.java** - Handles file uploads/downloads

### Synchronization (Member 4)
- **SynchronizedFileAccess.java** - Thread-safe file operations
- **FileTransferCoordinator.java** - Integration wrapper

## 🚀 Running the System

### Option 1: Run Complete System (Recommended)
Runs both server and client in separate windows:
```bash
run_complete_system.bat
```

### Option 2: Run Server Only
```bash
run_server.bat
```
Or with Maven:
```bash
mvn clean compile exec:java -Dexec.mainClass="server.ServerUI"
```

### Option 3: Run Client Only
```bash
run_client.bat
```
Or with Maven:
```bash
mvn javafx:run
```

## 🎨 Server UI Features

### Server Control Panel
- **Port Configuration**: Set the server port (default: 8080)
- **Shared Directory**: Choose where files are stored
- **Browse Button**: Select directory using file browser
- **Start/Stop Server**: Control server operation
- **Status Indicator**: Shows running/stopped status

### Connected Clients Table
- **Client Address**: Shows IP address of connected clients
- **Status**: Connection status (Active)
- **Connection Time**: How long each client has been connected
- **Refresh Button**: Update the client list

### Shared Files Table
- **File Name**: Names of all files in shared directory
- **Size**: File size in human-readable format
- **Date Modified**: Last modification date/time
- **Refresh Files**: Update the file list
- **Open Folder**: Open shared directory in file explorer

### Activity Log
- Real-time logging of all server activities
- Client connections/disconnections
- File uploads/downloads
- Errors and status messages
- Timestamp for each event

### Statistics Bar
- Current server status
- Number of connected clients
- Number of shared files

## 🎨 Client UI Features

### Connection Panel
- **Server Address**: IP address of the server
- **Port**: Server port number
- **Connect/Disconnect**: Control connection
- **Status Indicator**: Shows connection status

### Available Files Table
- Lists all files on the server
- Shows file name, size, and date
- Select files for download

### File Operations
- **Upload File**: Upload files to server
- **Download Selected**: Download selected file
- **Refresh List**: Update file list from server

### Activity Log & Progress
- Real-time activity logging
- Progress bar for uploads/downloads
- Transfer speed and status

## 📊 Server UI Screenshot Description

```
┌──────────────────────────────────────────────────────────────────┐
│  Server Control                                                   │
│  Port: [8080]  Shared Directory: [./shared_files] [Browse]       │
│  [Start Server] [Stop Server]          Status: Running           │
├──────────────────────────────────────────────────────────────────┤
│                                                                    │
│  Connected Clients          │  Shared Files                       │
│  ┌────────────────────────┐ │  ┌───────────────────────────────┐│
│  │ Address    │Status│Time││ │  │Name      │Size    │Date      ││
│  │ 192.168.1.5│Active│45s ││ │  │file1.txt │1.2 KB  │Nov 7... ││
│  │ 192.168.1.8│Active│23s ││ │  │image.jpg │145 KB  │Nov 7... ││
│  └────────────────────────┘ │  └───────────────────────────────┘│
│  [Refresh Clients]          │  [Refresh Files] [Open Folder]    │
├──────────────────────────────────────────────────────────────────┤
│  Server Activity Log                                              │
│  [12:30:15] Server started on port 8080                          │
│  [12:30:22] Client connected: 192.168.1.5                        │
│  [12:30:45] UPLOAD - 192.168.1.5 - file1.txt                    │
│  Statistics: Running | Clients: 2 | Files: 5                     │
└──────────────────────────────────────────────────────────────────┘
```

## 🔧 Configuration

### Server Configuration
1. **Port**: Default is 8080, can be changed before starting
2. **Shared Directory**: Default is `./shared_files`, browse to change
3. Directory is created automatically if it doesn't exist

### Client Configuration
1. **Server Address**: Default is `localhost` for local testing
2. **Port**: Must match server port (default 8080)
3. **Download Directory**: Files are saved to `./downloads`

## 🧪 Testing the System

### Test Scenario 1: Basic Connection
1. Start the **Server UI** using `run_server.bat`
2. Click **Start Server**
3. Start the **Client UI** using `run_client.bat`
4. Click **Connect** in the client
5. Verify client appears in server's "Connected Clients" table

### Test Scenario 2: File Upload
1. In **Client UI**, click **Upload File**
2. Select a file to upload
3. Watch progress bar in client
4. Verify log entry in server: "UPLOAD - [address] - [filename]"
5. Click **Refresh Files** in server to see the new file

### Test Scenario 3: File Download
1. In **Client UI**, select a file from the table
2. Click **Download Selected**
3. Watch progress bar
4. Verify file appears in `./downloads` directory

### Test Scenario 4: Multiple Clients
1. Run `run_multiple_clients.bat` or start multiple client instances
2. Connect all clients to the server
3. Verify all appear in server's "Connected Clients" table
4. Upload files from different clients
5. Download same file from multiple clients (tests Member 4's synchronization)

## 📝 Design Structure

Both UIs follow the same design pattern:

### Layout Structure
```
BorderPane (Main Layout)
├── Top: Control Panel (VBox)
│   └── Configuration and action buttons
├── Center: Main Content
│   └── Tables for data display (clients/files)
└── Bottom: Log Panel (VBox)
    └── Activity log and statistics
```

### Common Styling
- Same CSS stylesheet (`styles.css`)
- Consistent color scheme
- Matching button styles
- Uniform table appearance
- Professional modern look

### UI Components Used
- **TableView**: Display clients and files
- **TextField**: Input for configuration
- **Button**: Actions (Start/Stop, Upload/Download)
- **Label**: Status and titles
- **TextArea**: Activity logging
- **ProgressBar**: Transfer progress (client)
- **SplitPane**: Divide content areas (server)

## 🔌 Integration Points

### Member 1 (Server Developer) - ✅ Complete
- ServerMain.java - TCP server logic
- ServerUI.java - JavaFX interface
- Listener pattern for UI updates

### Member 2 (Client Developer) - ✅ Complete
- ClientMain.java - TCP client logic
- ClientUI.java - JavaFX interface
- FileTransferHandler.java - File operations

### Member 4 (Synchronization) - ✅ Complete
- Ready to integrate into ServerMain
- Thread-safe file access
- See `MEMBER4_README.md` for integration guide

### Member 3 (File Handler) - Integration Ready
- Can use NIO channels with synchronization
- Coordinate with Member 4's locks

### Member 5 (Broadcaster) - Integration Ready
- Can access client list from ServerMain
- Use listener pattern for notifications

## 🎯 Key Features

### Server UI Highlights
✅ Visual server monitoring  
✅ Real-time client tracking  
✅ File system browsing  
✅ Activity logging  
✅ Statistics display  
✅ Easy start/stop controls  

### Client UI Highlights
✅ Intuitive file browsing  
✅ Progress tracking  
✅ Multiple file operations  
✅ Connection management  
✅ Activity logging  
✅ Modern interface  

## 🛠️ Troubleshooting

### Server won't start
- Check if port is already in use
- Verify shared directory permissions
- Check firewall settings

### Client can't connect
- Ensure server is running
- Verify IP address and port match
- Check network connectivity
- Disable firewall for testing

### Files not appearing
- Click "Refresh Files" button
- Check shared directory path
- Verify file permissions

## 📚 Code Structure

```
src/
├── server/
│   ├── ServerMain.java        (TCP server logic)
│   ├── ServerUI.java          (JavaFX GUI)
│   └── Server.java            (placeholder)
├── client/
│   ├── ClientMain.java        (TCP client logic)
│   ├── ClientUI.java          (JavaFX GUI)
│   └── FileTransferHandler.java
├── shared/
│   ├── SynchronizedFileAccess.java (Member 4)
│   └── FileTransferCoordinator.java (Member 4)
└── resources/
    └── styles.css             (Shared styling)
```

## 🎓 Learning Outcomes

This implementation demonstrates:
- **JavaFX Application Development**
- **Observer/Listener Pattern**
- **Multithreading** (Server handles multiple clients)
- **TCP Socket Programming**
- **Event-Driven Programming**
- **MVC-like Architecture**
- **Real-time UI Updates**
- **Concurrent File Access** (Member 4)

## 📝 Notes

- Both server and client can run on the same machine for testing
- Use `localhost` or `127.0.0.1` for local testing
- For network testing, use actual IP addresses
- Server must start before clients connect
- All UI updates use `Platform.runLater()` for thread safety

---

**Happy File Sharing! 🚀**
