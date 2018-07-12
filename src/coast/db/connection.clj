(ns coast.db.connection
  (:require [coast.env :refer [env]]
            [clojure.string :as string])
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource)))

; turn off logging in c3p0
(System/setProperties
  (doto (java.util.Properties. (System/getProperties))
    (.put "com.mchange.v2.log.MLog" "com.mchange.v2.log.FallbackMLog")
    (.put "com.mchange.v2.log.FallbackMLog.DEFAULT_CUTOFF_LEVEL" "OFF")))

(defn db-url []
  (let [url (or (env :database-url)
                (env :db-spec-or-url))]
    (if (string/blank? url)
      (throw (Exception. "Your database connection string is blank. Set the DATABASE_URL or DB_SPEC_OR_URL environment variable"))
      url)))

(defn admin-db-url []
  (let [url (or (env :admin-db-spec-or-url)
                (env :admin-database-url)
                "postgres://localhost:5432/postgres")]
    (if (string/blank? url)
      (throw (Exception. "Your admin database connection string is blank. Set the ADMIN_DB_SPEC_OR_URL environment variable"))
      url)))

(defn user-info [db-uri]
  (when (string? (.getUserInfo db-uri))
    (let [[user password] (clojure.string/split (.getUserInfo db-uri) #":")]
      {:user user :password password})))

(defn db-spec []
  (let [uri (java.net.URI. (db-url))
        user (user-info uri)]
    {:classname   "org.postgresql.Driver"
     :subprotocol "postgresql"
     :user        (:user user)
     :password    (:password user)
     :subname (if (= -1 (.getPort uri))
                (format "//%s%s" (.getHost uri) (.getPath uri))
                (format "//%s:%s%s" (.getHost uri) (.getPort uri) (.getPath uri)))}))

(defn pool [db-spec]
  (let [cpds (doto (ComboPooledDataSource.)
               (.setDriverClass (:classname db-spec))
               (.setJdbcUrl (str "jdbc:" (:subprotocol db-spec) ":" (:subname db-spec)))
               (.setUser (:user db-spec))
               (.setPassword (:password db-spec))
               ;; expire excess connections after 30 minutes of inactivity:
               (.setMaxIdleTimeExcessConnections (* 30 60))
               ;; expire connections after 3 hours of inactivity:
               (.setMaxIdleTime (* 3 60 60)))]
    {:datasource cpds}))

(def pooled-db (delay (pool (db-spec))))

(defn connection [] @pooled-db)
