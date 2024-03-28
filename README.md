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

### POST Requests
POST requests are used to shorten the URLs. The POST body should contain a single line containing just the
URL that we want to shorten. For example:
```shell
curl -v --request POST http://127.0.0.1:8888 -d 'http://google.com/path'
```
The server will respond with a shortened (random) URL in the response body:
```shell
http://shorty.com/EN4Ryh
```

### GET Requests
To use the shortened URL, send a GET request using the path defined in the POST example above:
```shell
curl -v  http://127.0.0.1:8888/EN4Ryh
```
If there is a mapping of `EN4Ryh` to a URL then we get the redirect; for example:
```shell
< HTTP/1.1 301 Moved Permanently
< content-type: text/html; charset=UTF-8
< connection: close
< content-length: 214
< location: http://google.com/path
< 
<!DOCTYPE HTML>
<html lang="en-US">
    <head>
        <meta charset="UTF-8">
        <meta http-equiv="refresh" content="0; url=http://google.com/path">
        <title>Page Redirection</title>
    </head>
</html>
```
Note that the server sends a 301 and adds the original URL in the `location` header.

If the lookup fails then we get a 404:
```shell
< HTTP/1.1 404 Not Found
< content-type: text/html; charset=UTF-8
< connection: close
< content-length: 9
< 
* Closing connection 0
Not Found
```

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