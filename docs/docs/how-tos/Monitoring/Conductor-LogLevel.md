# Conductor Log Level

Conductor is based on Spring Boot, so the log levels are set via [Spring Boot properties](https://docs.spring.io/spring-boot/docs/2.1.13.RELEASE/reference/html/boot-features-logging.html):

From the Spring Boot Docs:


> All the supported logging systems can have the logger levels set in the Spring Environment (for example, in application.properties) by using ```logging.level.<logger-name>=<level>``` where level is one of TRACE, DEBUG, INFO, WARN, ERROR, FATAL, or OFF. The ```root``` logger can be configured by using logging.level.root.

> The following example shows potential logging settings in ```application.properties```:

```
logging.level.root=warn
logging.level.org.springframework.web=debug
logging.level.org.hibernate=error
```

It’s also possible to set logging levels using environment variables. For example, ```LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB=DEBUG``` will set ```org.springframework.web``` to `DEBUG`.
