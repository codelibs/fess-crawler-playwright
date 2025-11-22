/*
 * Copyright 2012-2025 CodeLibs Project and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.codelibs.fess.crawler.util;

import java.io.File;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.core.io.FileUtil;
import org.codelibs.core.io.ResourceUtil;
import org.codelibs.fess.crawler.exception.CrawlerSystemException;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.DefaultHandler;
import org.mortbay.jetty.handler.HandlerList;
import org.mortbay.jetty.handler.ResourceHandler;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.jetty.security.SslSocketConnector;
import org.mortbay.log.Log;

/**
 * @author shinsuke
 *
 */
public class CrawlerWebServer {
    private static final Logger logger = LogManager.getLogger(CrawlerWebServer.class);

    private int port = 8080;

    private final File docRoot;

    private final Server server;

    private boolean tempDocRoot = false;

    public CrawlerWebServer(final int port) {
        this(port, createDocRoot(3));
        tempDocRoot = true;
    }

    public CrawlerWebServer(final int port, final File docRoot) {
        this.port = port;
        this.docRoot = docRoot;

        if (logger.isDebugEnabled()) {
            logger.debug("Initializing CrawlerWebServer on port {} with document root: {}", port, docRoot.getAbsolutePath());
        }

        server = new Server();

        // Bind to all interfaces (dual-stack: IPv4 and IPv6)
        final SelectChannelConnector connector = new SelectChannelConnector();
        connector.setPort(port);
        server.addConnector(connector);

        final ResourceHandler resource_handler = new ResourceHandler();
        resource_handler.setWelcomeFiles(new String[] { "index.html" });
        resource_handler.setResourceBase(docRoot.getAbsolutePath());
        Log.info("serving " + resource_handler.getBaseResource());
        final HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { resource_handler, new DefaultHandler() });
        server.setHandler(handlers);

