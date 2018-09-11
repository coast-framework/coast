(ns coast.jobs
  (:require [coast.time :as time]
            [clojure.string :as string]
            [coast.db :refer [defq]])
  (:refer-clojure :exclude [run update]))

(defq insert "sql/jobs.sql")
(defq update "sql/jobs.sql")
(defq queued "sql/jobs.sql")

(defn qualified-name [var]
  (if (qualified-symbol? var)
    (str var)
    (let [{:keys [ns name]} (meta var)]
      (format "%s/%s" (ns-name ns) name))))

(defn queue-job
  ([f args scheduled-at]
   (let [params {:function (qualified-name f)
                 :args (str args)
                 :finished-at nil
                 :scheduled-at scheduled-at}]
     (insert params)))
  ([f args]
   (queue-job f args nil)))

(defmacro queue [body]
  `(let [f# (symbol (str *ns* "/" (-> '~body first symbol)))
         args# (-> '~body rest)]
     (queue-job f# args#)))

(defn resolve* [s]
  (let [[q-ns q-name] (->> (string/split s #"/")
                           (map symbol))]
    (load (str (string/replace q-ns #"\." "/")))
    (ns-resolve q-ns q-name)))

(defn run [job]
  (let [{:keys [function args]} job
        f (resolve* function)
        parsed-args (clojure.edn/read-string args)
        parsed-args (if (sequential? parsed-args)
                     parsed-args
                     [parsed-args])]
    (when (not (nil? f))
      (if (empty? parsed-args)
          (f)
          (apply f parsed-args))
      (update (assoc job :finished-at (time/now))))))
