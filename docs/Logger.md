# Logger

Coast comes with a relatively simple (i.e. not feature-packed) logger.

The goal was to adhere to the 12factor app rules and not really worry about logging.

All requests and responses are logged to stdout and look a little something like this:

```bash
2019-03-02 10:56:58 -0700 GET "/" :home/index 200 text/html 4ms
```

The format is more generally:

- `timestamp`
- `request method`
- `requested url`
- `handler keyword called`
- `response status`
- `response content type`
- `response time`

It's old school, but don't be afraid to use `println` if you get stuck for any reason.

The logger may grow up in the future, the goal may be to copy well-known frameworks' loggers, but for now, this will do.
