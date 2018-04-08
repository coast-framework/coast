(ns coast.errors)

(defn not-found? [m]
  (= :404 (:type m)))

(defn invalid? [m]
  (= :invalid (:type m)))

(defn error? [m]
  (contains? m :type))

(defmacro catch+ [f & types]
  `(try
     ~f
     (catch Exception e#
       (let [ex# (ex-data e#)
             types# (set '~types)]
         (if (and (contains? types# (:type ex#))
                  (not (empty? ex#)))
           ex#
           (throw e#))))))

(defmacro catch! [f]
  `(try
     ~f
     (catch Exception e#
       (let [ex# (ex-data e#)]
         (if (error? ex#)
           ex#
           (throw e#))))))
