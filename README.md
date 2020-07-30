# SPlugin

> SPlugin is a lightweight spigot plugin designed for a basic server infrastructure. Users can define groups of users, lock chests/hoppers/anvil... and everything is configurable to the user.

### Building yourself

Simply clone this repository and add the following to `de.sean.splugin:SToken.java`:

```java
public class SToken {
    protected static final TOKEN = "YOUR TOKEN HERE";
}
```

Then use `gradlew build` to build your JAR into `./build/libs/`.
JDA automatically gets shadowed which means you won't need to put the JAR anywhere you use it.