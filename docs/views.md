# Views

* [Basic Example](#user-content-basic-example)
* [Request Information](#user-content-request-information)
* [Helpers](#user-content-helpers)
* [View Logic](#user-content-view-logic)
* [Components](#user-content-components)
* [Layout](#user-content-layout)
* [Syntax](#user-content-syntax)

Coast uses [hiccup](https://github.com/weavejester/hiccup) as its rendering engine, which is pretty darn fast and comes with an elegant API to create dynamic views.

## Basic example
Let's start with the classic **Hello World** example by rendering a hiccup vector.

All views are stored alongside form submission code in the `src` directory, which
is in opposition to most other frameworks where controllers and views are separate files.

Coast takes a different "separation of concerns" where the concern isn't filetypes but instead
logical bits of code that fit together.

It's always nice and sometimes jarring to see the form submission code right below the code that
will be called on redirect or error in the same file.

```clojure
[:h1 "Hello world"]
```

This is a hiccup vector, clojure keywords make up the html bits, `:a`, `:h2`, `:div` and the rest of the vector
is a map of parameters representing the html tag's attributes. The body of the tag is the last element of the vector.

Here's how it looks in practice.

Make a route that will render some html:

```clojure
; src/routes.clj
(def routes
  (coast/routes
    (coast/site-routes
      [:get "/" :home/index])))
```

```clojure
; src/home.clj

(defn index [request]
  [:div
    [:h1 "Hello world"]])
```

That's all you need to render html to the client.

There are no separate templating libraries you have to learn, no special syntax (aside from hiccup's syntax),
it's just clojure.

### Nested views
You can also render other functions that emit hiccup:

```clojure
(defn goodbye [s]
  [:h2 (str "Goodbye " s)])

(defn index [request])
  [:div
    [:h1 "Hello world"]
    (goodbye "world")]
```

## Request information
All views have access to the current `request` map.

You can use request data inside your view functions like so:

```clojure
[:div (str "The request URL is " (:uri request))]
```

## Helpers
The following helpers are provided by Coast

#### `coast/css`
Adds a `link` tag to a CSS bundle.

Relative path (to CSS files in the `public` directory):

```clojure
(coast/css "bundle.css")

; assuming the assets.edn looks something like this
{"bundle.css" ["style.css"]}
```

The code above outputs:

```html
<link rel="stylesheet" href="/style.css" />
```

#### `coast/js`
Adds a `script` tag to a JS bundle

```clojure
(coast/js "bundle.js")

; assuming the assets.edn looks like this
{"bundle.js" ["app.js"]}
```

The code above outputs:

```html
<script type="text/javascript" src="/app.js"></script>
```

#### url-for
Returns the URL for a route.

For example, using the following example route…

```clojure
[:get "/customers/:customer-id" :customer/show]
```

…if you pass the route name and any route parameters…

```clojure
[:div
  [:a {:href (url-for :customer/show {:customer/id 123})}
    "View customer"]]
```

…the route URL will render like so:

```html
<a href="/customers/123">
  View customer
</a>
```

#### CSRF
You can access the CSRF token and input field using one of the following helpers.

#### `csrf`

```clojure
[:form {:action "/" :method "POST"}
  (coast/csrf)]
```

Which renders

```html
<form action="/" method="POST">
  <input type="hidden" name="__anti-forgery-token" value="<token value>" />
</form>
```

#### Forms
You never really have to know about the csrf field itself because coast has built in form components as well

#### `form`

```clojure
(coast/form (coast/action-for :customer/change {:customer/id 123})
  [:input {:type "text" :name "first-name" :value ""}])
```

The csrf field is automatically appended to the `coast/form` form.

#### `form-for`

```clojure
(coast/form-for :customer/change {:customer/id 123}
  [:input {:type "text" :name "first-name" :value ""}])
```

`form-for` is a convenience function that takes a coast route name and any route parameters followed by the rest of the form.

## View Logic
Coast uses clojure code to insert conditional logic into hiccup vectors

Here's an example of one way to do authentication:

```clojure
(defn index [request]
  (let [session (:session request)]
    [:div

      (if session
        "You are logged in!"
        [:a {:href "/login"} "Click here to log in"])]))
```

If you need to loop through a list of things, here is one way to do it:

```clojure
(ns post
  (:require [coast]))

(defn index [request]
  (let [posts (coast/q '[:select * :from post])]
    [:ul
      (for [post posts]
        [:li (:post/title post)])]))
```

#### `raw`

Newer versions of hiccup (2.0.0 and greater) escape all html by default, if you need to render
a string as html, you'll have to explicitly call raw

```clojure
(coast/raw [:div "<b>is fine now</b>"])
```

## Components

Keeping track of hiccup code can become unwieldy if the functions get long enough, similar to HTML code.

Coast has a way of separating bits of HTML into smaller chunks that can be called on when needed. Components.

Here's a basic example:

```clojure
(defn modal [title & content]
  [:div {:class "modal" :tabindex "-1" :role "dialog"}
   [:div {:class "modal-dialog" :role "document"}
    [:div {:class "modal-content"}
     [:div {:class "modal-header"}
      [:h5 {:class "modal-title"} title]
      [:button {:type "button" :class "close" :data-dismiss "modal" :aria-label "Close"}
       [:span {:aria-hidden "true"}
        "×"]
       ]]
     [:div {:class "modal-body"} content]
     [:div {:class "modal-footer"}
      [:button {:type "button" :class "btn btn-primary"}
       "Save changes"]
      [:button {:type "button" :class "btn btn-secondary" :data-dismiss "modal"}
       "Close"]
      ]]
    ]])
```

Use the `modal` component like this:

```clojure
(defn index [request]
  [:div
    [:a {:href "#" :id "show-modal"}]

    (modal "My Modal"
      [:p "My modal body goes here"]
      [:div "In fact multiple things can go here"])]])
```

This is assuming you have the requisite js somewhere.

## Layout

Layouts in coast are specified alongside the routes which makes supporting multiple layouts a little easier

Here's an example:

```clojure
(defn my-layout-function [request body]
  [:html
    [:head
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     (coast/css "bundle.css")
     (coast/js "bundle.js")]
    [:body
     body]])


(defn my-other-layout-function [request body]
 [:html
   [:head
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    (coast/css "bundle.css")
    (coast/js "bundle.js")]
   [:body
    body]])


(def routes
  (coast/routes
    (coast/site-routes :my-layout-function
      [:get "/" :home/index]
      [:resource :customer]

    (coast/site-routes :my-other-layout-function
      [:get "/other-route" :other/route]))))
```

## Syntax

Hiccup also offers a more terse syntax in case you get tired of writing out html identifiers and class names in maps:

This in html:

```html
<div id="my-id" class="btn btn-solid"></div>
```

...becomes this in normal hiccup

```clojure
[:div {:id "my-id" :class "btn btn-solid"}]
```

...becomes this in terse hiccup

```clojure
[:div#my-id.btn.btn-solid]
```
