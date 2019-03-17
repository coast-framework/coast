# Handlers

* [Creating Handlers](#user-content-creating-handlers)
* [Using Handlers](#user-content-using-handlers)

Handlers are represented by routes, grouping related request handling logic into single files, and are the common point of interaction between your database, html and any other services you may need.

NOTE: A handler's only job is to respond to a HTTP request.

## Creating Handlers

To create a new handler function files, use the `coast gen code` command:

```bash
coast gen code author
```

This command creates a boilerplate file in the `src` folder:

```clojure
; src/author.clj
(ns author
  (:require [coast]))


(defn index [request])
(defn view [request])
(defn build [request])
(defn create [request])
(defn edit [request])
(defn change [request])
(defn delete [request])
```

## Using Handlers

A handler can be accessed from a route.

This is done by referencing the handler as a **keyword** in your route definition:

```clojure
; routes.clj
[:get "/authors" :author/index]
```

The part before the `/` is a reference to the handler file (e.g. `author.clj`).

The part after the `/` is the name of the function you want to call (e.g. `index`).

For example:

```clojure
; routes.clj

; src/author.clj -> (defn index [request])
[:get "/authors" :author/index]

; src/admin/dashboard.clj -> (defn index [request])
[:get "/authors" :admin.dashboard/index]

; src/a/deep/path/file.clj -> (defn create [request])
[:get "/a-deep-path" :a.deep.path.file/create]
```

As your defined handler functions are route handlers, they will receive the [request map](/docs/request-lifecycle.md) as an argument.

```clojure
; src/author.clj

(ns author
  (:require [coast]))

(defn index [request]
  (let [params (:params request)
        session (:session request)
        errors (:errors request)]
    ; code generating a response goes here
  ))
```
