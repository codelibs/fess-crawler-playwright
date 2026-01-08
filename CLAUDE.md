# Fess Crawler Playwright

Playwright-based web crawler extension for [Fess](https://fess.codelibs.org/) that handles JavaScript-rendered pages using Chromium, Firefox, or WebKit browsers.

## Project Structure

```
fess-crawler-playwright/
├── pom.xml                                    # Maven project configuration
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

# 3. Build
mvn clean package
```

## Development Commands

```bash
mvn formatter:format                           # Format code (REQUIRED before commit)
mvn test                                       # Run all tests
mvn -Dtest=PlaywrightClientTest test           # Run specific test class
mvn -Dtest=PlaywrightClientTest#test_doGet test  # Run specific test method
mvn clean package -DskipTests                  # Build without tests
```

## Key Classes

### PlaywrightClient.java
Main crawler implementation extending `AbstractCrawlerClient`:
- Browser management with configurable browser type (chromium/firefox/webkit)
- Request execution via `execute(RequestData)` returning `ResponseData`
- Authentication integration with Fess's `HcHttpClient`
- File downloads with progressive backoff polling
- Thread-safe resource cleanup

### PlaywrightClientCreator.java
Factory for registering PlaywrightClient with URL patterns.

## Configuration Properties

| Property | Default | Description |
|----------|---------|-------------|
| `browserName` | `chromium` | Browser: `chromium`, `firefox`, `webkit` |
| `sharedClient` | `false` | Share Playwright worker across clients |
| `renderedState` | `NETWORKIDLE` | When to extract: `LOAD`, `DOMCONTENTLOADED`, `NETWORKIDLE` |
| `downloadTimeout` | `15` | Download timeout in seconds |
| `closeTimeout` | `15` | Cleanup timeout in seconds |
| `contentWaitDuration` | `0` | Extra wait before extraction (ms) |
| `ignoreHttpsErrors` | `false` | Skip SSL certificate validation |
| `proxyBypass` | - | Comma-separated bypass patterns |
| `http.proxyHost` | - | Proxy hostname |
| `http.proxyPort` | - | Proxy port |

## Testing

### Test Utilities
- **CrawlerWebServer**: Jetty-based HTTP/HTTPS server with IPv4/IPv6 dual-stack support
- **CrawlerAuthenticationServer**: Supports Basic, Digest, and Form authentication
- **CrawlerWebProxy**: Mock proxy with authentication tracking

### Test Resources
Test content in `src/test/resources/docroot/`: HTML, PDF, DOCX, EPUB, images (GIF/JPG/PNG), ZIP, TXT, JSON

## Code Standards

- **ALWAYS** run `mvn formatter:format` before committing
- All source files require Apache 2.0 license headers
- Add tests for new features using existing test utilities
- Java Module Name: `org.codelibs.fess.crawler.playwright`

## Architecture Notes

### Thread Safety
- Page access synchronized with `synchronized(page) { ... }`
- Shared worker uses `volatile` and lock for initialization

### Resource Management
- Multi-stage close: Page → Context → Browser → Playwright
- Background cleanup threads prevent blocking

### Download Handling
Progressive backoff polling for non-HTML content: 100ms → 200ms → 400ms → 500ms max, until `downloadTimeout` expires.

### Authentication Flow
1. Check for `HcHttpClient` with authentication
2. Make request to establish session
3. Transfer cookies to Playwright context

## Troubleshooting

- **Browser Not Found**: Run `npx playwright install --with-deps`
- **SSL Errors**: Self-signed cert at `src/test/resources/sslKeystore/selfsigned_keystore.jks`
- **Timeout Issues**: Increase `downloadTimeout` or `closeTimeout`
