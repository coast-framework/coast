# Routing

* [Basic Routing](#user-content-basic-routing)
* [Route Parameters](#user-content-route-parameters)
* [Named Route](#user-content-named-route)
* [Route Responses](#user-content-route-responses)
* [Route Resources](#user-content-route-resources)
* [Route Prefixes](#user-content-route-prefixes)

Routes enable the outside world to interact with your app via URLs.

Routes are defined inside of the `src/routes.clj` file.

## Basic Routing

The most basic route definition requires an http method, a url and a function:

```clojure
; src/routes.clj
(ns routes
  (:require [coast]))

(coast/defroutes
  [:get "/" (fn [request] {:status 200 :body "hello world")])

; this is just syntactic sugar for

(defn routes []
  (coast/routes
    [:get "/" (fn [request] {:status 200 :body "hello world"})]))
```

The return value of the function will be sent back to the client as a response.

You can also "bind" a route to a function using a `namespace.function` signature:

```clojure
[:get "/" :post/index]
```

The above keyword `post/index` refers to the `src/post.clj` file's `index` function.

### Available Router Methods

Resourceful routes use different HTTP verbs to indicate the type of request:

```clojure
[:get url :qualified/keyword]
[:post url :qualified/keyword]
[:put url :qualified/keyword]
[:patch url :qualified/keyword]
[:delete url :qualified/keyword]
```

## Route Parameters

Route parameters are defined like so:

```clojure
[:get "/posts/:id" (fn [{:keys [params]}]
                     (str "Post " (:id params)))]
```

In the example above, `:id` is a route parameter.

Its value is then retrieved via the `:params` keyword.

## Named Route

Though routes are defined inside the `src/routes.clj` file, they are referenced everywhere else in the application (e.g. using the `url-for` route helper to make a URL for a given route).

By using the last element of the vector, you can assign your route a unique name:

```clojure
[:get "/posts" :post/index :posts]

This will enable you to use `route` helpers in your code, like so:

```clojure
; before
[:a {:href "/posts"} "List of posts"]

; after
[:a {:href (url-for :posts)} "List of posts"]
```

```clojure
; src/post.clj
(ns post
  (:require [coast]))

(defn index [request]
  (coast/redirect-to :posts))

; or more verbose

(defn index [request]
  (coast/redirect (coast/url-for :posts)))
```

Both `route` helpers share the same signature and accept optional parameters map as their second argument:

```clojure
[:get "/posts/:id" :post/view :posts/show]
```

(url-for :posts/show {:id 1})

(redirect-to :posts/show {:id 1})
----

Namespaced keywords are supported as well

```clojure
[:get "/authors/:author-id/posts/:id" :post/view]

(url-for :post/view {:author/id 1 :id 2})

(redirect-to :post/view {:author/id 1 :id 2})

; or you can use the exact parameter name as well

(url-for :post/view {:author-id 1 :id 2})
```

### Query Parameters

Anything you pass to `url-for` or `redirect-to` that isn't defined as a route parameter will be appended as a query parameter

```clojure
[:get "/posts/:post-id/comments/:id/edit" :comment/edit]

(url-for :comment/edit {:post/id 1 :id 2 :all true}) ; => "/post/1/comment/2/edit?all=true"
```

## Route Responses

Routes don't have to just respond with [hiccup vectors](https://github.com/weavejester/hiccup), they can also respond with a map which
overrides any layouts or the coast default of rendering with html.

```clojure
[:get "/" (fn [request] (coast/ok {:message "ok"}))] ; this responds with json by default {"message": "ok"}
```

You can define separate routes for an api and a site:

```clojure
(ns your-app
  (:require [coast]))

; this route corresponds to the src/www/home.clj index function
(coast/defroutes
  [:get "/" :home/index :www.home/index])

; these route correspond to the src/api/home.clj index and status functions
(coast/defroutes api
  [:get "/api" :api.home/index]
  [:get "/api/status" :api.home/status])

(def app (coast/app {:routes/site site :routes/api api}))
```

Coast uses a different set of middleware functions when responding to an api request vs a site request.

The api requests do not check for layouts and a host of other things, making them lighter weight than their site counterparts.

## Route Resources

You will often create resourceful routes to do CRUD operations on a resource.

`resource` assigns CRUD routes to a namespace using a single line of code:

```clojure
; This...
(resource :posts)

; ...equates to this:
[:get "/posts" :post/index]
[:get "/posts/build" :post/build]
[:post "/posts" :post/create]
[:get "/posts/:id" :post/view]
[:get "/posts/:id/edit" :post/edit]
[:put "/posts/:id" :post/change]
[:delete "/posts/:id" :post/delete]
```

NOTE: This feature is only available when binding routes to a namespace.

### Filtering Resources

You can limit the routes assigned by the `resource` method by using the `except` or `only` keywords

#### except

Removes `GET resource/create` and `GET resource/:id/edit` routes:

```clojure
; src/routes.clj
(resource :posts :except [:create :edit])
```

#### only

Keeps only the passed routes:

```clojure
; src/routes.clj
(resource :posts :only [:index :view])
```

### Resource Middleware

You can wrap middleware around any resource as you would with a single route:

```clojure
; src/routes.clj
(ns routes
  (:require [coast]))

(defn wrap-auth [handler]
  (fn [request]
    (if (some? (:session request))
      (handler request)
      (coast/unauthorized [:h1 "HAL9000 says, \"Sorry Dave, I can't let you do that\""]))))

(defroutes
  (coast/wrap-routes wrap-auth
    (resource :posts)))
```

## Route Prefixes

If your application routes share common urls, instead of repeating the same urls for each route, you can prefix them like so:

```clojure
; no prefix
[:get "/api/v1/members" fn]
[:post "/api/v1/members" fn]

; with prefix
(coast/prefix-routes "/api/v1"
  [:get "/members"]
  [:post "/members"])

```

### Middleware

Assign one or many middleware to the route group:

```clojure
(coast/wrap-routes wrap-auth
  (coast/prefix-routes "/api/v1"
    [:get "/members"
    [:post "/members"]]))
```

NOTE: Route middleware executes before app middleware.