        if (logger.isDebugEnabled()) {
            logger.debug("CrawlerWebServer initialized successfully");
        }
    }

    /**
     * Constructor to start the web server with TLS enabled
     */
    public CrawlerWebServer(final int port, final File docRoot, final boolean useSelfSignedTls) {
        this(port, docRoot);

        if (useSelfSignedTls) {
            if (logger.isDebugEnabled()) {
                logger.debug("Configuring TLS with self-signed certificate for port {}", port);
            }

            final File certFile = ResourceUtil.getResourceAsFile("sslKeystore/selfsigned_keystore.jks");
            final String certFilePath = certFile.getAbsolutePath();

            if (logger.isDebugEnabled()) {
                logger.debug("Using keystore file: {}", certFilePath);
            }

            // Ssl handler - dual-stack support (IPv4 and IPv6)
            final SslSocketConnector sslSocketConnector = new SslSocketConnector();
            sslSocketConnector.setKeystore(certFilePath);
            sslSocketConnector.setTruststore(certFilePath);
            sslSocketConnector.setPassword("password");
            sslSocketConnector.setKeyPassword("password");
            sslSocketConnector.setTrustPassword("password");
            sslSocketConnector.setPort(port);

            server.setConnectors(ArrayUtils.toArray(sslSocketConnector));

            if (logger.isDebugEnabled()) {
                logger.debug("TLS configuration completed");
            }
        }
    }

    public void start() {
        try {
            if (logger.isInfoEnabled()) {
                logger.info("Starting CrawlerWebServer on port {}", port);
            }
            server.start();
            if (logger.isInfoEnabled()) {
                logger.info("CrawlerWebServer started successfully on port {}", port);
            }
        } catch (final Exception e) {
            logger.warn("Failed to start CrawlerWebServer on port {}", port, e);
            throw new CrawlerSystemException(e);
        }
    }

    public void stop() {
        try {
            if (logger.isInfoEnabled()) {
                logger.info("Stopping CrawlerWebServer on port {}", port);
            }
            server.stop();
            server.join();
            if (logger.isInfoEnabled()) {
                logger.info("CrawlerWebServer stopped successfully on port {}", port);
            }
        } catch (final Exception e) {
            logger.warn("Failed to stop CrawlerWebServer on port {}", port, e);
            throw new CrawlerSystemException(e);
        } finally {
            if (tempDocRoot) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Deleting temporary document root: {}", docRoot.getAbsolutePath());
                }
                docRoot.delete();
            }
        }
    }

    protected static File createDocRoot(final int count) {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Creating temporary document root with content depth: {}", count);
            }

            final File tempDir = File.createTempFile("crawlerDocRoot", "");
            tempDir.delete();
            tempDir.mkdirs();

            // robots.txt
            StringBuilder buf = new StringBuilder();
            buf.append("User-agent: *").append('\n');
            buf.append("Disallow: /admin/").append('\n');
            buf.append("Disallow: /websvn/").append('\n');
            final File robotTxtFile = new File(tempDir, "robots.txt");
            FileUtil.writeBytes(robotTxtFile.getAbsolutePath(), buf.toString().getBytes("UTF-8"));
            robotTxtFile.deleteOnExit();

            // sitemaps.xml
            buf = new StringBuilder();
            buf.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>").append('\n');
            buf.append("<urlset ").append("xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">").append('\n');
            buf.append("<url>").append('\n');
            buf.append("<loc>http://[::1]:7070/index.html</loc>").append('\n');
            buf.append("<loc>http://[::1]:7070/file").append(count).append("-1.html").append("</loc>").append('\n');
            buf.append("</url>").append('\n');
            buf.append("</urlset>").append('\n');
            File sitemapsFile = new File(tempDir, "sitemaps.xml");
            FileUtil.writeBytes(sitemapsFile.getAbsolutePath(), buf.toString().getBytes("UTF-8"));
            robotTxtFile.deleteOnExit();

            // sitemaps.txt
            buf = new StringBuilder();
            buf.append("http://[::1]:7070/index.html").append('\n');
            buf.append("http://[::1]:7070/file").append(count).append("-1.html").append('\n');
            sitemapsFile = new File(tempDir, "sitemaps.txt");
            FileUtil.writeBytes(sitemapsFile.getAbsolutePath(), buf.toString().getBytes("UTF-8"));
            robotTxtFile.deleteOnExit();

            generateContents(tempDir, count);

            if (logger.isDebugEnabled()) {
                logger.debug("Temporary document root created successfully: {}", tempDir.getAbsolutePath());
            }

            return tempDir;
        } catch (final Exception e) {
            logger.warn("Failed to create temporary document root", e);
            throw new CrawlerSystemException(e);
        }
    }

    private static void generateContents(final File dir, final int count) throws Exception {
        if (count <= 0) {
            return;
        }

        final String content = getHtmlContent(count);

        final File indexFile = new File(dir, "index.html");
        indexFile.deleteOnExit();
        FileUtil.writeBytes(indexFile.getAbsolutePath(), content.getBytes("UTF-8"));

        for (int i = 1; i <= 10; i++) {
            final File file = new File(dir, "file" + count + "-" + i + ".html");
            file.deleteOnExit();
            FileUtil.writeBytes(file.getAbsolutePath(), content.getBytes("UTF-8"));
            final File childDir = new File(dir, "dir" + count + "-" + i);
            childDir.mkdirs();
            generateContents(childDir, count - 1);
        }
    }

    private static String getHtmlContent(final int count) {
        final StringBuilder buf = new StringBuilder();
        buf.append("<html><head><title>Title ");
        buf.append(count);
        buf.append("</title></head><body><h1>Content ");
        buf.append(count);
        buf.append("</h1><br>");
        buf.append("<a href=\"index.html\">Index</a><br>");
        for (int i = 1; i <= 10; i++) {
            buf.append("<a href=\"file");
            buf.append(count);
            buf.append("-");
            buf.append(i);
            buf.append(".html\">File ");
            buf.append(count);
            buf.append("-");
            buf.append(i);
            buf.append("</a><br>");
            buf.append("<a href=\"dir");
            buf.append(count);
            buf.append("-");
            buf.append(i);
            buf.append("/index.html\">Dir ");
            buf.append(count);
            buf.append("-");
            buf.append(i);
            buf.append("</a><br>");
        }
        buf.append("</body></html>");
        return buf.toString();
    }

    public int getPort() {
        return port;
    }
}
