# Fess Crawler Playwright

Playwright-based web crawler extension for [Fess](https://fess.codelibs.org/) that handles JavaScript-rendered pages using Chromium, Firefox, or WebKit browsers.

**Version**: 15.5.0-SNAPSHOT
**License**: Apache 2.0
**Java**: 21+

## Project Structure

```
fess-crawler-playwright/
├── pom.xml                                    # Maven project configuration
├── LICENSE                                    # Apache 2.0 license
├── README.md                                  # User documentation
├── CLAUDE.md                                  # AI assistant guide (this file)
├── .github/workflows/maven.yml                # CI/CD pipeline
│
├── src/main/java/org/codelibs/fess/crawler/client/http/
│   ├── PlaywrightClient.java                  # Main crawler implementation (~1000 lines)
│   └── PlaywrightClientCreator.java           # Client factory for URL pattern registration
│
├── src/main/resources/crawler/
│   └── client++.xml                           # LasDi/Spring configuration
│
├── src/test/java/org/codelibs/fess/crawler/
│   ├── client/http/
│   │   ├── PlaywrightClientTest.java          # Basic GET, downloads, file types
│   │   ├── PlaywrightAuthTest.java            # Basic, Digest, Form authentication
│   │   ├── PlaywrightClientProxyTest.java     # Proxy with/without auth, bypass
│   │   ├── PlaywrightClientSslIgnoreTest.java # SSL certificate handling
│   │   ├── PlaywrightClientConfigTest.java    # Browser types, load states
│   │   ├── PlaywrightClientEdgeCaseTest.java  # URL parsing, timeouts, errors
│   │   └── PlaywrightClientCreatorTest.java   # Factory registration tests
│   └── util/
│       ├── CrawlerWebServer.java              # Jetty HTTP/HTTPS test server
│       ├── CrawlerAuthenticationServer.java   # Mock auth server
│       └── CrawlerWebProxy.java               # Mock proxy server
│
└── src/test/resources/
    ├── docroot/                               # Test web content (HTML, PDF, images, etc.)
    │   └── js/                                # JavaScript resources for SPA testing
    └── sslKeystore/
        └── selfsigned_keystore.jks            # Self-signed cert for HTTPS tests
```

## Setup

```bash
# 1. Install parent POM (required dependency)
git clone https://github.com/codelibs/fess-parent.git
cd fess-parent && mvn install -Dgpg.skip=true && cd ..

# 2. Install Playwright browsers
npx playwright install --with-deps

# 3. Clone and build
git clone https://github.com/codelibs/fess-crawler-playwright.git
cd fess-crawler-playwright
mvn clean package
```

## Development Commands

```bash
mvn formatter:format              # Format code (REQUIRED before commit)
mvn test                          # Run all tests
mvn -Dtest=PlaywrightClientTest test    # Run specific test class
mvn -Dtest=PlaywrightClientTest#test_doGet test  # Run specific test method
mvn clean package -DskipTests     # Build without tests
mvn jacoco:report                 # Generate code coverage report
```

## Key Classes

### PlaywrightClient.java
Main crawler implementation extending `AbstractCrawlerClient`. Key responsibilities:

- **Browser Management**: Creates/manages Playwright instances with configurable browser type
- **Request Execution**: `execute(RequestData)` method handles HTTP requests and returns `ResponseData`
- **Authentication**: Integrates with Fess's `HcHttpClient` for session/cookie management
- **File Downloads**: Handles non-HTML content with progressive backoff polling
- **Resource Cleanup**: Thread-safe closing with configurable timeout

**Key Fields**:
```java
SHARED_WORKER          // Static shared Playwright instance (optional)
browserName            // "chromium" | "firefox" | "webkit"
renderedState          // LOAD | DOMCONTENTLOADED | NETWORKIDLE
downloadTimeout        // File download timeout (default: 15s)
closeTimeout           // Resource cleanup timeout (default: 15s)
contentWaitDuration    // Additional wait before extraction (default: 0)
```

**Key Methods**:
```java
init()                           // Initialize Playwright worker
execute(RequestData)             // Main crawl method
createPlaywrightWorker()         // Create browser instance
createAuthenticatedContext()     // Setup auth context with cookies
initNewContextOptions()          // Configure proxy/SSL options
close()                          // Cleanup resources
```

### PlaywrightClientCreator.java
Factory for registering PlaywrightClient with URL patterns in the Fess crawler framework.

```java
register(List<String> regexList, String name)  // Register URL patterns
```

## Configuration Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `browserName` | String | `chromium` | Browser: `chromium`, `firefox`, `webkit` |
| `sharedClient` | Boolean | `false` | Share Playwright worker across clients |
| `renderedState` | String | `NETWORKIDLE` | When to extract: `LOAD`, `DOMCONTENTLOADED`, `NETWORKIDLE` |
| `downloadTimeout` | Integer | `15` | Download timeout in seconds |
| `closeTimeout` | Integer | `15` | Cleanup timeout in seconds |
| `contentWaitDuration` | Long | `0` | Extra wait before extraction (ms) |
| `ignoreHttpsErrors` | Boolean | `false` | Skip SSL certificate validation |
| `proxyBypass` | String | - | Comma-separated bypass patterns |
| `http.proxyHost` | String | - | Proxy hostname |
| `http.proxyPort` | Integer | - | Proxy port |

## Testing

### Test Utilities
- **CrawlerWebServer**: Jetty-based HTTP/HTTPS server with IPv4/IPv6 dual-stack support
- **CrawlerAuthenticationServer**: Supports Basic, Digest, and Form authentication
- **CrawlerWebProxy**: Mock proxy with authentication tracking

### Test Files
Test content is in `src/test/resources/docroot/`:
- HTML pages with JavaScript rendering
- Documents: PDF, DOCX, EPUB
- Images: GIF, JPG, PNG
- Archives: ZIP
- Text: TXT, JSON

### Running Tests
```bash
# All tests
mvn test

# Specific test classes
mvn -Dtest=PlaywrightClientTest test           # Basic functionality
mvn -Dtest=PlaywrightAuthTest test             # Authentication
mvn -Dtest=PlaywrightClientProxyTest test      # Proxy handling
mvn -Dtest=PlaywrightClientSslIgnoreTest test  # SSL handling
mvn -Dtest=PlaywrightClientConfigTest test     # Browser configuration
mvn -Dtest=PlaywrightClientEdgeCaseTest test   # Edge cases
```

## Code Standards

### Formatting
- **ALWAYS** run `mvn formatter:format` before committing
- Formatting is enforced by `formatter-maven-plugin`

### License Headers
- All source files require Apache 2.0 license headers
- Enforced by `license-maven-plugin`

### Testing
- Add tests for new features
- Use existing test utilities (`CrawlerWebServer`, etc.)
- Follow existing test patterns in the codebase

### Module
- Java Module Name: `org.codelibs.fess.crawler.playwright`

## Architecture Notes

### Thread Safety
- Page access is synchronized: `synchronized(page) { ... }`
- Shared worker uses `volatile` and lock for initialization
- Background threads used for timeout-safe cleanup

### Resource Management
- Multi-stage close: Page → Context → Browser → Playwright
- Background cleanup threads prevent blocking on close
- Page reset between requests prevents state bleeding

### Download Handling
When navigation fails (non-HTML content), progressive backoff polling:
1. Start with 100ms polling interval
2. Double interval each iteration (100→200→400→500ms max)
3. Continue until `downloadTimeout` expires
4. Return downloaded file content

### Authentication Flow
1. Check for `HcHttpClient` in container with authentication
2. Make request with HcHttpClient to establish session
3. Transfer cookies from HcHttpClient to Playwright context
4. Create browser context with transferred cookies

## CI/CD

GitHub Actions workflow (`.github/workflows/maven.yml`):
1. Checkout code
2. Setup JDK 21 (Temurin)
3. Cache Maven repository
4. Install fess-parent POM
5. Install Playwright browsers
6. Build with Maven

Triggers: Push/PR to `main` and `*.x` branches

## Dependencies

**Core**:
- `fess-crawler` - Fess crawler framework
- `corelib` - CodeLibs utilities
- `playwright` - Microsoft Playwright

**Runtime**:
- `commons-pool2` - Object pooling
- `log4j-api` - Logging (provided)
- `opensearch` - Search engine (provided)

**Test**:
- `junit` - Testing framework
- `utflute-core` - Test utilities
- `jetty:6.1.26` - Test web server

## Troubleshooting

### Browser Not Found
```bash
npx playwright install --with-deps
```

### SSL Errors in Tests
Self-signed certificate in `src/test/resources/sslKeystore/selfsigned_keystore.jks`

### Memory Issues
```java
LaunchOptions options = new LaunchOptions()
    .setArgs(Arrays.asList("--no-sandbox", "--disable-dev-shm-usage"));
```

### Timeout Issues
Increase `downloadTimeout` or `closeTimeout` values.
