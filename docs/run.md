# Running project

To start project locally follow next steps.

Create application configuration files as described [here](config.md).

Run server:
```
java -Dlogging.config=<LOGGING_CONFIG_FILE> -jar prebid-server.jar --spring.config.additional-location=<APPLICATION_CONFIG_FILE>
```
where
- <LOGGING_CONFIG_FILE> - file with configuration for logger
- <APPLICATION_CONFIG_FILE> - file with prebid server configuration

The server can be reached at `http://localhost:8000`.

## Java optimizations

Feel free to optimize the command line to suit your environment:
```
-Xms4G -Xmx4G -XX:+UseParallelGC
```

## Static content configuration

To override default static content you can create ```static``` folder in place you running the server 
(Vert.x will search for this directory in current working directory as seen by java process).

Setting the option ```vertx.disableFileCPResolving``` to true disables creating cache folder, so it can be used 
only if you override static content folder because content from JAR file will not be served.

Vert.x caches files that are served from the classpath into a file on disk in a sub-directory of a directory 
called ```.vertx``` in the current working directory by default.
You can set another location with ```vertx.cacheDirBase``` key, for example:
```
-Dvertx.cacheDirBase=/tmp/prebid-server
```
