Object trace is a way for each object to be traced through the framework by an external system.
The basic tracing is for when an object start's processing and when an object complete output.
But specific places can be enabled to output when an object completes that places processing.

It is enabled by setting the env variable ObjectTrace=true for the system.
When running local it can be set by: `ObjectTrace=true ./emissary server`

The output is json lines with an object ID and the processing stage. For example:
```json
{"@timestamp":"2023-05-10T01:07:59.667Z","STAGE":"StartProcessing","ID":"emissary-knight.png","node":"localhost"}
{"@timestamp":"2023-05-10T01:08:01.295Z","STAGE":"CompletedUnixFilePlace","ID":null,"node":"localhost"}
{"@timestamp":"2023-05-10T01:08:01.311Z","STAGE":"DropOffComplete","ID":null,"node":"localhost"}
```

File's are rolled by logback every 15 min using `emissary.util.FifteenMinuteLogbackAppender`
TODO: In the future that appender should take a configuration to set the roll interval

Once the file is rolled a follow on microservice can push the output to the downstream system