# Fess Crawler Playwright - Claude Context

## Project Overview

Fess Crawler Playwright is a Playwright-based web crawler component for [Fess](https://fess.codelibs.org/) that enables crawling of JavaScript-rendered web pages. This project integrates Microsoft Playwright with the Fess crawler framework to handle modern web applications (SPAs, JavaScript-heavy sites) that require browser execution for content rendering.

## Core Architecture

### Main Components

```
src/main/java/org/codelibs/fess/crawler/client/http/
├── PlaywrightClient.java        # Main crawler client implementation
└── PlaywrightClientCreator.java # Factory for creating client instances
```

### Key Classes

- **PlaywrightClient**: Core implementation extending AbstractCrawlerClient
  - Manages Playwright browser lifecycle (initialization, execution, cleanup)
  - Handles HTTP requests and responses through Playwright
  - Supports file downloads and various content types
  - Integrates with Fess authentication system via HcHttpClient
  - Provides browser context sharing for resource efficiency

- **PlaywrightClientCreator**: Factory pattern implementation
  - Creates and configures PlaywrightClient instances
  - Manages client pooling and lifecycle

## Technology Stack

- **Java**: 21+ (uses modern Java features)
- **Build Tool**: Maven 3.x
- **Browser Automation**: Microsoft Playwright (latest)
- **Core Framework**: Fess Crawler
- **Connection Pooling**: Apache Commons Pool2
- **Search Engine**: OpenSearch (provided scope)
- **Testing**: JUnit 4 + UTFlute (DBFlute testing framework)
- **Test Server**: Jetty 6.1.26 for local testing

## Key Features

1. **Multi-Browser Support**: Chromium, Firefox, WebKit
2. **JavaScript Rendering**: Full SPA support with configurable load states
3. **Authentication**: Seamless integration with Fess auth system
4. **Proxy Configuration**: Built-in proxy support with bypass patterns
5. **SSL Flexibility**: Optional certificate validation bypass
6. **File Downloads**: Automatic handling of PDF, images, documents
7. **Resource Management**: Efficient browser context sharing and cleanup
8. **Configurable Rendering**: Control when to extract content (LOAD, DOMCONTENTLOADED, NETWORKIDLE)

## Configuration Options

| Property | Default | Description |
|----------|---------|-------------|
| `browserName` | `chromium` | Browser type: chromium, firefox, webkit |
| `sharedClient` | `false` | Enable shared Playwright worker |
| `downloadTimeout` | `15000` | Download timeout in milliseconds |
| `closeTimeout` | `15000` | Resource cleanup timeout in milliseconds |
| `renderedState` | `LOAD` | When to extract content |
| `contentWaitDuration` | `0` | Additional wait time before extraction |
| `ignoreHttpsErrors` | `false` | Skip SSL certificate validation |

## Test Structure

```
src/test/java/org/codelibs/fess/crawler/
├── client/http/
│   ├── PlaywrightClientTest.java           # Core functionality tests
│   ├── PlaywrightAuthTest.java             # Authentication integration tests
│   ├── PlaywrightClientProxyTest.java      # Proxy configuration tests
│   ├── PlaywrightClientSslIgnoreTest.java  # SSL handling tests
│   ├── PlaywrightClientConfigTest.java     # Configuration tests
│   ├── PlaywrightClientEdgeCaseTest.java   # Edge case handling
│   └── PlaywrightClientCreatorTest.java    # Factory tests
└── util/
    ├── CrawlerWebServer.java               # Test web server
    ├── CrawlerWebProxy.java                # Test proxy server
    └── CrawlerAuthenticationServer.java    # Test auth server
```

Test resources:
- `src/test/resources/docroot/`: HTML, PDF, image files for testing
- `src/test/resources/sslKeystore/`: SSL certificates for HTTPS testing

## Build and Development

### Prerequisites
```bash
# Install Fess parent POM
git clone https://github.com/codelibs/fess-parent.git
cd fess-parent
mvn install -Dgpg.skip=true

# Install Playwright browsers
npx playwright install --with-deps
```

### Common Commands
```bash
# Build
mvn clean package

# Run tests
mvn test

# Run specific test
mvn -Dtest=PlaywrightClientTest test

# Format code (required before commits)
mvn formatter:format

# Skip tests
mvn clean package -DskipTests
```

### Maven Plugins
- **formatter-maven-plugin**: Code formatting enforcement
- **jacoco-maven-plugin**: Code coverage
- **license-maven-plugin**: License header management
- **maven-surefire-plugin**: Test execution
- **maven-javadoc-plugin**: JavaDoc generation

## Code Standards

1. **Formatting**: Enforced by formatter-maven-plugin (run `mvn formatter:format`)
2. **License Headers**: All Java files must have Apache 2.0 license header
3. **Testing**: Comprehensive tests required for new features
4. **JavaDoc**: Required for public APIs
5. **Module Name**: `org.codelibs.fess.crawler.playwright` (JPMS)

## Common Patterns

### Client Initialization
```java
PlaywrightClient client = new PlaywrightClient();
client.setBrowserName("chromium");
client.setRenderedState(LoadState.NETWORKIDLE);
client.init();
```

### Request Execution
```java
RequestData requestData = RequestDataBuilder.newRequestData()
    .get()
    .url("https://example.com")
    .build();
ResponseData responseData = client.execute(requestData);
```

### Resource Cleanup
```java
client.close(); // Closes browser context and resources
```

## Integration Points

### Fess Framework
- Extends `AbstractCrawlerClient` from fess-crawler
- Uses `ResponseData` and `RequestData` entities
- Integrates with `CrawlerContext` for configuration
- Uses `MimeTypeHelper` for content type detection
- Leverages `UrlFilter` for URL filtering

### Authentication
- Integrates with Fess's `HcHttpClient` for authentication
- Supports username/password credentials
- Can pass authentication cookies to Playwright context

### Dependency Injection
- Uses Jakarta annotations (`@Resource`)
- Configured via Spring XML: `src/main/resources/crawler/client++.xml`

## Important Files

- `pom.xml`: Maven configuration, dependencies, plugins
- `README.md`: User documentation
- `LICENSE`: Apache License 2.0
- `.github/workflows/maven.yml`: CI/CD pipeline
- `src/main/resources/crawler/client++.xml`: Spring configuration

## Development Workflow

1. Make changes to source code
2. Run `mvn formatter:format` to format code
3. Add/update tests for new functionality
4. Run `mvn test` to verify all tests pass
5. Check code coverage with JaCoCo report
6. Commit with descriptive message
7. Push and create PR

## Debugging Tips

1. **Enable Playwright debug logs**: Set system property or environment variable
2. **Use headful mode**: `LaunchOptions.setHeadless(false)` for visual debugging
3. **Increase timeouts**: Adjust `downloadTimeout` and `closeTimeout` for slow environments
4. **Check browser installation**: Verify Playwright browsers are installed
5. **SSL issues**: Use `ignoreHttpsErrors` option for testing (not production)

## Known Limitations

1. Requires Playwright browsers to be installed separately
2. Headless mode required for CI/CD environments
3. Memory overhead due to browser instances
4. Not suitable for simple static HTML crawling (use HcHttpClient instead)

## Version Information

- Current version: `15.4.0-SNAPSHOT`
- Parent POM: `fess-parent:15.4.0-SNAPSHOT`
- Java compatibility: 21+
- Playwright version: Managed by parent POM

## Contributing Notes

- Follow existing code style (enforced by formatter)
- Add comprehensive test coverage
- Update documentation for public APIs
- Ensure backward compatibility
- Add issue reference in commit messages
