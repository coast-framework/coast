# Routing

Routes in coast are vectors, they look like this

```clojure
[:get "/" `home]
```

You can also name your routes and reference them by name later with `url-for`

```clojure
[:get "/" `home :home]

; used later

(url-for :home) ; => "/"
```

If you don't give a name, the name of the fully qualified (folders + file + function) is used instead

```clojure
[:get "/" `home] ; => :home
[:get "/" `home.index/view] ; => :home.index/view
```

The backtick is a shorthand for "this is the name of a function in this file". You can also reference functions in other files and folders (or namespaces as they're called), like this:

```clojure
[:get "/todos" `todo.index/view]
```

Everything before the `/` is a path, so in the above example the function `view` is located in the `todo` folder in the `index` file like this:

```bash
todo/index.clj
```

You can also pass in named parameters as part of the url

```clojure
[:get "/todo/:id" `todo.action/show]
```

You can name them anything you like and put them anywhere

```clojure
[:get "/post/:post-id/comment/:id/edit" `comment.edit/view :comment/edit]

; you can pass your params with url-for

(url-for :comment/edit {:post-id 1 :id 2}) ; => "/post/1/comment/2/edit"

; any params you don't reference in the url will be appended as query params

(url-for :comment/edit {:post-id 1 :id 2 :all true}) ; => "/post/1/comment/2/edit?all=true"
```

Let's say you want to do something across a few different routes, like make sure someone is logged in before accessing a few more routes?

You can wrap your routes in middleware

```clojure
(ns routes
  (:require [coast.router :as router]
            [coast.app :refer [unauthorized]]))

(def wrap-auth [handler]
  (fn [request]
    (if (some? (-> request :session :user/name))
      (handler request)
      (unauthorized
        [:h1 "Sorry dave, I can't let you do that"]))))

(def public [[:get    "/todo"          `todo.index/view]
             [:get    "/todo/new"      `todo.new/view]
             [:get    "/todo/:id/edit" `todo.edit/view]
             [:get    "/todo/:id"      `todo.show/view]])

(def private (-> [[:post   "/todo/new"         `todo.new/action]
                  [:post   "/todo/:id/edit"    `todo.edit/action]
                  [:post   "/todos/:id/delete" `todos.delete/action]]
                 (wrap-auth)))

(def routes (concat public private))
```
