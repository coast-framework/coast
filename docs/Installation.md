# Installation

* [System Requirements](#user-content-system-requirements)
* [Installing Coast](#user-content-installing-coast)
* [Serving the Application](#user-content-serving-the-application)

Installing Coast is a simple process and will only take a few minutes.

## System Requirements

The only dependencies of the framework are `java` and `clojure`.

Ensure your versions of those tools match the following criteria:

- clojure >= 1.8.0
- java >= 9

TIP: You can use tools like [jabba](https://github.com/shyiko/jabba) to help manage multiple versions of java at the same time.

## Installing Coast

### Via Coast CLI

Coast CLI is a command line tool to help you install Coast.

Install it globally via `curl` like so:

```bash
curl -o /usr/local/bin/coast https://raw.githubusercontent.com/coast-framework/coast/master/coast && chmod a+x /usr/local/bin/coast
```

Make sure to add the `/usr/local/bin` directory to your `$PATH`.

Once installed, you can use the `coast new` command to create fresh installations of Coast.

For example, to create a new application called `zero`, simply:

```bash
coast new zero
```

## Serving the application

Once the installation process has completed, you can `cd` into your new application directory and run the following command to start the server:

```bash
make server
```

Or you can run `(server/-main)` from your REPL. The REPL server can be started from the terminal with:

```bash
make repl
```

This command starts the server on the port defined inside the root `env.edn` file.
