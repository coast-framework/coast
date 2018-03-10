(ns controllers.__table
  (:require [coast.alpha :as coast]
            [models.__table :as __table]
            [views.__table :as views.__table])
  (:refer-clojure :exclude [update]))

(defn index [request]
  (-> request
      __table/list
      views.__table/index))

(defn show [request]
  (-> request
      __table/find
      views.__table/show))

(defn fresh [request]
  (views.__table/fresh request))

(defn create [request]
  (let [[_ errors] (-> request
                       __table/create
                       coast/try+)]
    (if (empty? errors)
      (-> (coast/redirect "/__table")
          (coast/flash "__singular created successfully"))
      (fresh (assoc request :errors errors)))))

(defn edit [request]
  (-> request
      __table/find
      views.__table/edit))

(defn update [request]
  (let [[_ errors] (-> request
                       __table/find
                       __table/update
                       coast/try+)]
    (if (empty? errors)
      (-> (coast/redirect "/__table")
          (coast/flash "__singular updated successfully"))
      (edit (assoc request :errors errors)))))

(defn delete [request]
  (let [[_ errors] (-> request
                       __table/delete
                       coast/try+)]
    (if (empty? errors)
      (-> (coast/redirect "/__table")
          (coast/flash "__singular deleted successfully"))
      (-> (coast/redirect "/__table")
          (coast/flash (format "__singular could not be deleted: %s" (:error errors)))))))
