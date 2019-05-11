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

### From Coast CLI

Coast CLI is a command line tool to help you install Coast.

Install it globally via `curl` like so:

```bash
curl -o /usr/local/bin/coast https://raw.githubusercontent.com/coast-framework/coast/master/coast && chmod a+x /usr/local/bin/coast
```

Make sure to add the `/usr/local/bin` directory to your `$PATH`.

Once installed, you can use the `coast new` command to create new Coast apps.

For example, to create a new application called `zero`, type the following into your terminal:

```bash
coast new zero
```

## Serving the application

### From the CLI

Once the installation process has completed, you can `cd` into your new application directory and run the following command to start the server:

```bash
make server
```

This command starts the server on the `:port` defined inside the `env.edn` file: `http://localhost:1337`

### From the REPL

Or you can run `(server/-main)` from your REPL. The REPL server can be started from the terminal with:

```bash
make repl
```

Then connect to the editor from your REPL with one of these handy guides:

- [Cursive](https://cursive-ide.com/userguide/repl.html)
- [Atom With Proto-REPL](https://github.com/jasongilman/proto-repl#connecting-to-a-remote-repl)
- [VSCode with Calva](https://github.com/BetterThanTomorrow/calva#how-to-use)
- [Emacs with Cider](https://github.com/clojure-emacs/cider#connect-to-a-running-nrepl-server)
- [Vim with Fireplace](https://github.com/tpope/vim-fireplace)

After the editor is connected to the running REPL server, note this code at the bottom of the `server.clj` file:

```clojure
(comment
  (-main))
```

Move your text editor's cursor over any of the letters in `-main` and press the keyboard shortcut for "sending the text under the cursor" to the REPL server. The http server will start and you can navigate to `http://localhost:1337`
