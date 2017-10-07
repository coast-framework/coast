# coast on clojure

An easy to use clojure web framework

## How do I use this?

Create a new coast project like this
```bash
lein new coast-app blog
cd blog
```

Let's code the world's most useless web app
```clojure
; blog/src/core.clj

(ns blog.core
  (:require [coast.core :as coast]))

(defn index [request]
  {:status 200
   :body "<html><body>Hello world!</body></html>"})

(coast/defroutes routes
  (coast/get "/" index))

(def app
  (-> routes
      coast/wrap-with-logger))

(defn -main [& args]
  (coast/run-server app {:port 1337}))
```

Now you can start your server

```bash
lein run
```

## Why did I do this?

In my short web programming career, I've found two things
that I really like, clojure and rails. This is my attempt
to put the two together and make the web great again.

## Credits
This framework is only possible because of the hard work of
a ton of great clojure devs who graciously open sourced their
projects that took a metric ton hard work that coast is using
under one namespace.

Copyright © 2017 Sean Walker
