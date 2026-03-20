# Logistics Ports

A Minecraft mod that introduces a flexible logistics system for item and fluid transportation, designed to integrate seamlessly with the **Create** mod.

## Features

### 📡 Frequency-Based Logistics
- Blocks are linked through unique **frequencies**.
- Synchronize frequencies by right-clicking one port block with another port item in your hand.
- Frequencies can also be generated randomly by clicking two empty ports together.

### 📥 Access Port
- The control center for your logistics requests.
- **Recipe Configuration**: Set up a list of required items and one fluid in the settings menu (Shift + Right Click).
- **Ordering**: Request the configured items and fluids from available **Output Ports** on the same frequency.
- **Redstone Control**: An order can be triggered by a redstone signal (one pulse per order).
- **Multiport Mode**:
  - Can be toggled in the settings menu.
  - Changes the block name to **Access Multiport**.
  - Disables fluid settings (only items).
  - All 9 slots remain available, but each item can be ordered individually via separate "Order" buttons in the UI.
  - Right-click always opens the UI instead of placing a quick order.
  - Redstone signals do **not** trigger orders in this mode.
- **Status Indicators**: Changes color (Green for success, Red for failure) to show the order's progress.
- **Indicator Item**: Displays the "result" item on its face to identify different ports at a glance.

### 📤 Output Port
- Acts as a destination or a buffer for your items.
- Items and fluids in containers adjacent to an Output Port are considered "available" for the system.
- Can store items internally and provide them to players upon interaction.

### 📦 Create Integration & Packaging
- **Requires the Create mod.**
- Supports Create's **Packaging system**. In "Package Mode", the Access Port will box all requested items into a single Create Package.
- Full **Wrench support** for rotating and instantly picking up blocks.

### 💧 Fluid Transportation
- **Transport Reservoir**: A specialized item used to carry fluids between ports.
- **Transport Unpacker**: A utility block that automatically drains fluids from Reservoirs into adjacent tanks and pushes items into neighboring inventories.

### ⚙️ GregTech CEu Integration (Optional)
- **Automatic Machine Configuration**: When a `gtceu:programmed_circuit` (Programmed Circuit) passes through the **Transport Unpacker** into a neighboring GregTech machine, it automatically sets the machine's configuration to the value stored in the circuit. The circuit item is consumed in the process.
- **Circuit Generation**: In the **Access Port** settings, you can specify a **GT Circuit** number (0-24). When an order is placed, the system will automatically generate a Programmed Circuit with the correct configuration and send it through the output port along with the requested items. This simplifies the automation of complex GregTech production lines.

### 🛠 Mod Compatibility
- **JEI (Just Enough Items)**: Drag items or fluids directly from JEI into the Access Port's ghost slots to set up recipes.
- **JADE (Just Enough Details)**: Provides detailed tooltips showing the port's frequency, current recipe, and availability of required materials.

## 🌍 Supported Languages
The mod is available in the following languages:
- **English** (en_us)
- **Russian** (ru_ru)
- **Ukrainian** (uk_ua)

## Requirements
- **Minecraft Forge**
- **Create Mod** (Mandatory)
- **JEI** (Optional, recommended for recipe setup)
- **JADE** (Optional, recommended for better information display)
- **GregTech CEu Modern** (Optional, for advanced machine automation)

## Configuration
The mod includes both client-side and server-side configurations:
- **Client**: Toggle indicator rendering, rotation, and adjust render distance.
- **Server**: Configure the built-in update checker.

## 📄 License
This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

---
*Created by XanderWP*
