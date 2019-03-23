# Sessions

* [Supported Stores](#user-content-supported-stores)
* [Basic Example](#user-content-basic-example)
* [Flash Messages](#user-content-flash-messages)
* [View Logic](#user-content-view-logic)
* [Components](#user-content-components)
* [Layout](#user-content-layout)
* [Syntax](#user-content-syntax)

Coast has first-class session support with a variety of inbuilt drivers to efficiently manage and store sessions.

In this guide, we learn how to configure and use these different session drivers.

## Supported stores
Below is the list of stores supported by Coast.

You can change the current store by adding one to your `deps.edn` file and specifying it in the `app` map.

Note: The [redis](https://github.com/paraseba/rrss) and [jdbc](https://github.com/luminus-framework/jdbc-ring-session) stores are not included with coast by default.

| Name          | Function         | Description                                                         |
| :------------ | :--------------- | :------------------------------------------------------------------ |
| Cookie        | cookie-store   | The default store used by coast, stores sessions in encrypted cookie |
| Memory        | memory-store   | In-memory store, all sessions will be reset on server restart       |
| JDBC          | jdbc-store     | Stores the sessions in a database table                             |
| Redis         | redis-store    | Stores the sessions in redis                                        |


## Basic example
The `session` object is passed as part of the [request map](/docs/request.md)

Here's a quick example of how to use sessions during the HTTP lifecycle:

```clojure
(ns server
  (:require [coast]))

(def app (coast/app {:session {:store (cookie-store {:key "16 byte secret key"})}}))
```

Then later on in a handler function:

```clojure
(defn index [request]
  (let [authenticated-person (get-in request [:session :person/id])]
    (if (some? authenticated-person)
      [:h1 "You're logged in!"]
      [:a {:href (coast/url-for :login)} "Log in here"])))
```

Here's how to set a session after a person signs in or creates an account:

```clojure
(defn create [request]
  (-> (coast/redirect-to :after-sign-up)
      (assoc :session {:person/id 123})))
```

If you don't feel like checking if someone is authenticated in every handler function,
feel free to use middleware:

```clojure
; src/middleware.clj

(defn auth [handler]
  (fn [request]
    (let [person (get-in request [:session :person/id])]
      (if (some? person)
        (handler request)
        (coast/unauthorized "HAL9000: Sorry Dave, I can't let you do that")))))
```

Then you can wrap the routes that require auth:

```clojure
; src/routes.clj

(def routes
  (coast/site
    [:get "/" :home/index]
    [:get "/sign-in" :session/build]
    [:post "/sessions" :sessions/create]

    (coast/with middleware/auth
      [:get "/dashboard" :home/dashboard]))))
```

## Flash messages
Flash messages are short-lived session values for a single request only. They are mainly used for *success messages*, but can be used for any other purpose.

### HTML form example

Let's say we want to validate submitted user data and redirect back to our form if there aren't any validation errors.

Start with the following HTML form:

```clojure
; src/customer.clj

(defn build [request]
  (coast/form-for ::create
    [:input {:type "text" :name "customer/email"}]
    [:button {:type "submit"} "Submit"]))
```

Then, add the `/customers` routes to validate form data:

```clojure
(def routes
  (coast/site
    [:get "/customers/build" :customer/build]
    [:post "/customers" :customer/create]
    [:get "/customers/:customer-id" :customer/view]))
```

...and implement the handler

```clojure
; src/customer.clj

(defn create [request]
  (let [[_ errors] (-> (select-keys (:params request) [:customer/email])
                       (coast/validate [[:email [:customer/email]]])
                       (coast/rescue))
    (if (nil? errors)
      (-> (redirect-to ::view)
          (flash "Thanks for signing up!"))
      (customer/build (merge request errors)))))
```

Finally, write the view handler to show the flash message:

```clojure
; src/customer.clj

(defn view [request]
  [:div
    (when (some? (:flash request)))]
      (:flash request))
```

Here's the whole `customer.clj` file just for kicks and giggles:

```clojure
; src/customer.clj

(defn build [request]
  (coast/form-for ::create
    [:input {:type "text" :name "customer/email"}]
    [:button {:type "submit"} "Submit"]))


(defn create [request]
  (let [[_ errors] (-> (select-keys (:params request) [:customer/email])
                       (coast/validate [[:email [:customer/email]]])
                       (coast/rescue))
    (if (nil? errors)
      (-> (redirect-to ::view)
          (flash "Thanks for signing up!"))
      (customer/build (merge request errors)))))


(defn view [request]
  [:div
    (when (some? (:flash request)))]
      (:flash request))
```
