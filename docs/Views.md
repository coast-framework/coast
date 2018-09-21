# Views

Views are clojure functions the emit [hiccup](https://github.com/weavejester/hiccup) with a few implicit things related to re-usable layouts
When starting up a coast app, you call `coast/app` and you can pass in a few options, one of which
is a function that represents a persistent set of html elements you want to see across all pages
of your site

```clojure
(ns server
  (:require [coast]))

(defn home [request]
  [:h1 "Welcome!"])

(def routes [[:get "/" :home]})

(defn layout [request body]
  [:html
    [:head
      [:title "Hello!"]
    [:body
      body]]])

(def app (coast/app {:routes routes :layout layout}))

(app {:request-method :get :uri "/"}) ; => <html><head><title>Hello!</title></head><body><h1>Welcome!</h1></body></html>
```
