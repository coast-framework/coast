# Request Lifecycle

* [Introduction](#user-content-introduction)
* [Request Flow](#user-content-request-flow)
* [HTTP Context](#user-content-http-context)

## Introduction

Coast utilizes the [ring](https://github.com/ring-clojure/ring) library for handling requests and responses. For beginners, it can be difficult to understand how it works, and how to handle its higher order functional approach to application flow.

It can also be confusing differentiating the clojure you write for your application code verses what the framework supplies. Hopefully clearer namespaces can help with this.

Having an excellent high-level overview of the request lifecycle is a must-have. Coast will feel less "magical", and you will be more confident about building your applications.

## Request Flow

HTTP requests sent from a client are handled in the Coast `server`  namespace, executing all **middleware** (for example, the `wrap-file` middleware that serves static files from the `resources/public` directory).

If the request isn’t terminated by middleware, the Coast `router` comes into play. It attempts to find a route that matches the URL requested. If the `router` can't find a match, the `not-found` route will be returned.

After finding a matching route, all **middleware** are executed. If no middleware terminate the request, the matched route handler is called.

You must respond to the request in your route handler. Once terminated, Coast executes all **middleware** and sends the response back to the client.

## HTTP Context

Coast provides an **HTTP Context** map in the form of ring's request map to each route handler.

This map contains everything you need to handle the request, like the `:uri` of the route, any url or form `:params` or the `:session` values and can be easily extended via [Middleware](Middleware.md)

```clojure
(defn index [{:keys [params session uri method] :as request}]
  ; response map or html goes here
  [:div "some content"] ; this is combined with the layout function in components
  ; or you can override the layout like so
  {:status 200 :body "<div>some content</div>" :headers {"content-type" "text/html"}}
  ; or use coast html response helpers
  (ok "<div>some content</div>")
  )
```

Alternatively, you can use it directly instead of destructuring it:

```clojure
(defn index [request]
  (let [params (:params request)
        session (:session request)
        uri (:uri request)
        method (:method request)]))
```
