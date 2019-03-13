# Authentication

* [Libraries](#user-content-libraries)
* [Setup](#user-content-setup)
* [Password Hashing](#user-content-password-hashing)
* [Complete Example](#user-content-complete-example)

Coast does not have authentication built in. It's up to you to determine how you want to authenticate people.

Typically though for clojure database-backed web applications, there are a few options:

- Sessions
- JWT Tokens (this guide does not cover this)
- Basic HTTP Auth (also not covered by this guide)

## Libraries

Let's get into it. In the clojure world, there are two popular web application authentication libraries

- [Friend](https://github.com/cemerick/friend)
- [Buddy](https://github.com/funcool/buddy)

Coast lends itself more to buddy, so that's what this guide covers.

Buddy is composed of several different libraries:

- `buddy-core`
- `buddy-hashers`
- `buddy-sign`
- `buddy-auth`

Setting up authentication middleware with Coast only really needs `buddy-hashers`, so that's the library we'll choose

## Setup

Here's how to set up buddy for use with a Coast application

Install the `buddy-hashers` dependency in your `deps.edn` file

```clojure
; deps.edn

{; other keys not shown
 :deps
 {org.clojure/clojure {:mvn/version "1.9.0"}
  coast-framework/coast.theta {:mvn/version "1.0.0"}
  org.xerial/sqlite-jdbc {:mvn/version "3.25.2"}
  buddy/buddy-hashers {:mvn/version "1.3.0"}}}
```

## Password Hashing

You can see the [full documentation of buddy-hashers here](https://funcool.github.io/buddy-hashers/latest/), this guide summarizes basic usage:

```clojure
(ns some-ns
  (:require [buddy.hashers :as hashers]))

(hashers/derive "secretpassword")
;; => "bcrypt+sha512$4i9sd34m..."

(hashers/check "secretpassword" "bcrypt+sha512$4i9sd34m...")
;; => true
```

Buddy uses the bcrypt + sha512 algorithm by default.

## Complete Example

The first step into integrating the `buddy-hashers` library into a Coast application is to create six handlers:

- 2 handlers for signing up
- 2 handlers for signing in
- 1 handler for signing out
- 1 handler that only signed in people can see

Let's start with the auth middleware, that checks that a session exists before continuing:

```clojure
(ns middleware
  (:require [coast]))

(defn auth [handler]
  (fn [request]
    (if (some? (:session request))
      (handler request)
      (coast/unauthorized "HAL9000 says: I'm sorry Dave, I can't let you do that"))))
```

...and the routes:

```clojure
; src/routes.clj

(ns routes
  (:require [coast]))

(def routes
  (coast/routes
    (coast/site-soutes
     [:get "/sign-up" :person/build]
     [:post "/persons" :person/create]
     [:get "/sign-in" :session/build]
     [:post "/sessions" :session/create]

     (coast/wrap-routes auth
      [:get "/dashboard" :person/dashboard]
      [:delete "/sessions" :sessions/delete]))))
```

Now create three new handler function definitions `build` and `create` in the `src/person.clj` file:

```clojure
; src/person.clj

(ns person
  (:require [coast]
            [buddy.hashers :as hashers]))

(defn build [request])

(defn create [request])

(defn dashboard [request])
```

Create a simple, unstyled form in the `build` function so people can enter an email and a password:

```clojure
; src/person.clj

(defn build [request]
  (coast/form-for ::build
    [:input {:type "text" :name "person/email"}]
    [:input {:type "password" :name "person/password"}]
    [:input {:type "submit" :value "Submit"}]))
```

And fill in the `create` function to handle the submission of that form:

```clojure
; src/person.clj

(defn create [request]
  (let [[_ errors] (-> (:params request)
                       (select-keys [:person/email :person/password])
                       (coast/validate [[:email [:person/email]
                                        [:required [:person/password]]]])
                       (update :person/password hashers/derive)
                       (coast/insert) ; you'll need a database for this to work
                       (coast/rescue))]
    (if (some? errors)
      (build (merge errors request))
      (-> (coast/redirect ::dashboard)
          (assoc :session (select-keys (:params request) [:person/email]))))))
```

Two colons `::` in front of a keyword means use the namespace as the current namespace of the file, in this case `person`.

`:person/email` and `::email` in the person namespace are equivalent.

Now fill in the `dashboard` function with a simple message and a sign out link (which is an actual form):

```clojure
(defn dashboard [request]
  [:div
    [:h1 "You're signed in! Welcome!"]
    (coast/form-for :session/delete
     [:input {:type "submit" :value "Sign out"}])
```

At this point we've handled a very simple whole sign up flow, minus the database migrations.

Next let's get sign in and sign out working:

Create a new file in `src` named `session.clj`

```clojure
; src/session.clj

(ns session
  (:require [coast]))
            [buddy.hashers :as hashers]

(defn build [request])

(defn create [request])

(defn delete [request])
```

Now let's fill in the handler to show the sign in form:

```clojure
(defn build [request]
  [:div
    (when (some? (:error/message request))
      [:div (:error/message request)])
    (coast/form-for ::build
      [:input {:type "text" :name "person/email"}]
      [:input {:type "password" :name "person/password"}]
      [:input {:type "submit" :value "Submit"}])])
```

...and the form submission

```clojure
(defn create [request]
  (let [email (get-in request [:params :person/email])
        person (first (coast/q '[:select *
                                 :from person
                                 :where [email ?email]]
                               {:email email}))
        [valid? errors] (-> (:params request)
                            (select-keys [:person/email :person/password])
                            (coast/validate [[:email [:person/email]
                                             [:required [:person/password]]]]) ; these three lines could be middleware
                            (get :person/password) ; this returns the plaintext password from the params map
                            (hashers/check (:person/password person)) ; hashers/check is here
                            (coast/rescue))]
    (if (or (some? errors)
            (false? valid?))
      (build (merge errors request {:error/message "Invalid email or password"}))
      (-> (coast/redirect :person/dashboard)
          (assoc :session (select-keys (:params request) [:person/email]))))))
```

Notice the use of `hashers/check` to check the plaintext password from the form against the
password from the existing hashed password in the database.

...and finally the sign out handler

```clojure
(defn delete [request]
  (-> (coast/redirect ::build)
      (assoc :session nil)))
```

This is not the only way to implement authentication in your Coast app, but it is a complete example of one way of doing authentication.
