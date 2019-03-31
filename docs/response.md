# Response

* [Basic Example](#user-content-basic-example)
* [Making Responses](#user-content-making-responses)
* [Headers](#user-content-headers)
* [Cookies](#user-content-cookies)
* [Redirects](#user-content-redirects)
* [Attachments](#user-content-attachments)
* [Extending Response](#user-content-extending-response)

This guide outlines how to use the response map to respond to incoming requests.

Coast passes the current HTTP response map as part of the [request-lifecycle](/docs/request-lifecycle.md) which is sent to all route handlers and middleware.

```clojure
; routes.clj
(def routes
  (coast/routes
    [:get "/" :home/index]))

; src/home.clj
(defn index [request]
  {:status 200 :body "this returns a string"})
```

The above example returns a string with a content type of `text/plain`

## Basic Example
The following example returns an array of customers in JSON format:

```clojure
; routes.clj
[:get "/customers" :customer/index]

; src/customer.clj
(defn index [request]
  (let [customers [{:customer/id 1 :customer/name "Sean"}
                   {:customer/id 2 :customer/name "Johnny"}
                   {:customer/id 2 :customer/name "Felix"}
                   {:customer/id 2 :customer/name "Gloria"}]]
    {:status 200 :body customers :headers {"Content-Type" "application/json"}}))
```

The `coast/ok` function can also be used instead of a map:

```clojure
(defn index [request]
  (let [customers [{:customer/id 1 :customer/name "Sean"}
                   {:customer/id 2 :customer/name "Johnny"}
                   {:customer/id 2 :customer/name "Felix"}
                   {:customer/id 2 :customer/name "Gloria"}]]
    (coast/ok customers :json)))
```

## Making responses

Coast has common functions for the most common http responses:

- ok (200)
- created (201)
- accepted (202)
- no-content (204)
- bad-request (400)
- unauthorized (401)
- not-found (404)
- forbidden (403)
- server-error 500

They accept either a keyword as the last argument, `:html` or `:json`.

```clojure
(coast/created {:customer/id 1} :json)

(coast/ok [:h1 "hello world"] :html)
```

## Headers

They also accept headers as well:

```clojure
(coast/created {:customer/id 1} {"Content-Type" "application/json"})

(coast/ok [:h1 "hello world"] {"Content-Type" "text/html"})
```

Coast by default returns `{"Content-Type" "application/octet-stream"}`

## Cookies
Use the following keys to set/remove response cookies.

#### cookie
Set a cookie value:

```clojure
{:status 200
 :body ""
 :cookies {"cart-total" {:value "20"}}}
```

#### clearing a cookie
Remove an existing cookie value (by setting its expiry in the past):

```clojure
{:status 200
 :body ""
 :cookies {"cart-total" {:expires (coast/time 1 :second/ago)}}}
```

As well as setting the value of the cookie, you can also set additional keys:

Shamelessly stolen from the [ring docs](https://github.com/ring-clojure/ring/wiki/Cookies)

- `:domain` - restrict the cookie to a specific domain
- `:path` - restrict the cookie to a specific path
- `:secure` - restrict the cookie to HTTPS URLs if true
- `:http-only` - restrict the cookie to HTTP if true (not accessible via e.g. JavaScript)
- `:max-age` - the number of seconds until the cookie expires
- `:expires` - a specific date and time the cookie expires
- `:same-site` - Specify :strict or :lax to determine whether cookies should be sent with cross-site requests

## Redirects
Use one of the following functions to redirect requests to a different URL.

#### `redirect`
Redirect request to a different url (by default it will set the status as `302`):

```clojure
(defn view [request])

(defn create [request]
  (coast/redirect (coast/url-for ::view)))
```

You can also skip the `url-for` like so:

```clojure
(defn create [request]
  (coast/redirect-to ::view))
```

Or you can redirect to any url:

```clojure
(coast/redirect "https://coastonclojure.com")
```

#### `flash`

Coast also has a handy flash function which will append a value to the next request after the redirect:

```clojure
(-> (coast/redirect-to ::view)
    (coast/flash "Item created successfully!"))
```

This flash value now resides in the next function's request map under the `:flash` key

## Extending Response
It is also possible to extend the `response` map by adding your own keys:

```clojure
(defn index [request])
  (-> (coast/ok "Annie are you ok, are you ok, Annie?")
      (assoc :my-custom-key-for-my-custom-middleware "hello")
```
