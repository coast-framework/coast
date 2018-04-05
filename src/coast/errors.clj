(ns coast.errors)

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
