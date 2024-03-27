package com.brian;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;

/**
 * Load the server.properties file and extracts the following properties:
 * - port number
 * - domain name
 */
public class ServerProperties {
    private static final String PROPERTIES_FILE = "server.properties";

    private static final Logger logger = LoggerFactory.getLogger(ServerProperties.class);

    private final int port;
    private final String domain;
    private final int cacheTTL;

    public ServerProperties() throws IOException, InvalidServerPropertiesException {
        try (var input = HttpServer.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (null == input) {
                logger.error("Failed to load the properties file {}", PROPERTIES_FILE);
                throw new InvalidServerPropertiesException("Failed to load the properties file " + PROPERTIES_FILE);
            }

            var serverProps = new Properties();
            serverProps.load(input);

            var portStr = serverProps.getProperty("port");
            if (portStr == null || portStr.isBlank()) {
                throw new InvalidServerPropertiesException("The port number is not defined in the properties file "
                        + PROPERTIES_FILE);
            }

            try {
                port = Integer.parseInt(portStr.trim());
            } catch (NumberFormatException e) {
                throw new InvalidServerPropertiesException("The port number defined in the properties file "
                        + PROPERTIES_FILE + " is not a valid integer [" + portStr + "]");
            }

            domain = serverProps.getProperty("domain");
            if (null == domain || domain.isBlank()) {
                throw new InvalidServerPropertiesException("The domain name is not defined in the properties file "
                        + PROPERTIES_FILE);
            }

            var ttlStr = serverProps.getProperty("cache.ttl");
            if (ttlStr == null || ttlStr.isBlank()) {
                throw new InvalidServerPropertiesException("The cache TTL is not defined in the properties file "
                        + PROPERTIES_FILE);
            }

            try {
                cacheTTL = Integer.parseInt(ttlStr.trim());
            } catch (NumberFormatException e) {
                throw new InvalidServerPropertiesException("The cache TTL defined in the properties file "
                        + PROPERTIES_FILE + " is not a valid integer [" + portStr + "]");
            }
        }
    }

    public int getPort() {
        return port;
    }

    public String getDomain() {
        return domain;
    }

    public int getCacheTTL() {
        return cacheTTL;
    }

}
