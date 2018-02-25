(ns coast.time
  (:import (java.time LocalDateTime)
           (java.time.format DateTimeFormatter)))

(defn fmt [d pattern]
  (when (instance? LocalDateTime d)
    (let [formatter (DateTimeFormatter/ofPattern pattern)]
      (.format formatter d))))

(defn now []
  (LocalDateTime/now))
