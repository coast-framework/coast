# coast on clojure

An easy to use clojure web framework

## How do I use this?

Create a new coast project like this
```bash
lein new coast blog
cd blog
```

Let's set up the database!
```bash
lein db/create # assumes a running postgres server. creates a new db called blog_dev
```

Let's create a table to store posts and generate some code to so we can interact with that table!
```bash
lein db/migration posts title:text body:text
lein db/migrate
lein mvc/gen posts
```

Let's see our masterpiece so far

```clojure
lein repl ; or start a repl your preferred way
(coast) ; => Listening on port 1337
```

OR

```bash
lein run
```

You should be greeted with the text "You're coasting on clojure!"
when you visit http://localhost:1337 and when you visit http://localhost:1337/posts
you should be able to add, edit, view and delete the rows from the post table!

Amazing!

## TODO

- Document auth
- Document logging?
- Document ... just more documentation

## Why did I do this?

In my short web programming career, I've found two things
that I really like, clojure and rails. This is my attempt
to put the two together and make the web great again.

## Credits

This framework is only possible because of the hard work of
a ton of great clojure devs who graciously open sourced their
projects that took a metric ton of hard work. Here's the list
of open source projects that coast uses:

[potemkin](https://github.com/ztellman/potemkin)
[http-kit](https://github.com/http-kit/http-kit)
[trail](https://github.com/swlkr/trail) (this one is a swlkr special)
[trek](https://github.com/swlkr/trek) (so is this one)
[bunyan](https://github.com/swlkr/bunyan) (and this one. although I guess real logging is coming... maybe)
[environ](https://github.com/weavejester/environ)
[hiccup](https://github.com/weavejester/hiccup)
[ring/ring-core](https://github.com/ring-clojure/ring)
[ring/ring-defaults](https://github.com/ring-clojure/ring-defaults)
[ring/ring-devel](https://github.com/ring-clojure/ring)
[org.postgresql/postgresql](https://github.com/pgjdbc/pgjdbc)
[org.clojure/java.jdbc](https://github.com/clojure/java.jdbc)
[org.clojure/tools.namespace](https://github.com/clojure/tools.namespace)
[ragtime](https://github.com/weavejester/ragtime)
[oksql](https://github.com/swlkr/oksql) (another swlkr special)
[selmer](https://github.com/yogthos/Selmer)
[inflections](https://github.com/r0man/inflections-clj)
[prone](https://github.com/magnars/prone)
[com.jakemccrary/reload](https://github.com/jakemcc/reload)
[cheshire](https://github.com/dakrone/cheshire)
