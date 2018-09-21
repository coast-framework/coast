# Routing

Routes in coast are vectors, they look like this

```clojure
[:get "/" :home.index/view]

; used later

(url-for :home.index/view) ; => "/"
```

You can also name your routes and reference them by name later with `url-for`

```clojure
[:get "/" :home.index/view :home]

; used later

(url-for :home) ; => "/"
```

If you don't give a name, the name of the fully qualified (folders + file + function) is used instead

```clojure
[:get "/" :home] ; => :home
[:get "/" :home.index/view] ; => :home.index/view
[:get "/todos" :todo.index/view] ; => :todo.index/view
```

Everything before the `/` is a path, so in the above example the function `view` is located in the `todo` folder in the `index` file like this:

```bash
# your coast project
src/todo/index.clj
```

You can also pass in named parameters as part of the url

```clojure
[:get "/todo/:id" :todo.show/action]
```

You can name them anything you like and put them anywhere

```clojure
[:get "/post/:post-id/comment/:id/edit" :comment.edit/view :comment/edit]
```

You can pass your params with url-for

```clojure
(url-for :comment/edit {:post-id 1 :id 2}) ; => "/post/1/comment/2/edit"
```

Any params you don't reference in the url will be appended as query params

```clojure
(url-for :comment/edit {:post-id 1 :id 2 :all true}) ; => "/post/1/comment/2/edit?all=true"
```

Sometimes you need to call a function (or a few) on certain routes, but not every route, here's how that looks

```clojure
(ns routes
  (:require [coast :refer [wrap-routes unauthorized]]))

(defn wrap-auth [handler]
  (fn [request]
    (if (some? (:session request))
      (handler request)
      (unauthorized [:h1 "Sorry dave, I can't let you do that"]))))

(def public [[:get "/"          :home.index/view]
             [:get "/todo/list" :todo.index/view]
             [:get "/todo/:id"  :todo.show/view]])

(def private (wrap-routes wrap-auth
              [[:get "/new-todo"  :todo.new/view]
               [:post "/new-todo" :todo.new/action]]))

(def routes (concat public private))
```

Maybe you want separate routes for an api and routes for your site? Coast can do that too!

```clojure
(ns your-app
  (:require [coast]))

(def site [[:get "/" :home.index/view :home]]))

(def api [[:get "/api/status" :api/status]])))
          [:get "/api/another-api-route" :api/another]

(def app (coast/app {:routes/site site :routes/api api}))
```

If you have a lot of routes that share the same prefix you can use `prefix-routes` from the `coast` namespace

```clojure
(ns routes
  (:require [coast :refer [prefix-routes]]))

(def site [[:get "/" :home.index/view :home]]))

(def api (prefix-routes "/api"
          [[:get "" :api/status]] ; this is the /api route, it's weird but it works
           [:get "/another-api-route" :api/another]))
```

Or if you just want to use coast as an api, you can do that too

```clojure
(ns server
  (:require [coast])
  (:gen-class))

(defn home [request]
  (coast/ok {:message "ok"}))

(def app (coast/app {:routes [[:get "/" :home]]}))

(defn -main [& [port]]
  (coast/server app {:port port}))

(comment
  (app {:request-method :get :uri "/"}) ; => {"message": "ok"}
```
