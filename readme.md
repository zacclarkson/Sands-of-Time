# Sands of Time
Sands of Time is a fan-made Minecraft plugin inspired by the MCC (Minecraft Championship) game mode. It allows players to explore procedurally generated dungeons, interact with custom mechanics, and enjoy a unique gameplay experience.

## Features

- **Procedural Dungeon Generation**: Generate dungeons dynamically with various room types, corridors, and stairs.
- **Custom Doors**: Clone and interact with doors using specific items (e.g., slimeballs as keys).
- **Floor Items**: Spawn collectible items on the ground, such as coins or other custom items.
- **Segment-Based Design**: Modular dungeon segments with entry points for flexible dungeon layouts.
- **Custom Commands**:
    - `/generatedungeon`: Generates a dungeon at the player's current location.
    - `/cloneDoor`: Clones a door to the player's location.

## Installation

1. Clone the repository or download the source code.
2. Build the project using Maven:
     ```bash
     mvn clean package
     ```
3. Place the generated `.jar` file in your Minecraft server's `plugins` folder.
4. Start the server to load the plugin.

## Commands

| Command           | Description                                      | Usage               |
|-------------------|--------------------------------------------------|---------------------|
| `/generatedungeon`| Generates a dungeon at the player's location.    | `/generatedungeon`  |
| `/cloneDoor`      | Clones a door to the player's current location.  | `/cloneDoor`        |

## Dependencies

- [Paper API](https://papermc.io/) (1.20.1 or higher)
- [WorldEdit](https://enginehub.org/worldedit/) (7.2.5 or higher)

## Development

### Prerequisites

- Java 8 or higher
- Maven
- A Minecraft server running Paper 1.20.1 or higher

### Building the Project

1. Clone the repository:
     ```bash
     git clone <repository-url>
     ```
2. Navigate to the project directory:
     ```bash
     cd Sands-of-Time
     ```
3. Build the project:
     ```bash
     mvn clean package
     ```

### Project Structure

- `src/main/java/com/clarkson/sot`: Contains the main plugin code.
- `src/main/resources`: Contains the `plugin.yml` file for plugin configuration.
- `.idea`: IntelliJ IDEA project files.
- `pom.xml`: Maven configuration file.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Author

Created by Zac Clarkson.  
