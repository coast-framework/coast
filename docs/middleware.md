# Middleware

* [Creating Middleware](#user-content-creating-middleware)
* [Using Middleware](#user-content-using-middleware)
* [Route Middleware](#user-content-route-middleware)
* [Middleware Options](#user-content-middleware-options)

Middleware hook into the request lifecycle of your application.

They are a set of functions executed in sequence and let you transform the request and/or the response.

As an example, Coast provides several middleware functions inside of the `app` function.

## Creating Middleware

To create a new middleware, Coast's convention is to define a [ring middleware](https://github.com/ring-clojure/ring/wiki/Middleware-Patterns) function in the `src/middleware.clj` file

```clojure
(ns middleware
  (:require [coast]))

(defn auth [handler]
  (fn [request]
    (if (some? (:session request))
      (handler request)
      (coast/unauthorized "No"))))
```

In the example `auth` middleware, we want to show a 401 unauthorized response if there is no session

### Middleware Execution Order

When your middleware function gets called, you can modify the request or the response like this:

```clojure
(defn your-middleware [handler]
  (fn [request]
    ; request == {:request-method :get :uri "/" :headers {} :params {}}
    (let [request (merge request {:hello "world"})
          response (handler request)]
      ; response == {:status 200 :body "" :headers {"content-type" "text/html"}}
      (assoc response :status 404))))
```

All middleware code runs after the middleware modified request hits your route handler.

All middleware referenced "below" your handler function modifies the request before your function and then it modifies the response after your function is called.

In fact, while coast is a "web app framework", the `app` function is really just made up of a series of ring middleware, like this:

```clojure
(defn app
  "The main entry point for coast apps"
  [opts]
  (let [routes (:routes opts)
        opts (dissoc opts :routes)]
    (-> (router/handler routes opts)
        (middleware/wrap-logger)
        (middleware/wrap-file opts)
        (middleware/wrap-absolute-redirects)
        (middleware/wrap-resource "public")
        (middleware/wrap-content-type)
        (middleware/wrap-default-charset "utf-8")
        (middleware/wrap-not-modified)
        (middleware/wrap-simulated-methods)
        (middleware/wrap-coerce-params)
        (middleware/wrap-keyword-params)
        (middleware/wrap-params)
        (middleware/wrap-site-errors routes)
        (middleware/wrap-reload))))
```


### Route Middleware

Route middleware can modify the request before the functions you write and modify the response map after.

Even `404`'s are middleware functions that come from `(coast/site-routes)`.

Coast calls four functions on every route defined underneath `site-routes`:

```clojure
(def routes
  (coast/routes
    (coast/site-routes :option-layout-fn
      [:get "/posts" :post/index]
      [:post "/posts" :post/create]
      [:404 :home/not-found]
      [:500 :home/server-error])))
```

1. wrap-layout
2. wrap-site-defaults
3. wrap-not-found
4. wrap-site-errors

#### `wrap-layout`
This function wraps any [hiccup](https://github.com/weavejester/hiccup) vector returned from functions that you write in the `function` you specify as the fist argument to `site-routes`.

#### `wrap-site-defaults`
This function wraps common website options, csrf protection, content-type headers, etc. from [ring's own defaults](https://github.com/ring-clojure/ring-defaults)

#### `wrap-not-found`
This function catches any coast exception maps with a `:404` key and returns your specified `[:404 :home/not-found]` 404 function (in this case `:home/not-found` or `home.clj`'s `not-found` function).

#### `wrap-site-errors`
This is similar to not found, except it catches any exceptions thrown by your code and returns your specified `:500` handler, which is by default `:home/server-error`.

### Middleware Options
Coast can receive several options to the various middleware functions.

```clojure
(coast/app {:storage "/path/to/your/files"
            :session {:cookie-attrs {:max-age 2629800}}})
```

Those two are the most common keys that coast can be configured with:

#### `:storage`
This is the path to where user uploaded files will be served from, so you might want to put this in an environment variable and reference it if you need to store user uploaded things.

#### `:session`
There are multiple keys you can pass to the `:session` key, but `cookie-attrs` is the most common one, you can adjust how long a given person can stay logged into your site, the cookies' name in their browsers, anything about the [ring session cookie store](https://github.com/ring-clojure/ring/wiki/Sessions), really.
