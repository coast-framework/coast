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

Typing out routes and putting them in a clojure file works well when there aren't too many routes, but even a solo dev might have an app with quite a few. That's where `routes.edn` comes in. This file lets you specify your routes in an edn file and coast will read from this file. Here's what it looks like:

```clojure
[[:get "/"         :home.index/view]
 [:get "/todos"    :todo.index/view]
 [:get "/todo/:id" :todo.show/view]]
```

And you can specify middleware as data in the same file too

```clojure
; routes.edn
{:middleware/wrap-certain-errors [[:get "/"         :home.index/view]
                                  [:get "/todos"    :todo.index/view]
                                  [:get "/todo/:id" :todo.show/view]]}
```

or multiple middleware

```clojure
; routes.edn
{[:middleware/wrap-certain-errors
  :middleware/another-one]        [[:get "/"         :home.index/view]
                                   [:get "/todos"    :todo.index/view]
                                   [:get "/todo/:id" :todo.show/view]]}
```
