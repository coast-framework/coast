# Request

* [Request Body](#user-content-request-body)
* [Headers](#user-content-headers)
* [Content Types](#user-content-content-types)
* [Method Spoofing](#user-content-method-spoofing)
* [Extending Request](#user-content-extending-request)


This guide outlines how to use the request map to read request data.

Coast passes the current HTTP request object as part of the [request lifecycle](/docs/request-lifecycle.md) which is sent to all route handlers and middleware:

```clojure
(defn index [{:keys [params session errors uri request-method] :as request}])
```

The example above uses [destructuring](https://clojure.org/guides/destructuring) to assign the keys in the request map to variables of the same name.

## Request body
The request map is composed of a number of keys that will help you decided what to do on each request

### Comment clojure functions to retrieve values from the request map
```clojure
; using clojure's get function
(get request :params) ; => {}

; using the keyword itself as a function
(:params request) ; => {}

; using destructuring in the function arguments
(defn index [{:params params}])

; using :keys destructuring in the function arguments
(defn index [{:keys [params]}])

; having the request still be a variable in the current function
(defn index [{:keys [params] :as request}]
  (let [session (:session request)]))
```

### Request body keys
The following list of functions can be used to read any value from the request map.

#### `:params`
Returns a value containing a mix of query params and form params

```clojure
(coast/form-for :todo/change {:todo/id 123}
  [:input {:type "text" :name "todo/name"}]
  [:input {:type "text" :name "todo/category"}]))

  [:input {:type "submit" :value "Submit"}]

; sends your handler function this request map when submitted
(defn change [request]
  (let [params (:params request)
        uri (:uri request)
        request-method (:request-method request)]
    (= params {:todo/name "some name" :todo/category "some category" :todo/id 123})
    (= uri "/todos/123")
    (= request-method :put)))
```

#### `:coerced-params`
This is the same as params in most cases

#### `:raw-params`
Coast attempts to intelligently coerce params from strings to ints, floats, decimals, booleans, and sequences.
This key holds the value of the params as the http server received them.

#### `:session`
This is set when a person is said to be logged in

```clojure
(= request {:session {:id 123 :a-value-you-control ""}})
```

#### `:uri`
This is the url of the incoming request

#### `:request-method`
This is the spoofed method of the incoming request. Spoofed methods being either `put`, `patch` or `delete`.

#### `:original-request-method`
This is the method of the incoming request before Coast gets ahold of it

#### `:errors`
This can be set when `rescue`-ing from any errors, validation errors, app code exceptions, those kinds of things.

## Headers
Headers in coast are string values, not keywords

So this:

```http
Accept: application/json
Content-Type: application/json
```

Is this in coast:

```clojure
(defn change [request]
  (= request {:headers {"Accept" "application/json" "Content-Type" "application/json"}}))
```

## Content Types
Web servers don't only serve web pages – they also have to deal with API responses served as *JSON*

In Coast you can separate json responses and html responses at the router level

```clojure
; routes.clj
(def routes
  (coast/routes
    (coast/site-routes
      [:get "/" :home/index])

    (coast/api-routes
      [:get "/api" :api.home/index])))
```

This helps Coast not only handle json requests with less checking for json request bodies, it also helps with returning the response as json without specifying json in every single handler function:

```clojure
(ns api.home
  (:require [coast]))

(defn index [request]
  (coast/ok {:hello "world"})) ; this will return maps, vectors or anything allowable as json
```

Feel free to not specify any particular set of route middleware though:

```clojure
(def routes
  (coast/routes
    [:get "/" :home/index]))
```

Content negotiation and what should be returned is entirely up to you.

## Method spoofing
HTML forms are only capable of making `GET` and `POST` requests, which means you cannot utilize the REST conventions of other HTTP methods like `PUT`, `PATCH` and `DELETE`.

Coast makes it simple to bypass the request method by adding a `_method` input to a form, executing the correct route for you automatically:

```clojure
; give this route
[:put "/posts/:post-id" :post/change]

(coast/form {:method :put :action "/posts/:post-id" :post/id 123}))

; of course you can take this one step further by using coast's form helper
(let [post {:post/id 123}]
  (coast/form-for :post/change post))
```

## Extending Request
It is also possible to add your own keys to the `request` map by calling `assoc` or `merge` in a middleware function you define

```clojure
; src/middleware.clj
(defn wrap-auth [handler]
  (fn [request]
    (if (some? (:session request))
      (handler (assoc request :authenticated? true))
      (handler request))))

; src/server.clj
(def app (-> (coast/app {:routes routes}))
             (wrap-auth)
```
