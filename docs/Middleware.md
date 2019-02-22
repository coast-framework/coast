# Middleware

* [Creating Middleware](#user-content-creating-middleware)
* [Using Middleware](#user-content-using-middleware)

Middleware hook into the request lifecycle of your application.

They are a set of functions executed in sequence and let you transform the request and/or the response.

As an example, Coast provides several middleware functions inside of the `app` function.

## Creating Middleware

To create a new middleware, Coast's convention is to define a ring middleware function in the `src/middleware.clj` file

```clojure
(ns middleware
  (:require [coast]))

(defn auth [handler]
  (fn [request]
    (if (some? (:session request))
      (handler request)
      (coast/unauthorized "No"))))

In the example `auth` middleware, we want to show a 401 unauthorized response if there is no session

### Middleware Execution Order

When your middleware function gets called, you can modify the request or the response like this:

```clojure
(defn your-middleware [handler]
  (fn [request]
    (let [response (handler request)]
      ; response == {:status 200 :body "" :headers {"content-type" "text/html"}}
      (assoc response :status 404))))
```

All middleware code runs after the request hits your route handler

### Route Middleware

Route middleware executes before the request reaches the Coast routing system. This means if the requested route isn't available, Coast will still execute all middleware and then return a `404`

Route middleware is generally used for authentication or to wrap a set of routes in a common set of HTML tags:

```clojure
(ns route
  (:require [coast]))
```
