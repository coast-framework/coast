(ns coast
  (:require [coast.components :as c]
            [coast.middleware :as middleware]
            [coast.responder :as responder]
            [coast.server :as server]
            [db.core]
            [error.core :as error]
            [env.core :as env]
            [helper.core :as helper]
            [hiccup2.core]
            [logger.core :as logger]
            [router.core :as router]
            [validator.core :as validator])
  (:refer-clojure :exclude [update]))

; hiccup
(def raw hiccup2.core/raw)

; components
(def doctype c/doctype)
(def css c/css)
(def js c/js)
(def form c/form)

; middleware
(def layout middleware/layout)
(def logger logger/logger)
(def assets middleware/assets)
(def json middleware/json)
(def body-parser middleware/body-parser)
(def sessions middleware/sessions)
(def not-found middleware/not-found)
(def server-error middleware/server-error)
(def content-type? middleware/content-type?)
(def head middleware/head)
(def cookies middleware/cookies)
(def security-headers middleware/security-headers)
(def set-db middleware/set-db)
(def simulated-methods middleware/simulated-methods)

; router
(def middleware router/middleware)
(def routes router/routes)
(def prefix router/prefix)
(def app router/app)
(def apps router/apps)
(def url-for router/url-for)
(def action-for router/action-for)
(def redirect-to router/redirect-to)

; server
(def server server/restart)
(def start-server server/start)
(def stop-server server/stop)
(def restart-server server/restart)

; responder
(defmacro html [& args]
  `(responder/html ~@args))

(def redirect responder/redirect)
(def flash responder/flash)
(def render responder/render)

; db
(def q db.core/q)
(def pull db.core/pull)
(def fetch db.core/fetch)
(def from db.core/from)
(def insert db.core/insert)
(def insert-all db.core/insert-all)
(def update db.core/update)
(def update-all db.core/update-all)
(def upsert db.core/upsert)
(def upsert-all db.core/upsert-all)
(def delete db.core/delete)
(def delete-all db.core/delete-all)

(defmacro defq
  ([n filename]
   `(db.core/defq n filename))
  ([filename]
   `(db.core/defq filename)))

(def query db.core/query)
(def execute db.core/execute)

(defmacro with-transaction [binder context & body]
  `(db.core/with-db-transaction [~binder ~context]
     ~@body))

(def db-context db.core/context)
(def db-connect db.core/connect)
(def db-disconnect db.core/disconnect)

(defn db []
  (-> (env/env :coast-env)
      (keyword)
      (db.core/context)
      (db.core/connect)))

; error
(def raise error/raise)

(defmacro try* [f]
  `(error/try ~f))

(defmacro rescue
  ([f id]
   `(error/rescue ~f ~id))
  ([f]
   `(error/rescue ~f true)))

; validator
(def params validator/params)

; helper
(def uuid helper/uuid)
(def xhr? helper/xhr?)

; env
(def env env/env)
