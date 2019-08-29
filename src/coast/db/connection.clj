(ns coast.db.connection
  (:require [coast.env :refer [env]]
            [clojure.edn :as edn]
            [clojure.java.io :as io])
  (:import (com.zaxxer.hikari HikariConfig HikariDataSource)
           (java.util Properties)))


(defn spec
  ([]
   (let [m (->> (slurp (or (io/resource "db.edn") "db.edn"))
                (edn/read-string {:readers {'env env}})
                (mapv (fn [[k v]] [k v]))
                (into {}))]
     (get m (keyword (env :coast-env)))))
  ([k]
   (get (spec) k)))


(def datasource-class-names {"sqlite"   "org.sqlite.SQLiteDataSource"
                             "postgres" "org.postgresql.ds.PGSimpleDataSource"})


(def opts {:auto-commit        true
           :read-only          false
           :connection-timeout 30000
           :validation-timeout 5000
           :idle-timeout       600000
           :max-lifetime       1800000
           :minimum-idle       10
           :maximum-pool-size  10
           :register-mbeans    false})


(defn pool
  "Shamelessly stolen from hikari-cp and makes a new hikaricp data source"
  [m]
  (let [m (merge opts m)
        connection-init-sql (when (= "sqlite" (:adapter m))
                              "PRAGMA foreign_keys=ON")
        datasource-class-name (get datasource-class-names (:adapter m))
        _ (when (nil? datasource-class-name)
            (throw (Exception. "Unsupported connection string, only sqlite and postgres are supported currently")))
        c (doto (HikariConfig.)
            (.setDataSourceClassName datasource-class-name)
            (.addDataSourceProperty  "databaseName" (:database m))
            (.setUsername            (:username m))
            (.setPassword            (:password m))
            (.setAutoCommit          (:auto-commit m))
            (.setReadOnly            (:read-only m))
            (.setConnectionTimeout   (:connection-timeout m))
            (.setValidationTimeout   (:validation-timeout m))
            (.setIdleTimeout         (:idle-timeout m))
            (.setMaxLifetime         (:max-lifetime m))
            (.setMinimumIdle         (:minimum-idle m))
            (.setMaximumPoolSize     (:maximum-pool-size m))
            (.setConnectionInitSql   connection-init-sql))]
       _ (when (= "sqlite" (:adapter m))
           (.addDataSourceProperty c "url" (str "jdbc:sqlite:" (:database m))))
       _ (when (some? (:port m))
           (.addDataSourceProperty c "portNumber" (:port m)))
       _ (when (some? (:host m))
           (.addDataSourceProperty c "serverName" (:host m)))
    {:datasource (HikariDataSource. c)}))

(def pooled-db (atom (delay (pool (spec)))))

(defn connection
  "Return a database connection."
  []
  @(deref pooled-db))

(defn reconnect! []
  (reset! pooled-db (delay (pool (spec)))))
