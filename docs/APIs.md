# APIs

You want to make a native iphone app? You can do that with coast as your backend!
Just tell coast which routes are api routes and it will by default respond with json and parse json params
in the body of requests!

```clojure
(ns server
  (:require [coast]))

(defn home [request]
  (coast/ok {:message "Welcome!"}))

(def routes [[:get "/" :home]])

(def app (coast/app {:routes/api routes}))

(app {:request-method :get :uri "/"}) ; => {"message": "Welcome!"}
```
