# Test plan: wrong-method → 405 (Issue #1393)

Supporting doc for
[scheduler-method-not-allowed-architecture.md](scheduler-method-not-allowed-architecture.md).
Names and types match that document verbatim.

## 1. Goal

Prove that `HttpRequestMethodNotSupportedException` yields `405 Method Not
Allowed` (not `500`), carries the `Allow` header, and returns a well-formed
`ErrorResponse`. This is the server-side condition the Java client's GET-then-PUT
fallback depends on.

## 2. Unit test — `ApplicationExceptionMapperTest`

New file:
`rest/src/test/java/com/netflix/conductor/rest/controllers/ApplicationExceptionMapperTest.java`

Follows the repo testing conventions: real implementation under test (no mock of
the mapper itself), a stub `HttpServletRequest` (Spring's
`MockHttpServletRequest`), and direct assertions on the returned
`ResponseEntity<ErrorResponse>`.

### Cases

| # | Input | Expected |
|---|---|---|
| 1 | `handleMethodNotSupported(req, new HttpRequestMethodNotSupportedException("GET", List.of("PUT")))` | status `405`; body `status == 405`; `retryable == false`; `Allow` header contains `PUT` |
| 2 | Same, message assertion | body `message` equals the exception message (`Request method 'GET' is not supported` form produced by Spring) |
| 3 | `handleMethodNotSupported(req, new HttpRequestMethodNotSupportedException("GET"))` (no supported methods) | status `405`; **no** `Allow` header set; body still well-formed |
| 4 | Regression: `handleAll(req, new RuntimeException("boom"))` | status `500` (unchanged — catch-all still defaults correctly) |

### Sketch

```java
class ApplicationExceptionMapperTest {

    private final ApplicationExceptionMapper mapper = new ApplicationExceptionMapper();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @Test
    void methodNotSupportedMapsTo405WithAllowHeader() {
        var ex = new HttpRequestMethodNotSupportedException("GET", List.of("PUT"));

        ResponseEntity<ErrorResponse> response =
                mapper.handleMethodNotSupported(request, ex);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertEquals(405, response.getBody().getStatus());
        assertFalse(response.getBody().isRetryable());
        assertTrue(response.getHeaders().getAllow().contains(HttpMethod.PUT));
    }

    @Test
    void methodNotSupportedWithoutSupportedMethodsOmitsAllowHeader() {
        var ex = new HttpRequestMethodNotSupportedException("GET");

        ResponseEntity<ErrorResponse> response =
                mapper.handleMethodNotSupported(request, ex);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNull(response.getHeaders().getFirst(HttpHeaders.ALLOW));
    }

    @Test
    void unmappedExceptionStillMapsTo500() {
        ResponseEntity<ErrorResponse> response =
                mapper.handleAll(request, new RuntimeException("boom"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }
}
```

## 3. Optional MVC slice check

If a `@WebMvcTest`-style slice already exists for a controller in the `rest`
module, a `GET` against a `PUT`-only route should assert `405` end-to-end
(exercising Spring's dispatch → `HttpRequestMethodNotSupportedException` →
`handleMethodNotSupported`). This is optional; the unit test above is the
authoritative coverage for the change.

## 4. Manual end-to-end (from the issue repro)

```bash
cd java-sdk
export CONDUCTOR_SERVER_URL=http://localhost:8080/api \
       CONDUCTOR_AGENT_LLM_MODEL=anthropic/claude-sonnet-4-6 \
       AGENT_SECONDARY_LLM_MODEL=anthropic/claude-sonnet-4-6
./gradlew :agent-examples:run -PmainClass=org.conductoross.conductor.ai.examples.Example99ScheduledAgent
```

Expected after the fix: the lifecycle reaches **pause → resume → preview →
delete** without the `Request method 'GET' is not supported {status=500}`
exception. A direct probe also confirms it:

```bash
curl -i -X GET http://localhost:8080/api/scheduler/schedules/eng_digest_99/pause
# HTTP/1.1 405
# Allow: PUT
```

## 5. Commands

```
./gradlew spotlessApply
./gradlew :rest:test
```

Both must pass before the change is considered complete.
</content>
