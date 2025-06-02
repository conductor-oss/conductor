# Conductor Client Spring

Provides Spring framework configurations, simplifying the use of Conductor client 
in Spring-based applications.

## Getting Started

### Prerequisites
- Java 17 or higher
- A Spring boot Project Gradle properly setup with Gradle or Maven
- A running Conductor server (local or remote)

### Using Conductor Client Spring

1. **Add `conductor-client-spring` dependency to your project**

For Gradle:
```groovy
implementation 'org.conductoross:conductor-client-spring:4.0.0'
```

For Maven:
```xml
<dependency>
    <groupId>org.conductoross</groupId>
    <artifactId>conductor-client-spring</artifactId>
    <version>4.0.0</version>
</dependency>
```

2. Add `com.netflix.conductor` to the component scan packages, e.g.:

```java
import org.springframework.boot.autoconfigure.SpringBootApplication;;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.netflix.conductor"})
public class MyApp {
    
}
```

3. Configure the client in `application.properties`

```properties
conductor.client.rootUri=http://localhost:8080/api
```

> **Note:** We are improving the Spring module to make the integration seamless. SEE: [[Java Client v4] Improve Spring module with auto-configuration](https://github.com/conductor-oss/conductor/issues/285)