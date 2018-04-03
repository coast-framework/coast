(ns coast.errors)

(defmacro catch+ [f & types]
  `(try
     ~f
     (catch Exception e#
       (let [ex# (ex-data e#)
             types# (set '~types)]
         (if (contains? types# (:type ex#))
           (:errors ex#)
           (throw e#))))))
