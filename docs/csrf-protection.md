# CSRF Protection

* [How it works](#user-content-how-it-works)
* [Components](#user-content-components)

Cross-Site Request Forgery (CSRF) allows an attacker to perform actions on behalf of another person without their knowledge or permission.

Coast protects your application from CSRF attacks by denying unidentified requests. HTTP requests with *POST, PUT and DELETE* methods are checked to make sure that the right people from the right place invoke these requests.

## How It Works

1. Coast creates a *CSRF secret* for each request on your site.
2. A corresponding token for the secret is generated for each request and passed to all `form` and `form-for` functions in the `csrf` and `*anti-forgery-token*` bindings
3. Whenever a *POST*, *PUT* or *DELETE* request is made, the middleware verifies the token with the secret to make sure it is valid.

## Components

Coast makes three components available for easy CSRF integration

A hidden input with the csrf token:

#### `csrf`

```clojure
(ns some-ns
  (:require [coast]))

[:form {:action "/" :method :post}
  (coast/csrf)]
```

A form with the hidden input already added to the body:

#### `form`

```clojure
(ns some-ns
  (:require [coast]))

(coast/form {:action "/" :method :post}) ; already includes the `csrf` part
```

And finally a form that includes the csrf hidden input in the body, and also takes a route handler name instead of a map:

```clojure
; example routes
[:post "/customers" :customer/create]
[:put "/customers/:customer-id" :customer/change]

(coast/form-for :customer/create)
  ; ... inputs go here

(coast/form-for :customer/change {:customer/id 123})
  ; ... inputs go here
```

Coast was designed to ensure you don't have to think about low-level details of web applications like CSRF protection but it's always nice to know what's going on under the hood.
