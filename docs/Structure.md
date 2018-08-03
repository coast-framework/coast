# Structure

## Why not MVC?

The goal of coast on clojure even if it has a similar sort of name, is not to *BE* ruby on rails, it's to *BEAT* ruby on rails at rapid website development. One place where coast shines is that you can dump everything in one file and still have it work a la PHP or you can break your site into multiple files and still have it work. Some people may call this configuration, gasp! If configuration can look like this

```clojure
(ns your-project
  (:require [coast.eta :as coast]))

(def routes [[:get "/hello/:name" `hello]])

(defn hello [req]
  [:div (str "Hello " (-> req :params :name) "!")])

(def app (coast/app routes))

(def main- [& [port]]
  (coast/server app {:port port}))
```

Then that's fine by me.

Of course some people don't like this or claim that "it doesn't scale" whatever that means. So coast can be split across multiple files in a way that makes sense when you don't have a model layer, since clojure as a language *is* the model layer (i.e. data everywhere, no classes/objects).

A post-MVC app for regular html rendered websites without javascript, what does that even look like? Like this:

.
├── Makefile
├── README.md
├── bin
│   └── repl.clj
├── deps.edn
├── resources
│   ├── public
│   │   ├── css
│   │   |   └── app.css
│   │   ├── favicon.ico
│   │   ├── js
│   │   |   └── app.js
│   │   └── robots.txt
├── src
│   ├── error
│   │   ├── internal_server_error.clj
│   │   └── not_found.clj
│   ├── home
│   │   ├── help.clj
│   │   └── index.clj
│   ├── author
│   │   ├── index.clj
│   │   ├── show.clj
│   │   ├── new.clj
│   │   ├── edit.clj
│   │   └── delete.clj
│   ├── routes.clj
│   ├── server.clj
└── test
    └── server_test.clj

So there's still the regular suspects, showing forms with `new` and `edit`, a `routes` file, the main difference is in how the code is laid out, like in `author.new` for example

```clojure
(ns author.new)

(defn view [req])

(defn action [req])
```

That's it, back to the web's roots, `view` functions that show html (`GET` requests) and `action` functions that handle `POST` requests from forms. It's so simple it hurts. I don't know if a lot of programmers know this, but browsers can only "natively" handle those two http verbs, the rest of the RESTful verbs don't exist as far as a browser is concerned. So you've got forms all across the web that have hidden inputs `<input type="hidden" name="__method" value="put">` which is fine but it's more work than you or I need to do.

So what does a typical `view`/`action` pair look like?

```clojure
(ns author.new
  (:require [coast.app :refer [form validate rescue ok redirect]]
            [coast.db :as db]
            [routes :refer [url-for action-for]]))

(defn view [req]
  (form (action-for `action)
    [:label {:for "author/email"} "Email"]
    [:input {:type "email" :name "author/email" :value (-> req :params :author/email)}]

    [:input {:type "submit" :value "New Author"}]))

(defn action [req]
  (let [{:keys [params]} req
        [_ errors] (-> (validate params [[:required [:author/email] "can't be blank"
                                         [:email [:author/email] "needs to be an email"]]])
                       (select-keys [:author/email])
                       (db/transact)
                       (rescue))]
    (if (nil? errors))
      (redirect (url-for :home.index/view))
      (view (merge req errors))))
```

That's it, that's all it takes to get a form validated and into your database. No classes, no objects, no controllers, no separate view files with separate syntax, no ORMs, no special framework-specific params whitelisting, just views and actions.

Look at all of the things I'm not doing.
