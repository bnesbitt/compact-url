package com.brian;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class ServerPropertiesTest {

    @Test()
    void propertiesFileMissing() {
        assertThrows(InvalidServerPropertiesException.class,
                ()-> new ServerProperties("asdfhasjdfhjkaskdfhjasjkdfhjkahsdf.props"));
    }

    @Test()
    void propertiesFileIsEmpty() {
        assertThrows(InvalidServerPropertiesException.class,
                ()-> new ServerProperties("empty.properties"));
    }

    @Test
    void validProperties() throws IOException {
        var serverProps = new ServerProperties("server.properties");
        assertEquals(8888, serverProps.getPort());
        assertEquals("shorty.com", serverProps.getDomain());
        assertEquals(60, serverProps.getCacheTTL());
    }

    @Test()
    void portMissing() {
        assertThrows(InvalidServerPropertiesException.class,
                ()-> new ServerProperties("port-missing.properties"));
    }

    @Test()
    void domainMissing() {
        assertThrows(InvalidServerPropertiesException.class,
                ()-> new ServerProperties("domain-missing.properties"));
    }

    @Test()
    void ttlMissing() {
        assertThrows(InvalidServerPropertiesException.class,
                ()-> new ServerProperties("ttl-missing.properties"));
    }

}
