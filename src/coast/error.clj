(ns coast.error
  "Regular exceptions leave little to be desired. raise and rescue are wrappers around ExceptionInfo")

(defn raise
  "Raise an instance of ExceptionInfo with a map `m` and an optional message `s`."
  ([s m]
   (throw (ex-info s (assoc m ::raise true))))
  ([m]
   (raise "Error has occurred" m)))

(defmacro rescue
  "Evaluate the form `f` and returns a two-tuple vector of `[result error]`.
  Any exception thrown by form `f` will be caught and return in the `error`.
  Otherwise `error` will be `nil`.

  When the exception contains the raise keyword `:coast.error/raise`,
  `rescue` will not catch the error.

  You can customize the raise keyword by using the optional `k` argument."
  ([f k]
   `(try
      [~f nil]
      (catch clojure.lang.ExceptionInfo e#
        (let [ex# (ex-data e#)]
          (if (and (contains? ex# ::raise)
                   (contains? ex# (or ~k ::raise)))
            [nil ex#]
            (throw e#))))))
  ([f]
   `(rescue ~f nil)))
