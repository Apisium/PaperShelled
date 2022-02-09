# PaperShelled [![](https://www.jitpack.io/v/Apisium/PaperShelled.svg)](https://www.jitpack.io/#Apisium/PaperShelled) [![Release](https://github.com/Apisium/PaperShelled/actions/workflows/release.yml/badge.svg)](https://github.com/Apisium/PaperShelled/actions/workflows/release.yml)

A Spigot mixin development framework.

## Usage

1. Download the [PaperShelled.jar](https://github.com/Apisium/PaperShelled/releases/latest)
2. Put the downloaded jar file into your MineCraft server root directory.

For 1.18-:

3. Start your MineCraft server with the following command line parameters:

```bash
java -javaagent:PaperShelled.jar -jar server_core.jar
```

4. Then you can put your plugins in the `PaperShelled/plugins` directory and enjoy it!

For 1.18 and later:
3. Start the server with the following command line arguments first:

```bash
java -jar PaperShelled.jar
```

4. A papershelled.properties should be generated in the server root directory.<br/>
The options are as follows:
   
   1. server.jar: Path to the server jar you got.
   2. server.java.jvmargs: JVM arguments, referring to those starts with - or --.Separated by a backspace.
   3. server.java.path: Path to java executable. On Windows it can be just 'java' with environment variables set properly.
   4. server.java.args: (Optional)Extra arguments for server.For example 'nogui' can be added here.Separated by a backspace.

5. Start the server again with the command.

```bash
java -jar PaperShelled.jar
```

6. Then you can put your plugins in the `PaperShelled/plugins` directory and enjoy it!
## For plugin developers

See also: https://github.com/Apisium/PaperShelledPluginTemplate

## License

[GPLv3](./LICENSE)

## Author

Shirasawa
