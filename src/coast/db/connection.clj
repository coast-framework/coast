(ns coast.db.connection
  (:require [coast.env :refer [env]]
            [clojure.string :as string])
  (:import (com.zaxxer.hikari HikariConfig HikariDataSource)))


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


(defn sqlite? [arg]
  (cond
    (string? arg) (> (.indexOf arg "sqlite") -1)
    (map? arg) (sqlite? (.getJdbcUrl (:datasource arg)))
    :else false))

(defn pg? [arg]
  (cond
    (string? arg) (> (.indexOf arg "postgres") -1)
    (map? arg) (pg? (.getJdbcUrl (:datasource arg)))
    :else false))


(defn driver [s]
  "Determines which driver class to pass into the hikari cp config"
  (when (string? s)
    (cond
      (sqlite? s) :sqlite
      (pg? s) :pg
      :else nil)))


(def driver-class-names {:sqlite "org.sqlite.JDBC"
                         :pg "org.postgresql.Driver"})


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
  [s m]
  (let [m (merge opts m)
        driver (driver s)
        connection-init-sql (when (= :sqlite driver)
                              "PRAGMA foreign_keys=ON")
        _ (when (nil? driver)
            (throw (Exception. "Unsupported connection string, only sqlite and postgres are supported currently")))
        c (doto (HikariConfig.)
            (.setDriverClassName     (get driver-class-names driver))
            (.setJdbcUrl             s)
            (.setAutoCommit          (:auto-commit m))
            (.setReadOnly            (:read-only m))
            (.setConnectionTimeout   (:connection-timeout m))
            (.setValidationTimeout   (:validation-timeout m))
            (.setIdleTimeout         (:idle-timeout m))
            (.setMaxLifetime         (:max-lifetime m))
            (.setMinimumIdle         (:minimum-idle m))
            (.setMaximumPoolSize     (:maximum-pool-size m))
            (.setConnectionInitSql   connection-init-sql))]
    {:datasource (HikariDataSource. c)}))

(def pooled-db (delay (pool (db-url) opts)))

(defn connection [] @pooled-db)
