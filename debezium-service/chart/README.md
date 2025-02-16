# debezium-service

![Version: 0.0.1](https://img.shields.io/badge/Version-0.0.1-informational?style=flat-square) ![Type: application](https://img.shields.io/badge/Type-application-informational?style=flat-square) ![AppVersion: 0.0.1](https://img.shields.io/badge/AppVersion-0.0.1-informational?style=flat-square)

A Helm chart for Kubernetes

## Values

| Key                                 | Type | Default | Description |
|-------------------------------------|------|---------|-------------|
| global.IS_ENABLED_APPLICATIONS      | bool | `false` |  |
| global.IS_ENABLED_PIPELINE          | bool | `false` |  |
| global.IS_ENABLED_PIPELINE_SNAPSHOT | bool | `false` |  |
| global.IS_ENABLED_PLG               | bool | `false` |  |
| global.IS_ENABLED_SSCA              | bool | `false` |  |
| global.IS_ENABLED_IDP               | bool | `false` |  |
| global.database.redis.installed     | bool | `true` |  |
| global.stackDriverLoggingEnabled    | bool | `false` |  |
| java                                | object | `{"memory":"4096m"}` | global.storageClass Global StorageClass for Persistent Volume(s) |

----------------------------------------------
Autogenerated from chart metadata using [helm-docs v1.11.3](https://github.com/norwoodj/helm-docs/releases/v1.11.3)
