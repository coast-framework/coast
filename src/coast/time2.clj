(ns coast.time2
  (:import (java.time ZoneOffset ZonedDateTime ZoneId Instant)
           (java.time.format DateTimeFormatter)))


(defn now
  "Return the current time (in epoch second)."
  []
  (.getEpochSecond (Instant/now)))


(defn instant
  "Convert epoch second `second` to `java.time.Instant`."
  [seconds]
  (Instant/ofEpochSecond seconds))


(defn datetime
  "Convert epoch second `second` to `java.time.ZonedDateTime`."
  ([seconds zone]
   (let [zoneId (if (string? zone)
                  (ZoneId/of zone)
                  ZoneOffset/UTC)]
     (ZonedDateTime/ofInstant
       (instant seconds)
       zoneId)))
  ([seconds]
   (datetime seconds nil)))


(defn strftime
  "Convert datetime `d` to str with pattern `pattern`."
  [d pattern]
  (let [formatter (DateTimeFormatter/ofPattern pattern)]
    (.format formatter d)))
