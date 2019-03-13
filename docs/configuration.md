# Configuration

* [Development](#user-content-development)
* [Production](#user-content-production)
* [Database](#user-content-database)

## Development

In Coast, configuration in the dev environment can come from four places, a `.env` file, a `env.edn` file, a `db.edn` and the OS's environment variables.

All of these files are located in the project's root directory.

You can access configuration values via the `coast/env` function:

```clojure
(ns your-project
  (:require [coast]))

(coast/env :session-key)
```

Environment variable values are fetched using `coast/env` which accepts a keyword argument referencing the key you want in the lowercase and kebab case style. Whereas OS environment variables tend to look like this:

```bash
COAST_ENV=dev SESSION_KEY=asecret123
```

Coast env keys look like this:

```clojure
{:coast-env "dev"
 :session-key "asecret123"}
```

In development, the `env.edn` file stores environment variables intended to be shared amongst developers, and by default this is checked in to source control. So be careful what you put in there

## Production

In production, while the code looks the same `(coast/env :session-key)` where the values come from should *not* re-use the same values that are checked into source code in `env.edn`.

Coast encourages the use of either an on-server `.env` file located at the root of the project (i.e. the same directory as the running uberjar) or set the environment variables at the OS level or when running the uberjar from the shell session like so:

```bash
COAST_ENV=prod SESSION_KEY=prodsecret123 java -jar your-app-standalone.jar
```

Or if you use something like supervisor, you can set the environment variables in the specific `.conf` file for your project:

```sh
[program:your-app]
command=java -Dclojure.server.repl="{:port 5555 :accept clojure.core.server/repl}" -Xmx100m -jar /home/deploy/your-app/your-app.jar
directory=/home/deploy/your-app
autostart=true
autorestart=true
startretries=3
user=deploy
redirect_stderr=true
stdout_logfile=/home/deploy/your-app/app.log
environment=DATABASE_URL="jdbc:sqlite:/home/deploy/your-app/your_app.db",COAST_ENV=prod,SESSION_KEY=prodsecret123,PORT=3000
```

Common Coast env variables and their defaults:

```clojure
{:coast-env "dev" ; default
 :session-key "" ; there is no default, this is required for the cookie sessions
 :port "1337" ; this is the default, set it to any port your computer is listening for connections on
```

## Database

All database configuration values are stored in `db.edn` which is also safe to check in to source control and is **not** `.gitignore`'d by default.

Here is what the default `db.edn` file looks like:

```clojure
{:dev {:database "your_app.sqlite3"
       :adapter "sqlite"}

 :test {:database "your_app.sqlite3"
        :adapter "sqlite"}

 :prod {:database #env :db-name
        :adapter "sqlite"}}

 ; or you can use postgres

 ; :prod {:database #env :db-name
 ;        :adapter "postgres"
 ;        :username #env :db-username
 ;        :password #env :db-password
 ;        :host #env :db-host
 ;        :port #env :db-port}}
```

There are three different values for the three different environments the database will be running in, `dev`, `test` and `prod`. The `prod` environment is special because it uses a [tagged literal](https://github.com/edn-format/edn#tagged-elements) and retrieves the configuration values from the environment, either `.env`, `env.edn` or the OS.

This file can and should be checked in but try not to set the production values directly in the file, instead on the production server set these environment variables:

```bash
DB_NAME=<your database name>
DB_USERNAME=<if your database is password protected>
DB_PASSWORD=<if your database is password protected>
DB_HOST=<typically localhost>
DB_PORT=<by default for postgres this is 5432>
```
