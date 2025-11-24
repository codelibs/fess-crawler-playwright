# Fess Crawler Playwright

Playwright-based web crawler for [Fess](https://fess.codelibs.org/) that handles JavaScript-rendered pages using Chromium/Firefox/WebKit browsers.

## Project Structure

```
src/main/java/org/codelibs/fess/crawler/client/http/
├── PlaywrightClient.java        # Main crawler implementation
└── PlaywrightClientCreator.java # Client factory

src/test/java/org/codelibs/fess/crawler/client/http/
├── PlaywrightClient*Test.java   # Test suites
└── util/Crawler*.java           # Test servers
```

## Setup

```bash
# Install parent POM
git clone https://github.com/codelibs/fess-parent.git
cd fess-parent && mvn install -Dgpg.skip=true

# Install Playwright browsers
npx playwright install --with-deps

# Build project
mvn clean package
```

## Development Commands

```bash
mvn formatter:format              # Format code (required before commit)
mvn test                          # Run all tests
mvn -Dtest=ClassName test         # Run specific test
mvn clean package -DskipTests     # Build without tests
```

## Key Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `browserName` | `chromium` | Browser: chromium/firefox/webkit |
| `renderedState` | `LOAD` | Extract timing: LOAD/DOMCONTENTLOADED/NETWORKIDLE |
| `downloadTimeout` | `15000` | Download timeout (ms) |
| `ignoreHttpsErrors` | `false` | Skip SSL validation |

## Tech Stack

- Java 21+, Maven 3.x
- Microsoft Playwright (browser automation)
- Fess Crawler (core framework)
- JUnit 4 + UTFlute (testing)

## Code Standards

- Run `mvn formatter:format` before commits
- Apache 2.0 license headers required
- Add tests for new features
- Module: `org.codelibs.fess.crawler.playwright`
