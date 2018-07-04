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
[:get "/" `controllers.home/index] ; => :controllers.home/index
```

The backtick is a shorthand for "this is the name of a function in this file". You can also reference functions in other files and folders (or namespaces as they're called), like this:

```clojure
[:get "/todos" `controllers.todos/index]
```

Everything before the `/` is a path, so in the above example the function `index` is located in the `controllers` folder in the `todos` file like this:

```sh
/controllers/todos.clj
```

You can also pass in named parameters as part of the url

```clojure
[:get "/todos/:id" `controllers.todos/show]
```

You can name them anything you like and put them anywhere

```clojure
[:get "/posts/:post-id/comments/:id/edit" `controllers.comments/edit :comments/edit]

; you can pass your params with url-for

(url-for :comments/edit {:post-id 1 :id 2}) ; => "/posts/1/comments/2/edit"
```

If you don’t care to write vectors all day you can also use the helper functions in coast.router

```clojure
(ns routes
  (:require [coast.router :refer [get post put delete]))

(def routes (-> (get "/" `home/index)
                (get "/posts" `posts/index)
                (get "/posts/:id" `posts/show)
                (post "/posts" `posts/create)))
```

The `->` is necessary because I couldn't be bothered to do things properly. It basically just `conj`s
everything into one big vector like this:

```clojure
[[:get "/" `home/index]
 [:get "/posts" `posts/index]
 [:get "/posts/:id" `posts/show]
 [:post "/posts" `posts/create]]
```

Here's a more complete example of 7 CRUD routes:

```clojure
(ns routes
  (:require [coast.router :as router]))

(def routes [[:get    "/todos"          `todos/index]
             [:get    "/todos/:id/edit" `todos/edit]
             [:get    "/todos/new"      `todos/new]
             [:get    "/todos/:id"      `todos/show]
             [:post   "/todos"          `todos/create]
             [:put    "/todos/:id"      `todos/update]
             [:delete "/todos/:id"      `todos/delete]])

(def url-for (partial router/url-for-routes routes))
```

Of course, that's a lot of typing for something you'll probably do a lot,
thank goodness the smart people working on rails have solved this problem already.

```clojure
(ns routes
  (:require [coast.router :as router]))

(def routes (router/resource :todos))

(def url-for (partial router/url-for-routes routes))
```

Same thing, just with less typing. Anything to help you ship faster.

If you don't need all 7, you can just pass the ones you want

```clojure
(ns routes
  (:require [coast.router :as router]))

(def routes (-> (router/resource `todos/index `todos/create)))

(def url-for (partial router/url-for-routes routes))
```

Let's say you want to do something across a few different routes, like make sure someone is logged in before accessing a few more routes?

You can wrap your routes in middleware

```clojure
(ns routes
  (:require [coast.router :as router]
            [coast.responses :as res]))

(def wrap-auth [handler]
  (fn [request]
    (if (some? (-> request :session :identity :user/name))
      (handler request)
      (res/unauthorized
        [:h1 "Sorry dave, I can't let you do that"]))))


(def public [[:get    "/todos"          `todos/index]
             [:get    "/todos/:id/edit" `todos/edit]
             [:get    "/todos/new"      `todos/new]
             [:get    "/todos/:id"      `todos/show]])

(def private (-> [[:post   "/todos"          `todos/create]
                  [:put    "/todos/:id"      `todos/update]
                  [:delete "/todos/:id"      `todos/delete]]
                 (wrap-auth)))

(def routes (concat public private))
```
