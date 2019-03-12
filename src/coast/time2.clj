(ns coast.time2
  (:import (java.time ZoneOffset ZonedDateTime ZoneId Instant)
           (java.time.format DateTimeFormatter)))


(defn now []
  (.getEpochSecond (Instant/now)))


(defn instant [seconds]
  (Instant/ofEpochSecond seconds))


(defn datetime
  ([seconds zone]
   (let [zoneId (if (string? zone)
                  (ZoneId/of zone)
                  ZoneOffset/UTC)]
     (ZonedDateTime/ofInstant
       (instant seconds)
       zoneId)))
  ([seconds]
   (datetime seconds nil)))


(defn strftime [d pattern]
  (let [formatter (DateTimeFormatter/ofPattern pattern)]
    (.format formatter d)))
