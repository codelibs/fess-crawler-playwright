# Fess Crawler with Playwright

[![Java CI with Maven](https://github.com/codelibs/fess-crawler-playwright/actions/workflows/maven.yml/badge.svg)](https://github.com/codelibs/fess-crawler-playwright/actions/workflows/maven.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/org.codelibs.fess/fess-crawler-playwright.svg)](https://search.maven.org/artifact/org.codelibs.fess/fess-crawler-playwright)

A Playwright-based web crawler component for [Fess](https://fess.codelibs.org/) that enables JavaScript-rendered web page crawling capabilities. This extension integrates Microsoft Playwright with the Fess crawler framework to handle modern web applications that require JavaScript execution for content rendering.

## Key Features

- **Multi-browser Support**: Compatible with Chromium, Firefox, and WebKit browsers
- **JavaScript Rendering**: Full support for SPAs and JavaScript-heavy websites
- **Authentication Integration**: Seamless integration with Fess's authentication system
- **Proxy Configuration**: Built-in proxy support with bypass patterns
- **SSL Flexibility**: Option to ignore SSL certificate validation for testing
- **File Downloads**: Handles various content types including PDF, images, documents
- **Resource Management**: Efficient browser context sharing and cleanup
- **Configurable Rendering States**: Control when to extract content (load, DOMContentLoaded, networkidle)

## Technology Stack

- **Java**: 21+
- **Maven**: 3.x
- **Microsoft Playwright**: Latest version for browser automation
- **Fess Crawler**: Core crawler framework
- **Apache Commons Pool**: Connection pooling
- **OpenSearch**: Search engine integration (provided scope)
- **JUnit + UTFlute**: Testing framework

## Prerequisites

- Java 21 or higher
- Maven 3.x
- Node.js and npm (for Playwright browser installation)
- Fess parent POM dependency

## Installation

### 1. Install Fess Parent POM

First, install the required parent POM dependency:

```bash
git clone https://github.com/codelibs/fess-parent.git
cd fess-parent
mvn install -Dgpg.skip=true
```

### 2. Install Playwright Browsers

Install the required browser binaries:

```bash
npx playwright install --with-deps
```

### 3. Build the Project

Clone and build the project:

```bash
git clone https://github.com/codelibs/fess-crawler-playwright.git
cd fess-crawler-playwright
mvn clean package
```

## Quick Start

### Basic Usage

```java
import org.codelibs.fess.crawler.client.http.PlaywrightClient;
import org.codelibs.fess.crawler.client.http.PlaywrightClientCreator;

// Create and configure the client
PlaywrightClient client = new PlaywrightClient();
client.setBrowserName("chromium"); // or "firefox", "webkit"
client.init();

// Use with Fess crawler
RequestData requestData = RequestDataBuilder.newRequestData()
    .get()
    .url("https://example.com")
    .build();

ResponseData responseData = client.execute(requestData);
```

### Maven Dependency

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>org.codelibs.fess</groupId>
    <artifactId>fess-crawler-playwright</artifactId>
    <version>15.2.0-SNAPSHOT</version>
</dependency>
```

## Configuration

### Browser Configuration

```java
PlaywrightClient client = new PlaywrightClient();

// Set browser type
client.setBrowserName("chromium"); // chromium, firefox, webkit

// Configure launch options
LaunchOptions launchOptions = new LaunchOptions()
    .setHeadless(true)
    .setTimeout(30000);
client.setLaunchOptions(launchOptions);

// Set rendering state
client.setRenderedState(LoadState.NETWORKIDLE);

// Configure timeouts
client.setDownloadTimeout(15000); // 15 seconds
client.setCloseTimeout(15000);    // 15 seconds
```

### Authentication Setup

```java
// The client automatically integrates with Fess's HcHttpClient for authentication
// Configure authentication in your Fess crawler configuration
NewContextOptions contextOptions = new NewContextOptions()
    .setUserAgent("CustomUserAgent/1.0")
    .setExtraHTTPHeaders(Map.of("Authorization", "Bearer token"));

client.setNewContextOptions(contextOptions);
```

### Proxy Configuration

```java
// Set proxy through system properties
System.setProperty("http.proxyHost", "proxy.example.com");
System.setProperty("http.proxyPort", "8080");
System.setProperty("fess.crawler.playwright.proxy.bypass", "*.local,127.0.0.1");

// Or configure directly
client.addOption("proxyHost", "proxy.example.com");
client.addOption("proxyPort", "8080");
```

### SSL Configuration

```java
// Ignore SSL certificate errors for testing
client.addOption("ignoreHttpsErrors", "true");
```

## Common Use Cases

### Crawling Single Page Applications (SPAs)

```java
PlaywrightClient client = new PlaywrightClient();
client.setBrowserName("chromium");
client.setRenderedState(LoadState.NETWORKIDLE); // Wait for network to be idle
client.init();

RequestData requestData = RequestDataBuilder.newRequestData()
    .get()
    .url("https://spa-example.com")
    .build();

ResponseData responseData = client.execute(requestData);
String content = new String(responseData.getResponseBody(), responseData.getCharSet());
```

### Handling File Downloads

```java
PlaywrightClient client = new PlaywrightClient();
client.setBrowserName("chromium");
client.setDownloadTimeout(30000); // 30 seconds for large files
client.init();

// The client automatically handles downloads for PDF, images, documents, etc.
RequestData requestData = RequestDataBuilder.newRequestData()
    .get()
    .url("https://example.com/document.pdf")
    .build();

ResponseData responseData = client.execute(requestData);
// File content is available in responseData.getResponseBody()
```

## Development

### Project Structure

```
src/
├── main/java/org/codelibs/fess/crawler/client/http/
│   ├── PlaywrightClient.java        # Main crawler client implementation
│   └── PlaywrightClientCreator.java # Factory for creating client instances
├── main/resources/crawler/
│   └── client++.xml                 # Spring configuration
└── test/
    ├── java/org/codelibs/fess/crawler/client/http/
    │   ├── PlaywrightClientTest.java         # Basic functionality tests
    │   ├── PlaywrightAuthTest.java           # Authentication tests
    │   ├── PlaywrightClientProxyTest.java    # Proxy configuration tests
    │   └── PlaywrightClientSslIgnoreTest.java # SSL tests
    └── resources/
        ├── docroot/                  # Test web content
        └── sslKeystore/             # SSL certificates for testing
```

### Building and Testing

```bash
# Full build with tests
mvn clean package

# Run specific test
mvn -Dtest=PlaywrightClientTest test

# Skip tests
mvn clean package -DskipTests

# Format code
mvn net.revelc.code.formatter:formatter-maven-plugin:format
```

### Running Tests

The test suite includes comprehensive tests for:

- **Basic functionality**: HTML, PDF, image crawling
- **Authentication**: Integration with Fess auth system
- **Proxy configuration**: Proxy server and bypass patterns
- **SSL handling**: Certificate validation and ignoring
- **File downloads**: Various content types
- **Error handling**: Network failures and timeouts

Tests use a local Jetty server (`CrawlerWebServer`) with test content in `src/test/resources/docroot/`.

### Configuration Options Reference

| Property | Default | Description |
|----------|---------|-------------|
| `browserName` | `chromium` | Browser type: chromium, firefox, webkit |
| `sharedClient` | `false` | Enable shared Playwright worker |
| `downloadTimeout` | `15000` | Download timeout in milliseconds |
| `closeTimeout` | `15000` | Resource cleanup timeout in milliseconds |
| `renderedState` | `LOAD` | When to extract content: LOAD, DOMCONTENTLOADED, NETWORKIDLE |
| `contentWaitDuration` | `0` | Additional wait time before content extraction |
| `ignoreHttpsErrors` | `false` | Skip SSL certificate validation |

## Troubleshooting

### Common Issues

**Browser installation fails**
```bash
# Ensure Node.js is installed and run:
npx playwright install --with-deps
```

**Tests fail with "Browser not found"**
- Verify Playwright browsers are installed
- Check if running in headless environment (CI/CD)

**SSL certificate errors**
```java
// For testing only - ignore SSL errors
client.addOption("ignoreHttpsErrors", "true");
```

**Memory issues with large sites**
```java
// Reduce resource usage
LaunchOptions options = new LaunchOptions()
    .setArgs(Arrays.asList("--no-sandbox", "--disable-dev-shm-usage"));
client.setLaunchOptions(options);
```

**Proxy authentication**
```java
// Configure proxy with authentication
NewContextOptions contextOptions = new NewContextOptions()
    .setProxy(new Proxy("proxy.example.com:8080")
        .setUsername("user")
        .setPassword("pass"));
client.setNewContextOptions(contextOptions);
```

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Make changes and add tests
4. Format code: `mvn formatter:format`
5. Run tests: `mvn test`
6. Commit changes: `git commit -am 'Add new feature'`
7. Push branch: `git push origin feature/my-feature`
8. Submit a Pull Request

### Code Standards

- Follow existing code formatting (enforced by formatter-maven-plugin)
- Add comprehensive tests for new features
- Update documentation for public APIs
- Ensure all tests pass before submitting PR

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

