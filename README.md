# coast

An easy to use clojure web framework

## Usage

Add this to your `project.clj`

```clojure
[coast "1.0.0"]
```

Now you can get started!
Create a new lein project with
a default lein template:

```bash
lein new blog
```

```clojure
(ns blog.core
  (:require [coast.core :as coast]))

(defn index [request]
  {:status 200
   :body "<html><body>Hello world!</body></html>"})

(coast/defroutes routes
  (coast/get "/" index))

(coast/run-server routes {:port 1337})
```

Copyright © 2017 Sean Walker
