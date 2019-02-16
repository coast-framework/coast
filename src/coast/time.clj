(ns coast.time
  (:import (java.time LocalDateTime OffsetDateTime ZoneOffset ZoneId)
           (java.time.format DateTimeFormatter)
           (java.time.temporal ChronoUnit)))

(defn fmt [d pattern]
  (when (or (instance? LocalDateTime d)
            (instance? OffsetDateTime d))
    (let [formatter (DateTimeFormatter/ofPattern pattern)]
      (.format formatter d))))

(defn now
  ([]
   (LocalDateTime/now))
  ([tz]
   (LocalDateTime/now (ZoneId/of tz))))


(defn offset []
  (OffsetDateTime/now))


(defn local
  ([d tz]
   (when (instance? java.util.Date d)
     (LocalDateTime/ofInstant (.toInstant d) tz)))
  ([d]
   (local d ZoneOffset/UTC)))

(defn since
  ([t tz]
   (let [n (now (or tz "UTC"))
         t (if (nil? tz)
             (local t)
             (local t (ZoneId/of tz)))]
     {:hours (.between (ChronoUnit/HOURS) t n)
      :minutes (.between (ChronoUnit/MINUTES) t n)
      :seconds (.between (ChronoUnit/SECONDS) t n)}))
  ([t]
   (since t nil)))

(defn parse [s]
  (OffsetDateTime/parse s))

(defn at [val k]
  (when (and (integer? val)
             (qualified-keyword? k))
    (case k
      :nanos/from-now (-> (now) (.plusNanos val))
      :nano/from-now (-> (now) (.plusNanos val))
      :seconds/from-now (-> (now) (.plusSeconds val))
      :second/from-now (-> (now) (.plusSeconds val))
      :minutes/from-now (-> (now) (.plusMinutes val))
      :minute/from-now (-> (now) (.plusMinutes val))
      :hours/from-now (-> (now) (.plusHours val))
      :hour/from-now (-> (now) (.plusHours val))
      :days/from-now (-> (now) (.plusDays val))
      :day/from-now (-> (now) (.plusDays val))
      :weeks/from-now (-> (now) (.plusWeeks val))
      :week/from-now (-> (now) (.plusWeeks val))
      :months/from-now (-> (now) (.plusMonths val))
      :month/from-now (-> (now) (.plusMonths val))
      :years/from-now (-> (now) (.plusYears val))
      :year/from-now (-> (now) (.plusYears val))
      :nanos/ago (-> (now) (.minusNanos val))
      :nano/ago (-> (now) (.minusNanos val))
      :seconds/ago (-> (now) (.minusSeconds val))
      :second/ago (-> (now) (.minusSeconds val))
      :minutes/ago (-> (now) (.minusMinutes val))
      :minute/ago (-> (now) (.minusMinutes val))
      :hours/ago (-> (now) (.minusHours val))
      :hour/ago (-> (now) (.minusHours val))
      :days/ago (-> (now) (.minusDays val))
      :day/ago (-> (now) (.minusDays val))
      :weeks/ago (-> (now) (.minusWeeks val))
      :week/ago (-> (now) (.minusWeeks val))
      :months/ago (-> (now) (.minusMonths val))
      :month/ago (-> (now) (.minusMonths val))
      :years/ago (-> (now) (.minusYears val))
      :year/ago (-> (now) (.minusYears val))
      :else nil)))
