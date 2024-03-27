# A URL shortening service

## Building
This requires JDK 17, and uses the provided Maven wrapper:

```shell
./mvnw clean install
```

## Running
This can be run from the command line as follows:
```shell
java -jar target/compact-url-1.0-jar-with-dependencies.jar
```

By default, the server will listen on port 8888. This can be modified via the properties file in the
resources directory if you wish to use a different port.
```properties
port=8888
```

The server expects POST requests with the body containing a single text line containing the URL
to be shortened. Refer to the examples below.

**NOTE:** 
The server only accepts POST requests. All other requests will be rejected.

### Properties
A number of properties can be set in the *server.properties* file:
```properties
port=8888
domain=shorty.com
cache.ttl=60
```
where:
- **port** is the port the server will listen on.
- **domain** is the domain name to be used in the shortened URL.
- **cache.ttl** The cache TTL (in seconds) used to determine when expired entries will be evicted.

## Examples
When the server is running you can send requests to it using:
```shell
curl -v --request POST http://127.0.0.1:8888 -d 'http://google.com/pathasdfasdfasd'
```
The command above will send a POST request to the server running on port 8888. The request body is
defined with:
```shell
-d 'http://google.com/pathasdfasdfasd'
```

The server will reply with:
```shell
< HTTP/1.1 200 OK
< content-type: text/plain; charset=UTF-8
< connection: close
< content-length: 24
< 
* Closing connection 0
http://shorty.com/z8EaZI
```
With the shortened URL in the body of the response:
```
http://shorty.com/z8EaZI
```